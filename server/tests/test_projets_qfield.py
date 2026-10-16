import hashlib
import sqlite3
import zipfile

import pytest
from fastapi.testclient import TestClient

from groma_server.api import creer_app
from groma_server.config import Config

H = {"Authorization": "Bearer secret"}


@pytest.fixture
def client(tmp_path):
    cfg = Config(dossier_donnees=tmp_path / "donnees", jeton="secret", taille_max_projet=5 * 1024**2,
                 versions_projet=3, reconstructeurs=("profondeur",))
    return TestClient(creer_app(cfg))


def qgz(tmp_path, projet_id="20261015_194200_yzoh", nom="inventaire_video", raster=b"\x00" * 50_000) -> bytes:
    """Archive shaped like creer_projet_inventaire_video()'s output."""
    gpkg = tmp_path / f"{projet_id}.gpkg"
    gpkg.unlink(missing_ok=True)
    with sqlite3.connect(gpkg) as db:
        db.execute("CREATE TABLE projet (fid INTEGER PRIMARY KEY, projet_id TEXT, projet_nom TEXT)")
        db.execute("INSERT INTO projet (projet_id, projet_nom) VALUES (?, 'Forêt test')", (projet_id,))
    out = tmp_path / f"{nom}.qgz"
    with zipfile.ZipFile(out, "w") as z:
        z.writestr(f"{nom}.qgs", "<qgis/>")
        z.write(gpkg, "inventaire_video.gpkg")
        z.writestr("rasters/P001_mnt.tif", raster)
    return out.read_bytes()


def fichier(nom, donnees):
    return {"nom": nom, "sha256": hashlib.sha256(donnees).hexdigest(), "taille": len(donnees)}


def publier(c, projet_id, archive, plugin=b"// plugin", projet_nom="Forêt test"):
    assert c.put(f"/projets/{projet_id}/qfield/fichiers/inventaire_video.qgz", content=archive, headers=H).status_code == 200
    fichiers = [fichier("inventaire_video.qgz", archive)]
    if plugin is not None:
        assert c.put(f"/projets/{projet_id}/qfield/fichiers/inventaire_video.qml", content=plugin, headers=H).status_code == 200
        fichiers.append(fichier("inventaire_video.qml", plugin))
    return c.post(f"/projets/{projet_id}/qfield/publier", json={"projet_nom": projet_nom, "fichiers": fichiers,
                                                                 "nemeton_version": "2026.10.9"}, headers=H)


def test_depot_publication_liste_et_telechargement(client, tmp_path):
    pid = "20261015_194200_yzoh"
    archive = qgz(tmp_path, pid)
    r = client.put(f"/projets/{pid}/qfield/fichiers/inventaire_video.qgz", content=archive, headers=H)
    assert r.json() == fichier("inventaire_video.qgz", archive)
    r = publier(client, pid, archive)
    assert r.status_code == 200, r.text
    version = r.json()["version"]

    projets = client.get("/projets", headers=H).json()
    assert [p["projet_id"] for p in projets] == [pid]
    assert projets[0]["version"] == version and projets[0]["projet_nom"] == "Forêt test"
    assert {f["nom"] for f in projets[0]["fichiers"]} == {"inventaire_video.qgz", "inventaire_video.qml"}
    assert client.get(f"/projets/{pid}/qfield", headers=H).json()["version"] == version

    g = client.get(f"/projets/{pid}/qfield/{version}/inventaire_video.qgz", headers=H)
    assert g.status_code == 200 and g.content == archive
    assert client.get(f"/projets/{pid}/qfield/{version}/inventaire_video.qml", headers=H).content == b"// plugin"


def test_telechargement_reprenable_par_range(client, tmp_path):
    pid = "P_range"
    archive = qgz(tmp_path, pid)
    version = publier(client, pid, archive).json()["version"]
    url = f"/projets/{pid}/qfield/{version}/inventaire_video.qgz"
    debut = client.get(url, headers={**H, "Range": "bytes=0-9999"})
    assert debut.status_code == 206 and len(debut.content) == 10_000
    suite = client.get(url, headers={**H, "Range": "bytes=10000-"})
    assert suite.status_code == 206
    assert debut.content + suite.content == archive


def test_somme_fausse_rien_publie(client, tmp_path):
    pid = "P_somme"
    archive = qgz(tmp_path, pid)
    client.put(f"/projets/{pid}/qfield/fichiers/inventaire_video.qgz", content=archive, headers=H)
    faux = {**fichier("inventaire_video.qgz", archive), "sha256": "0" * 64}
    r = client.post(f"/projets/{pid}/qfield/publier", json={"projet_nom": "x", "fichiers": [faux]}, headers=H)
    assert r.status_code == 422
    assert client.get("/projets", headers=H).json() == []


def test_projet_du_gpkg_different_refuse(client, tmp_path):
    archive = qgz(tmp_path, "autre_projet")
    r = publier(client, "P_attendu", archive, plugin=None)
    assert r.status_code == 422 and "autre_projet" in r.json()["detail"]


def test_archive_invalide_refusee(client):
    donnees = b"pas un zip"
    client.put("/projets/P_zip/qfield/fichiers/x.qgz", content=donnees, headers=H)
    r = client.post("/projets/P_zip/qfield/publier", json={"projet_nom": "x", "fichiers": [fichier("x.qgz", donnees)]}, headers=H)
    assert r.status_code == 422


@pytest.mark.parametrize("chemin", [
    "/projets/../qfield/fichiers/a.qgz",
    "/projets/P1/qfield/fichiers/a.exe",
    "/projets/P1/qfield/fichiers/..qgz",
    "/projets/bad%20id/qfield/fichiers/a.qgz",
])
def test_noms_invalides(client, chemin):
    assert client.put(chemin, content=b"x", headers=H).status_code in (400, 404)


def test_jeton_obligatoire(client):
    assert client.get("/projets").status_code == 401
    assert client.put("/projets/P1/qfield/fichiers/a.qgz", content=b"x").status_code == 401


def test_trop_volumineux(client):
    r = client.put("/projets/P_gros/qfield/fichiers/a.qgz", content=b"\x00" * (5 * 1024**2 + 1), headers=H)
    assert r.status_code == 413


def test_trois_versions_gardees(client, tmp_path):
    pid = "P_versions"
    versions = [publier(client, pid, qgz(tmp_path, pid, raster=bytes([i]) * 1000)).json()["version"] for i in range(4)]
    assert len(set(versions)) == 4
    assert client.get(f"/projets/{pid}/qfield", headers=H).json()["version"] == versions[-1]
    assert client.get(f"/projets/{pid}/qfield/{versions[0]}/inventaire_video.qgz", headers=H).status_code == 404
    assert client.get(f"/projets/{pid}/qfield/{versions[1]}/inventaire_video.qgz", headers=H).status_code == 200


def test_projets_separes(client, tmp_path):
    publier(client, "A", qgz(tmp_path, "A"), projet_nom="Forêt A")
    publier(client, "B", qgz(tmp_path, "B"), projet_nom="Forêt B")
    assert {p["projet_id"]: p["projet_nom"] for p in client.get("/projets", headers=H).json()} == {"A": "Forêt A", "B": "Forêt B"}
