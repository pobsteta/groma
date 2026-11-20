"""Mission 037a: acceptance tests of ``groma.dalles`` (schema 1.7.0), written from the
brief 2026-11-20 and the card 037 only, without reading the implementation.

Assumptions on the API (card 037 gives names only), to be confirmed by the orchestrator:
  * ``nom_dalle(x_km, y_km) -> "rasters/mnt_XXXX_YYYY.tif"``
  * ``dalle_du_point(x, y)`` -> the tile (name, or ``(x_km, y_km)`` tuple), Lambert-93 metres
  * ``dalles_du_rectangle(xmin, ymin, xmax, ymax)`` -> iterable of tiles (names or tuples)
  * ``verifier_projet(dossier)`` -> list of French messages, ``dossier`` holds ``rasters/``
The tile size is read from the schema (``rasters.dalles.cote_m``), never hard-coded.
"""
from __future__ import annotations

import re

import pytest

np = pytest.importorskip("numpy")
rasterio = pytest.importorskip("rasterio")
from rasterio.transform import from_origin

from groma import dalles
from groma.gpkg import charger_schema

_RASTERS = charger_schema()["rasters"]
RES = _RASTERS["resolution_m"]
# Until 037 is written the key is absent: fall back so that collection works and the
# tests fail on the missing behaviour, not on the collection.
COTE = _RASTERS.get("dalles", {}).get("cote_m", 1000)
NB_PIXELS = int(round(COTE / RES))


def _nom(t) -> str:
    """Normalise a tile given as a name or as (x_km, y_km) to its name."""
    if isinstance(t, str):
        return t
    return dalles.nom_dalle(int(t[0]), int(t[1]))


# ---- naming ---------------------------------------------------------------


def test_nom_dalle_exemple_du_brief():
    assert dalles.nom_dalle(849, 6689) == "rasters/mnt_0849_6689.tif"


def test_nom_dalle_quatre_chiffres_completes_par_des_zeros():
    assert dalles.nom_dalle(5, 66) == "rasters/mnt_0005_0066.tif"


def test_nom_dalle_a_plat_dans_rasters():
    nom = dalles.nom_dalle(849, 6689)
    assert nom.count("/") == 1 and nom.startswith("rasters/")


def test_nom_dalle_ne_se_confond_pas_avec_un_raster_de_placette():
    assert not re.fullmatch(r"rasters/.+_(mnt|mnh)\.tif", dalles.nom_dalle(849, 6689))


def test_point_interieur():
    assert _nom(dalles.dalle_du_point(849_500.0, 6_689_250.0)) == "rasters/mnt_0849_6689.tif"


def test_point_coin_sud_ouest_exact():
    assert _nom(dalles.dalle_du_point(849_000.0, 6_689_000.0)) == "rasters/mnt_0849_6689.tif"


def test_point_sur_bord_a_1000_m_pile_va_a_la_dalle_nord_est():
    assert _nom(dalles.dalle_du_point(850_000.0, 6_690_000.0)) == "rasters/mnt_0850_6690.tif"


def test_point_sur_bord_est_seul_va_a_l_est():
    assert _nom(dalles.dalle_du_point(850_000.0, 6_689_500.0)) == "rasters/mnt_0850_6689.tif"


def test_point_juste_avant_le_bord_reste_dans_la_dalle():
    assert _nom(dalles.dalle_du_point(849_999.999, 6_689_999.999)) == "rasters/mnt_0849_6689.tif"


def test_rectangle_a_cheval_sur_quatre_dalles():
    noms = [_nom(t) for t in dalles.dalles_du_rectangle(849_900.0, 6_689_900.0, 850_100.0, 6_690_100.0)]
    assert sorted(noms) == [
        "rasters/mnt_0849_6689.tif",
        "rasters/mnt_0849_6690.tif",
        "rasters/mnt_0850_6689.tif",
        "rasters/mnt_0850_6690.tif",
    ]


def test_rectangle_dans_une_seule_dalle():
    noms = [_nom(t) for t in dalles.dalles_du_rectangle(849_100.0, 6_689_100.0, 849_900.0, 6_689_900.0)]
    assert noms == ["rasters/mnt_0849_6689.tif"]


def test_rectangle_couvre_chaque_point():
    r = (849_900.0, 6_689_400.0, 851_100.0, 6_689_600.0)
    noms = {_nom(t) for t in dalles.dalles_du_rectangle(*r)}
    assert len(noms) == 3  # 3 tiles wide, 1 high
    for x in (r[0], 850_000.0, 850_500.0, r[2]):
        assert _nom(dalles.dalle_du_point(x, r[1])) in noms


@pytest.mark.parametrize("x,y", [(-1.0, 6_689_000.0), (849_000.0, -1.0), (-500_000.0, -500_000.0)])
def test_coordonnees_negatives_refusees(x, y):
    with pytest.raises(ValueError):
        dalles.dalle_du_point(x, y)


# ---- verifier_projet ------------------------------------------------------


def _ecrire(chemin, *, dtype="float32", n=NB_PIXELS, res=RES, x0=849_000.0, y0=6_690_000.0,
            crs=2154, nodata=-9999.0):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    arr = np.full((n, n), 12.0, dtype=dtype)
    with rasterio.open(
        chemin, "w", driver="GTiff", width=n, height=n, count=1, dtype=dtype, crs=f"EPSG:{crs}",
        transform=from_origin(x0, y0, res, res), nodata=nodata, compress="deflate", tiled=False,
    ) as dst:
        dst.write(arr, 1)
    return chemin


def _cvat(chemin, dtype="uint8"):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    arr = np.full((50, 40), 100, dtype=dtype)
    with rasterio.open(
        chemin, "w", driver="GTiff", width=40, height=50, count=1, dtype=dtype, crs="EPSG:2154",
        transform=from_origin(849_000.0, 6_690_000.0, 2.0, 2.0), nodata=0, compress="deflate",
    ) as dst:
        dst.write(arr, 1)
    return chemin


@pytest.fixture
def projet(tmp_path):
    _ecrire(tmp_path / "rasters" / "mnt_0849_6689.tif")
    return tmp_path


def test_dalle_conforme_acceptee(projet):
    assert dalles.verifier_projet(projet) == []


def test_dalle_et_cvat_conformes_acceptes(projet):
    _cvat(projet / "rasters" / "cvat.tif")
    assert dalles.verifier_projet(projet) == []


def test_dossier_sans_dalles_est_conforme(tmp_path):
    (tmp_path / "rasters").mkdir()
    assert dalles.verifier_projet(tmp_path) == []


def test_dossier_sans_rasters_est_conforme(tmp_path):
    assert dalles.verifier_projet(tmp_path) == []


def test_rasters_par_placette_ignores(tmp_path):
    _ecrire(tmp_path / "rasters" / "P001_mnt.tif", n=120)
    assert dalles.verifier_projet(tmp_path) == []


def _un_ecart(ecarts, fichier):
    assert len(ecarts) == 1, ecarts
    assert isinstance(ecarts[0], str) and fichier in ecarts[0]


def test_dalle_float64_un_ecart(tmp_path):
    _ecrire(tmp_path / "rasters" / "mnt_0849_6689.tif", dtype="float64")
    _un_ecart(dalles.verifier_projet(tmp_path), "mnt_0849_6689.tif")


def test_dalle_decalee_de_0_5_m_un_ecart(tmp_path):
    _ecrire(tmp_path / "rasters" / "mnt_0849_6689.tif", x0=849_000.5)
    _un_ecart(dalles.verifier_projet(tmp_path), "mnt_0849_6689.tif")


def test_dalle_de_1999_pixels_un_ecart(tmp_path):
    _ecrire(tmp_path / "rasters" / "mnt_0849_6689.tif", n=NB_PIXELS - 1)
    _un_ecart(dalles.verifier_projet(tmp_path), "mnt_0849_6689.tif")


def test_dalle_nommee_autrement_que_son_coin_sud_ouest_un_ecart(tmp_path):
    # Content is the tile 849/6689 but the name says 850/6689.
    _ecrire(tmp_path / "rasters" / "mnt_0850_6689.tif")
    _un_ecart(dalles.verifier_projet(tmp_path), "mnt_0850_6689.tif")


def test_dalle_mauvais_crs_un_ecart(tmp_path):
    _ecrire(tmp_path / "rasters" / "mnt_0849_6689.tif", crs=4326)
    _un_ecart(dalles.verifier_projet(tmp_path), "mnt_0849_6689.tif")


def test_cvat_float32_un_ecart(tmp_path):
    _cvat(tmp_path / "rasters" / "cvat.tif", dtype="float32")
    _un_ecart(dalles.verifier_projet(tmp_path), "cvat.tif")


def test_deux_defauts_deux_ecarts(tmp_path):
    _ecrire(tmp_path / "rasters" / "mnt_0849_6689.tif", dtype="float64")
    _cvat(tmp_path / "rasters" / "cvat.tif", dtype="float32")
    assert len(dalles.verifier_projet(tmp_path)) == 2


def test_ecarts_en_francais(tmp_path):
    _ecrire(tmp_path / "rasters" / "mnt_0849_6689.tif", dtype="float64")
    msg = dalles.verifier_projet(tmp_path)[0].lower()
    assert any(m in msg for m in ("dalle", "attendu", "format", "type", "écart", "float"))
    assert not re.search(r"\b(expected|wrong|invalid|mismatch)\b", msg)
