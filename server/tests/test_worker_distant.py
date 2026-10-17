import shutil
import time

import pytest
from fastapi.testclient import TestClient

from groma_server.api import creer_app
from groma_server.config import Config
from groma_server.file_attente import FileAttente
from groma_server.worker import boucler
from groma_server.worker_distant import ClientApi, Reponse, TacheReprise, traiter_une_distante

TEL = {"Authorization": "Bearer secret"}


def config(tmp_path, **kw):
    return Config(dossier_donnees=tmp_path / "api", jeton="secret", jeton_worker="wsecret",
                  reconstructeurs=("profondeur",), **kw)


def client_worker(c: TestClient, jeton: str = "wsecret") -> ClientApi:
    def envoyer(methode, chemin, entetes, corps):
        r = c.request(methode, chemin, headers=entetes, content=corps)
        return Reponse(r.status_code, r.content)

    return ClientApi("http://test", jeton, transport=envoyer)


def mettre_en_file(cfg: Config, paquet, plot_id="P001", projet_id="foret_a") -> str:
    d = cfg.dossier_donnees / "paquets" / projet_id
    d.mkdir(parents=True, exist_ok=True)
    cible = d / f"{plot_id}.groma.zip"
    shutil.copyfile(paquet, cible)
    return FileAttente(cfg.base).ajouter(plot_id, str(cible), projet_id)


def test_jetons_separes(tmp_path):
    c = TestClient(creer_app(config(tmp_path)))
    assert c.post("/worker/prendre", headers=TEL).status_code == 401
    assert c.get("/placettes", headers={"Authorization": "Bearer wsecret"}).status_code == 401
    assert c.post("/worker/prendre", headers={"Authorization": "Bearer wsecret"}).status_code == 204


def test_routes_worker_coupees_sans_jeton_worker(tmp_path):
    cfg = Config(dossier_donnees=tmp_path, jeton="secret")
    c = TestClient(creer_app(cfg))
    assert c.post("/worker/prendre", headers={"Authorization": "Bearer "}).status_code == 404


def test_cycle_distant(tmp_path, paquet_synthetique):
    cfg = config(tmp_path)
    c = TestClient(creer_app(cfg))
    tid = mettre_en_file(cfg, paquet_synthetique[0])
    travail = tmp_path / "gpu"

    assert traiter_une_distante(client_worker(c), cfg, travail)
    t = c.get(f"/taches/{tid}", headers=TEL).json()
    assert t["etat"] == "terminee", t["erreur"]
    g = c.get("/projets/foret_a/placettes/P001/resultat.gpkg", headers=TEL)
    assert g.status_code == 200 and g.content[:16] == b"SQLite format 3\x00"
    assert c.get("/projets/foret_a/placettes/P001/rapport.json", headers=TEL).json()["plot_id"] == "P001"
    # Results stored where the local worker puts them.
    assert (cfg.dossier_donnees / "resultats" / "foret_a" / "P001" / tid / "P001_definitif.gpkg").is_file()
    assert not (travail / tid).exists()
    assert not traiter_une_distante(client_worker(c), cfg, travail)


def test_prise_atomique(tmp_path, paquet_synthetique):
    cfg = config(tmp_path)
    c = TestClient(creer_app(cfg))
    mettre_en_file(cfg, paquet_synthetique[0])
    a, b = client_worker(c), client_worker(c)
    assert a.prendre()["tentative"] == 1
    assert b.prendre() is None


def test_bail_expire_tache_reprise(tmp_path, paquet_synthetique):
    cfg = config(tmp_path, bail_s=0)
    c = TestClient(creer_app(cfg))
    mettre_en_file(cfg, paquet_synthetique[0])
    a, b = client_worker(c), client_worker(c)
    ta = a.prendre()
    time.sleep(1.1)  # the lease (0 s) has expired: the task is offered again
    tb = b.prendre()
    assert tb["id"] == ta["id"] and tb["tentative"] == 2

    with pytest.raises(TacheReprise):
        a.prolonger(ta)
    with pytest.raises(TacheReprise):
        a.terminer(ta, "suffisante")
    for nom in ("definitif.gpkg", "rapport.json", "rapport.html"):
        f = tmp_path / nom
        f.write_bytes(b"x")
        b.deposer(tb, nom, f)
    assert b.terminer(tb, "suffisante")["etat"] == "terminee"


def test_resultats_manquants(tmp_path, paquet_synthetique):
    cfg = config(tmp_path)
    c = TestClient(creer_app(cfg))
    mettre_en_file(cfg, paquet_synthetique[0])
    w = client_worker(c)
    t = w.prendre()
    with pytest.raises(TacheReprise, match="manquants"):
        w.terminer(t, "suffisante")


def test_telechargement_repris(tmp_path, paquet_synthetique):
    cfg = config(tmp_path)
    c = TestClient(creer_app(cfg))
    mettre_en_file(cfg, paquet_synthetique[0])
    w = client_worker(c)
    t = w.prendre()
    original = paquet_synthetique[0].read_bytes()
    cible = tmp_path / "partiel.zip"
    cible.write_bytes(original[:1000])  # earlier download cut after 1000 bytes
    w.telecharger(t, cible, morceau=50_000)
    assert cible.read_bytes() == original


def test_paquet_illisible_mis_en_echec(tmp_path):
    cfg = config(tmp_path)
    c = TestClient(creer_app(cfg))
    faux = tmp_path / "faux.zip"
    faux.write_bytes(b"pas un zip")
    tid = mettre_en_file(cfg, faux)
    assert traiter_une_distante(client_worker(c), cfg, tmp_path / "gpu")
    t = c.get(f"/taches/{tid}", headers=TEL).json()
    assert t["etat"] == "en_attente" and t["erreur"]  # second attempt allowed (tentatives_max = 2)


def test_arret_apres_inactivite():
    debut = time.monotonic()
    boucler(lambda: False, pause=0.01, une_fois=False, arret_inactif=0.05)
    assert time.monotonic() - debut < 2
