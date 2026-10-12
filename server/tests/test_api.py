import hashlib

import pytest
from fastapi.testclient import TestClient

from groma_server.api import creer_app
from groma_server.config import Config
from groma_server.file_attente import FileAttente
from groma_server.worker import traiter_une


@pytest.fixture
def cfg(tmp_path):
    return Config(dossier_donnees=tmp_path, jeton="secret", taille_morceau=256 * 1024, reconstructeurs=("profondeur",))


def test_jeton_obligatoire(cfg):
    c = TestClient(creer_app(cfg))
    assert c.get("/placettes").status_code == 401
    assert c.get("/sante").status_code == 200


def test_televersement_reprenable_et_resultats(cfg, paquet_synthetique):
    chemin, _ = paquet_synthetique
    donnees = chemin.read_bytes()
    h = {"Authorization": "Bearer secret"}
    c = TestClient(creer_app(cfg))
    r = c.post("/televersements", json={"plot_id": "P001", "taille": len(donnees), "sha256": hashlib.sha256(donnees).hexdigest()}, headers=h)
    assert r.status_code == 200
    tid, morceau = r.json()["id"], r.json()["taille_morceau"]

    # First chunk, then a "network cut": the phone asks where to resume.
    c.put(f"/televersements/{tid}?offset=0", content=donnees[:morceau], headers=h)
    r = c.put(f"/televersements/{tid}?offset=0", content=donnees[:morceau], headers=h)
    assert r.status_code == 409 and r.json()["recu"] == morceau
    recu = c.get(f"/televersements/{tid}", headers=h).json()["recu"]
    while recu < len(donnees):
        recu = c.put(f"/televersements/{tid}?offset={recu}", content=donnees[recu : recu + morceau], headers=h).json()["recu"]
    tache = c.post(f"/televersements/{tid}/terminer", headers=h).json()["tache_id"]
    assert c.get(f"/taches/{tache}", headers=h).json()["etat"] == "en_attente"
    assert c.get("/placettes/P001/resultat.gpkg", headers=h).status_code == 404

    assert traiter_une(FileAttente(cfg.base), cfg)
    t = c.get(f"/taches/{tache}", headers=h).json()
    assert t["etat"] == "terminee", t["erreur"]
    assert c.get("/placettes", headers=h).json()[0]["plot_id"] == "P001"
    g = c.get("/placettes/P001/resultat.gpkg", headers=h)
    assert g.status_code == 200 and g.content[:16] == b"SQLite format 3\x00"
    assert c.get("/placettes/P001/rapport.json", headers=h).json()["plot_id"] == "P001"
    assert "html" in c.get("/placettes/P001/rapport.html", headers=h).headers["content-type"]


def test_somme_de_controle_refusee(cfg):
    h = {"Authorization": "Bearer secret"}
    c = TestClient(creer_app(cfg))
    tid = c.post("/televersements", json={"plot_id": "P9", "taille": 3, "sha256": "0" * 64}, headers=h).json()["id"]
    c.put(f"/televersements/{tid}?offset=0", content=b"abc", headers=h)
    assert c.post(f"/televersements/{tid}/terminer", headers=h).status_code == 422


def test_file_reprise_apres_echec(tmp_path):
    f = FileAttente(tmp_path / "t.sqlite")
    tid = f.ajouter("P1", "/nulle/part.zip")
    assert f.prendre()["id"] == tid
    assert f.prendre() is None
    f.echouer(tid, "panne", tentatives_max=2)
    assert f.obtenir(tid)["etat"] == "en_attente"
    f.prendre()
    f.echouer(tid, "panne", tentatives_max=2)
    assert f.obtenir(tid)["etat"] == "echec"
