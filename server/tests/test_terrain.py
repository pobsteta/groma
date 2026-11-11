"""Mission 020, point 3: the "terrain" archive of a plot on the server (written before missions 021-022).

API EXPECTED FROM MISSION 021 (server, ``groma_server/api.py``), fixed here:

* The archive is ``<plot_id>.terrain.zip`` (a zip with ``terrain.gpkg`` at its root and ``photos/...``).
* Upload: the resumable protocol of ``/televersements`` is reused, with one more field in the body of
  ``POST /televersements``: ``"type": "terrain"`` (default ``"paquet"``: the video package, unchanged;
  any other value -> 422). Same chunks (``PUT .../televersements/{id}?offset=N``, 409 + ``recu`` on a
  wrong offset), same 413 above ``cfg.taille_max``, same bearer token (401 without).
* ``POST /televersements/{id}/terminer`` for a terrain upload: checks size and SHA-256 (422 if the
  SHA-256 differs, 409 if incomplete, as for a package) and that the bytes are a zip containing an entry
  ``terrain.gpkg`` (422 otherwise); then the archive REPLACES the previous one of the same
  (projet_id, plot_id) (last archive completed wins, the former is deleted: at most one file
  ``*.terrain.zip`` per plot on disk) and the answer is 200 ``{"type": "terrain", "projet_id", "plot_id",
  "recu_le"}`` (``recu_le``: ISO 8601 UTC with time zone). NO ``tache_id``, NO task in the queue (no GPU
  work: ``FileAttente.prendre()`` stays empty, ``/taches`` unaffected, ``/worker/prendre`` -> 204).
  A refused or unfinished upload never touches the archive already stored.
* Download: ``GET /projets/{projet_id}/placettes/{plot_id}/terrain.zip`` (and, for the default project
  "", ``GET /placettes/{plot_id}/terrain.zip``): 200 ``application/zip``, the exact bytes; 404 before any
  upload, for another project, or for an invalid plot_id; 401 without token.
* ``GET /placettes[?projet_id=]``: every entry gets ``terrain_envoye_le`` (the ``recu_le`` of the latest
  archive, or null). A plot that only has a terrain archive (no video task) is listed too, with
  ``id`` null, ``etat`` = ``"sans_paquet"``, ``terrain_envoye_le`` set. Video fields are unchanged.
* The video chain is independent: a terrain upload changes nothing to ``resultat.gpkg`` or to a task, and
  a finished video task does not remove the terrain archive.
"""
import hashlib
import io
import zipfile
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from groma_server.api import creer_app
from groma_server.config import Config
from groma_server.file_attente import FileAttente

H = {"Authorization": "Bearer secret"}
RACINE = Path(__file__).resolve().parents[2]


@pytest.fixture
def cfg(tmp_path):
    return Config(dossier_donnees=tmp_path, jeton="secret", taille_morceau=1024, reconstructeurs=("profondeur",))


@pytest.fixture
def c(cfg):
    return TestClient(creer_app(cfg))


def archive(plot="P001", contenu_gpkg: bytes | None = None, photos: dict[str, bytes] | None = None, nom_gpkg="terrain.gpkg") -> bytes:
    """A terrain archive as the phone builds it."""
    if contenu_gpkg is None:
        contenu_gpkg = (RACINE / "exemples/inventaire_video/inventaire_video.gpkg").read_bytes()
    tampon = io.BytesIO()
    with zipfile.ZipFile(tampon, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(nom_gpkg, contenu_gpkg)
        for nom, octets in (photos or {}).items():
            z.writestr(f"photos/{nom}", octets)
    return tampon.getvalue()


def demande(donnees: bytes, plot="P001", projet="", type_="terrain") -> dict:
    d = {"plot_id": plot, "taille": len(donnees), "sha256": hashlib.sha256(donnees).hexdigest(), "type": type_}
    if projet:
        d["projet_id"] = projet
    return d


def deposer(c, donnees: bytes, plot="P001", projet=""):
    """Whole upload in chunks; returns the answer of /terminer."""
    r = c.post("/televersements", json=demande(donnees, plot, projet), headers=H)
    assert r.status_code == 200, r.text
    tid, morceau = r.json()["id"], r.json()["taille_morceau"]
    recu = 0
    while recu < len(donnees):
        recu = c.put(f"/televersements/{tid}?offset={recu}", content=donnees[recu : recu + morceau], headers=H).json()["recu"]
    return c.post(f"/televersements/{tid}/terminer", headers=H)


def url(plot="P001", projet="proj") -> str:
    return f"/projets/{projet}/placettes/{plot}/terrain.zip" if projet else f"/placettes/{plot}/terrain.zip"


def entree(c, plot="P001", projet=None):
    r = c.get("/placettes" + (f"?projet_id={projet}" if projet is not None else ""), headers=H)
    return [p for p in r.json() if p["plot_id"] == plot]


# --- nominal ---------------------------------------------------------------------------------

def test_depot_nominal_puis_service(c, cfg):
    donnees = archive(photos={"P001_4_20261111T102000.jpg": b"JPEG" * 600})
    assert len(donnees) > 2 * 1024  # several chunks
    r = deposer(c, donnees, projet="proj")
    assert r.status_code == 200, r.text
    corps = r.json()
    assert corps["type"] == "terrain" and corps["plot_id"] == "P001" and corps["projet_id"] == "proj"
    assert "tache_id" not in corps
    g = c.get(url(), headers=H)
    assert g.status_code == 200
    assert g.headers["content-type"].startswith("application/zip")
    assert g.content == donnees


def test_aucune_tache_gpu_creee(c, cfg):
    assert deposer(c, archive(), projet="proj").status_code == 200
    assert FileAttente(cfg.base).prendre() is None
    assert FileAttente(cfg.base).lister() == []


def test_worker_distant_ne_voit_rien(tmp_path):
    cfg = Config(dossier_donnees=tmp_path, jeton="secret", jeton_worker="w", taille_morceau=1024, reconstructeurs=("profondeur",))
    c = TestClient(creer_app(cfg))
    assert deposer(c, archive(), projet="proj").status_code == 200
    assert c.post("/worker/prendre", headers={"Authorization": "Bearer w"}).status_code == 204


def test_projet_par_defaut_sans_projet_id(c):
    donnees = archive()
    assert deposer(c, donnees).status_code == 200
    assert c.get("/placettes/P001/terrain.zip", headers=H).content == donnees


# --- 404, jeton, identifiants ------------------------------------------------------------------

def test_404_avant_tout_envoi(c):
    assert c.get(url(), headers=H).status_code == 404
    assert c.get(url(projet=""), headers=H).status_code == 404


def test_404_autre_projet_et_autre_placette(c):
    assert deposer(c, archive(), projet="proj").status_code == 200
    assert c.get(url(projet="autre"), headers=H).status_code == 404
    assert c.get(url(plot="P002"), headers=H).status_code == 404
    assert c.get(url(projet=""), headers=H).status_code == 404  # the default project is another project


def test_404_identifiant_invalide(c):
    assert c.get("/projets/proj/placettes/..%2Fx/terrain.zip", headers=H).status_code == 404
    assert c.get("/projets/proj/placettes/P 1/terrain.zip", headers=H).status_code == 404


def test_jeton_exige_partout(c):
    donnees = archive()
    assert c.post("/televersements", json=demande(donnees)).status_code == 401
    assert c.post("/televersements", json=demande(donnees), headers={"Authorization": "Bearer faux"}).status_code == 401
    assert deposer(c, donnees, projet="proj").status_code == 200
    assert c.get(url()).status_code == 401
    assert c.get(url(), headers={"Authorization": "Bearer faux"}).status_code == 401
    assert c.get(url(), headers=H).status_code == 200


def test_identifiants_de_depot_valides(c):
    donnees = archive()
    for mauvais in ({"plot_id": "../P1"}, {"plot_id": "P 1"}, {"projet_id": "a/b"}):
        assert c.post("/televersements", json=demande(donnees) | mauvais, headers=H).status_code == 422


def test_type_inconnu_refuse(c):
    donnees = archive()
    assert c.post("/televersements", json=demande(donnees, type_="photos"), headers=H).status_code == 422


# --- reprise du dépôt -----------------------------------------------------------------------------

def test_depot_reprenable_apres_coupure(c):
    donnees = archive(photos={"p.jpg": bytes(range(256)) * 20})
    r = c.post("/televersements", json=demande(donnees, projet="proj"), headers=H)
    tid, morceau = r.json()["id"], r.json()["taille_morceau"]
    assert c.put(f"/televersements/{tid}?offset=0", content=donnees[:morceau], headers=H).status_code == 200
    r = c.put(f"/televersements/{tid}?offset=0", content=donnees[:morceau], headers=H)  # the phone did not see the answer
    assert r.status_code == 409 and r.json()["recu"] == morceau
    assert c.post(f"/televersements/{tid}/terminer", headers=H).status_code == 409  # incomplete
    recu = c.get(f"/televersements/{tid}", headers=H).json()["recu"]
    assert recu == morceau
    while recu < len(donnees):
        recu = c.put(f"/televersements/{tid}?offset={recu}", content=donnees[recu : recu + morceau], headers=H).json()["recu"]
    assert c.post(f"/televersements/{tid}/terminer", headers=H).status_code == 200
    assert c.get(url(), headers=H).content == donnees


def test_trop_volumineux_413(tmp_path):
    cfg = Config(dossier_donnees=tmp_path, jeton="secret", taille_max=500, reconstructeurs=("profondeur",))
    c = TestClient(creer_app(cfg))
    donnees = archive()
    assert len(donnees) > 500
    assert c.post("/televersements", json=demande(donnees), headers=H).status_code == 413


# --- refus qui ne touchent pas à l'archive déjà reçue ----------------------------------------------

def test_somme_de_controle_fausse_422_et_ancienne_archive_gardee(c):
    ancienne = archive(photos={"a.jpg": b"ancienne"})
    assert deposer(c, ancienne, projet="proj").status_code == 200
    nouvelle = archive(photos={"a.jpg": b"nouvelle"})
    d = demande(nouvelle, projet="proj") | {"sha256": "0" * 64}
    tid = c.post("/televersements", json=d, headers=H).json()["id"]
    c.put(f"/televersements/{tid}?offset=0", content=nouvelle, headers=H)
    assert c.post(f"/televersements/{tid}/terminer", headers=H).status_code == 422
    assert c.get(url(), headers=H).content == ancienne


def test_archive_illisible_422(c):
    ancienne = archive()
    assert deposer(c, ancienne, projet="proj").status_code == 200
    assert deposer(c, b"ceci n'est pas un zip " * 20, projet="proj").status_code == 422
    assert deposer(c, archive(nom_gpkg="autre.gpkg"), projet="proj").status_code == 422
    assert c.get(url(), headers=H).content == ancienne


def test_renvoi_non_termine_ne_remplace_pas(c):
    ancienne = archive(photos={"a.jpg": b"ancienne"})
    assert deposer(c, ancienne, projet="proj").status_code == 200
    nouvelle = archive(photos={"a.jpg": b"nouvelle" * 500})
    tid = c.post("/televersements", json=demande(nouvelle, projet="proj"), headers=H).json()["id"]
    c.put(f"/televersements/{tid}?offset=0", content=nouvelle[:1024], headers=H)
    assert c.get(url(), headers=H).content == ancienne


# --- renvoi : le dernier reçu gagne ---------------------------------------------------------------------

def test_renvoi_remplace_et_ancien_non_servi(c, tmp_path):
    v1 = archive(photos={"a.jpg": b"version 1"})
    v2 = archive(photos={"a.jpg": b"version 2", "b.jpg": b"nouvelle photo"})
    assert deposer(c, v1, projet="proj").status_code == 200
    assert deposer(c, v2, projet="proj").status_code == 200
    g = c.get(url(), headers=H)
    assert g.content == v2 and g.content != v1
    assert len(list(tmp_path.rglob("*.terrain.zip"))) == 1, "l'ancienne archive est supprimée"


def test_dernier_recu_gagne_meme_si_commence_avant(c):
    a, b = archive(photos={"x.jpg": b"A"}), archive(photos={"x.jpg": b"B"})
    ta = c.post("/televersements", json=demande(a, projet="proj"), headers=H).json()["id"]  # started first
    tb = c.post("/televersements", json=demande(b, projet="proj"), headers=H).json()["id"]
    c.put(f"/televersements/{tb}?offset=0", content=b, headers=H)
    c.put(f"/televersements/{ta}?offset=0", content=a, headers=H)
    assert c.post(f"/televersements/{tb}/terminer", headers=H).status_code == 200
    assert c.post(f"/televersements/{ta}/terminer", headers=H).status_code == 200  # completed last
    assert c.get(url(), headers=H).content == a


def test_meme_placette_dans_deux_projets(c):
    a, b = archive(photos={"x.jpg": b"A"}), archive(photos={"x.jpg": b"B"})
    assert deposer(c, a, projet="p1").status_code == 200
    assert deposer(c, b, projet="p2").status_code == 200
    assert c.get(url(projet="p1"), headers=H).content == a
    assert c.get(url(projet="p2"), headers=H).content == b


# --- /placettes ---------------------------------------------------------------------------------------------

def _iso(s: str) -> datetime:
    d = datetime.fromisoformat(s)
    assert d.tzinfo is not None, "date avec fuseau horaire"
    return d


def test_placettes_indique_la_date_du_dernier_envoi(c, cfg):
    f = FileAttente(cfg.base)
    f.ajouter("P001", "/nulle/part.zip", "proj")  # a video package waiting
    avant = entree(c, "P001", "proj")
    assert len(avant) == 1 and avant[0]["terrain_envoye_le"] is None and avant[0]["etat"] == "en_attente"
    assert deposer(c, archive(), projet="proj").status_code == 200
    e1 = entree(c, "P001", "proj")
    assert len(e1) == 1, "pas de doublon pour la placette"
    assert e1[0]["etat"] == "en_attente" and e1[0]["id"] == avant[0]["id"], "champs vidéo inchangés"
    t1 = _iso(e1[0]["terrain_envoye_le"])
    assert abs(datetime.now(timezone.utc) - t1) < timedelta(seconds=60)
    assert deposer(c, archive(photos={"n.jpg": b"1"}), projet="proj").status_code == 200
    t2 = _iso(entree(c, "P001", "proj")[0]["terrain_envoye_le"])
    assert t2 >= t1


def test_placette_avec_terrain_seulement_listee(c):
    assert deposer(c, archive(), plot="P007", projet="proj").status_code == 200
    e = entree(c, "P007", "proj")
    assert len(e) == 1
    assert e[0]["projet_id"] == "proj" and e[0]["id"] is None and e[0]["etat"] == "sans_paquet"
    _iso(e[0]["terrain_envoye_le"])
    assert entree(c, "P007", "autre") == []


def test_placettes_sans_envoi_a_terrain_envoye_le_nul(c, cfg):
    FileAttente(cfg.base).ajouter("P002", "/nulle/part.zip", "proj")
    assert entree(c, "P002")[0]["terrain_envoye_le"] is None


# --- chaîne vidéo indépendante ----------------------------------------------------------------------------------

def test_video_et_terrain_independants(c, cfg, tmp_path):
    f = FileAttente(cfg.base)
    tid = f.ajouter("P001", "/nulle/part.zip", "proj")
    prise = f.prendre()
    assert prise["id"] == tid
    gpkg, rj, rh = (tmp_path / n for n in ("def.gpkg", "rap.json", "rap.html"))
    gpkg.write_bytes(b"SQLite format 3\x00 definitif"); rj.write_text('{"plot_id": "P001"}'); rh.write_text("<html></html>")
    f.terminer(tid, str(gpkg), str(rj), str(rh), "ok")
    assert c.get("/projets/proj/placettes/P001/resultat.gpkg", headers=H).content == gpkg.read_bytes()
    donnees = archive()
    assert deposer(c, donnees, projet="proj").status_code == 200
    assert c.get("/projets/proj/placettes/P001/resultat.gpkg", headers=H).content == gpkg.read_bytes()
    assert c.get(f"/taches/{tid}", headers=H).json()["etat"] == "terminee"
    assert c.get(url(), headers=H).content == donnees
    e = entree(c, "P001", "proj")
    assert len(e) == 1 and e[0]["etat"] == "terminee" and e[0]["terrain_envoye_le"] is not None
