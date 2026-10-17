import hashlib
from pathlib import Path

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


def _televerser(c, h, corps: dict, donnees: bytes) -> str:
    tid = c.post("/televersements", json=corps | {"taille": len(donnees), "sha256": hashlib.sha256(donnees).hexdigest()}, headers=h).json()["id"]
    c.put(f"/televersements/{tid}?offset=0", content=donnees, headers=h)
    return c.post(f"/televersements/{tid}/terminer", headers=h).json()["tache_id"]


def test_meme_placette_dans_deux_projets(cfg, tmp_path):
    """Two sampling plans both have a P001: tasks, listing and results stay apart."""
    h = {"Authorization": "Bearer secret"}
    c = TestClient(creer_app(cfg))
    ta = _televerser(c, h, {"projet_id": "foret_a", "plot_id": "P001"}, b"paquet A")
    tb = _televerser(c, h, {"projet_id": "foret_b", "plot_id": "P001"}, b"paquet B")
    t0 = _televerser(c, h, {"plot_id": "P001"}, b"ancien telephone")  # no projet_id: default project
    file = FileAttente(cfg.base)
    assert [Path(file.obtenir(t)["paquet"]).parent.name for t in (ta, tb, t0)] == ["foret_a", "foret_b", "_"]

    tout = c.get("/placettes", headers=h).json()
    assert sorted((p["projet_id"], p["plot_id"]) for p in tout) == [("", "P001"), ("foret_a", "P001"), ("foret_b", "P001")]
    assert [p["id"] for p in c.get("/placettes?projet_id=foret_b", headers=h).json()] == [tb]

    # Results: only foret_b's P001 is done.
    rapport = tmp_path / "rapport_b.json"
    rapport.write_text('{"plot_id": "P001", "projet": "b"}')
    file.terminer(tb, str(rapport), str(rapport), str(rapport), "conforme")
    assert c.get("/projets/foret_b/placettes/P001/rapport.json", headers=h).json()["projet"] == "b"
    assert c.get("/projets/foret_a/placettes/P001/rapport.json", headers=h).status_code == 404
    assert c.get("/placettes/P001/rapport.json", headers=h).status_code == 404  # default project
    assert c.get("/projets/foret_b/placettes/P001/inconnu.txt", headers=h).status_code == 404
    assert c.post("/televersements", json={"projet_id": "../x", "plot_id": "P1", "taille": 1, "sha256": "0" * 64}, headers=h).status_code == 422


def test_file_creee_avant_projet_id(tmp_path):
    """A queue base from before projet_id is migrated: old tasks go to the default project."""
    import sqlite3

    base = tmp_path / "t.sqlite"
    with sqlite3.connect(base) as cx:
        cx.executescript(
            "CREATE TABLE taches (id TEXT PRIMARY KEY, plot_id TEXT NOT NULL, paquet TEXT NOT NULL,"
            " etat TEXT NOT NULL DEFAULT 'en_attente', tentatives INTEGER NOT NULL DEFAULT 0, cree TEXT NOT NULL,"
            " maj TEXT NOT NULL, erreur TEXT, gpkg TEXT, rapport_json TEXT, rapport_html TEXT, verdict TEXT);"
            "CREATE INDEX taches_plot ON taches(plot_id, cree);"
            "INSERT INTO taches (id, plot_id, paquet, cree, maj) VALUES ('t1', 'P001', 'x.zip', '2026-10-01', '2026-10-01');"
        )
    f = FileAttente(base)
    assert f.derniere("P001")["id"] == "t1" and f.derniere("P001")["projet_id"] == ""
    f.ajouter("P001", "y.zip", "foret_a")
    assert f.derniere("P001")["id"] == "t1"
    assert len(f.lister()) == 2 and len(f.lister("foret_a")) == 1


def test_migration_simultanee(tmp_path):
    """API and worker started together on an old base: only one adds projet_id, neither fails."""
    import sqlite3
    import threading
    import time

    base = tmp_path / "t.sqlite"
    with sqlite3.connect(base) as cx:
        cx.execute("PRAGMA journal_mode=WAL")  # as left by the server that created it
        cx.executescript(
            "CREATE TABLE taches (id TEXT PRIMARY KEY, plot_id TEXT NOT NULL, paquet TEXT NOT NULL,"
            " etat TEXT NOT NULL DEFAULT 'en_attente', tentatives INTEGER NOT NULL DEFAULT 0, cree TEXT NOT NULL,"
            " maj TEXT NOT NULL, erreur TEXT, gpkg TEXT, rapport_json TEXT, rapport_html TEXT, verdict TEXT);"
            "CREATE INDEX taches_etat ON taches(etat, cree);"
        )
    # The other process holds the write lock and migrates while this one starts.
    autre = sqlite3.connect(base, isolation_level=None)
    autre.execute("BEGIN IMMEDIATE")
    erreurs = []

    def demarrer():
        try:
            FileAttente(base)
        except Exception as e:  # noqa: BLE001 - the failure is what the test looks for
            erreurs.append(e)

    t = threading.Thread(target=demarrer)
    t.start()
    time.sleep(0.5)
    autre.execute("ALTER TABLE taches ADD COLUMN projet_id TEXT NOT NULL DEFAULT ''")
    autre.execute("COMMIT")
    autre.close()
    t.join(10)
    assert not erreurs
    FileAttente(base).ajouter("P001", "x.zip", "foret_a")
