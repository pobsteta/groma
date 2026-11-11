"""Mission 021 (reprise 2/3): identifiers made of dots never reach a path; hostile zip entry names refused."""
import hashlib
import io
import zipfile

import pytest
from fastapi.testclient import TestClient

from groma_server.api import creer_app
from groma_server.config import Config

H = {"Authorization": "Bearer secret"}


@pytest.fixture
def c(tmp_path):
    return TestClient(creer_app(Config(dossier_donnees=tmp_path, jeton="secret", taille_morceau=1024, reconstructeurs=("profondeur",))))


def zip_de(noms: list[str]) -> bytes:
    t = io.BytesIO()
    with zipfile.ZipFile(t, "w") as z:
        for n in noms:
            z.writestr(n, b"x")
    return t.getvalue()


def demande(d: bytes, **kw) -> dict:
    return {"plot_id": "P1", "taille": len(d), "sha256": hashlib.sha256(d).hexdigest(), **kw}


@pytest.mark.parametrize("type_", ["terrain", "paquet"])
@pytest.mark.parametrize("champ,valeur", [("projet_id", ".."), ("projet_id", "."), ("projet_id", "..."), ("plot_id", ".."), ("plot_id", "."), ("plot_id", "...")])
def test_identifiant_de_points_refuse_au_depot(c, tmp_path, type_, champ, valeur):
    d = zip_de(["terrain.gpkg"])
    r = c.post("/televersements", json=demande(d, type=type_) | {champ: valeur}, headers=H)
    assert r.status_code == 422
    assert not list(tmp_path.parent.glob("*.terrain.zip"))
    assert not list(tmp_path.rglob("*.terrain.zip"))


@pytest.mark.parametrize("url", [
    "/projets/../placettes/P1/terrain.zip", "/projets/./placettes/P1/terrain.zip",
    "/projets/.../placettes/P1/terrain.zip", "/projets/proj/placettes/../terrain.zip",
    "/projets/proj/placettes/.../terrain.zip", "/projets/%2e%2e/placettes/P1/terrain.zip",
    "/projets/proj/placettes/%2e%2e/terrain.zip", "/placettes/../terrain.zip", "/placettes/.../terrain.zip",
])
def test_get_identifiant_de_points_jamais_200(c, url):
    assert c.get(url, headers=H).status_code in (404, 422)


@pytest.mark.parametrize("nom", ["/etc/passwd", "../x.gpkg", "photos/../../x.jpg", "photos\\..\\x.jpg", "C:/x.jpg"])
def test_entree_hostile_422(c, nom):
    d = zip_de(["terrain.gpkg", nom])
    tid = c.post("/televersements", json=demande(d, type="terrain", projet_id="proj"), headers=H).json()["id"]
    recu = 0
    while recu < len(d):
        recu = c.put(f"/televersements/{tid}?offset={recu}", content=d[recu:recu + 1024], headers=H).json()["recu"]
    assert c.post(f"/televersements/{tid}/terminer", headers=H).status_code == 422
    assert c.get("/projets/proj/placettes/P1/terrain.zip", headers=H).status_code == 404
