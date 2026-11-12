"""Mission 027: reception quality columns of ``centres_mesures`` (schema 1.6.0, still unpublished)."""
import pytest

gpd = pytest.importorskip("geopandas")
pyogrio = pytest.importorskip("pyogrio")
from shapely.geometry import Point

from groma.gpkg import charger_schema, creer_gpkg, lire_points

COLONNES = {
    "nb_positions": "INTEGER",
    "nb_satellites": "INTEGER",
    "hdop": "REAL",
    "age_corrections_s": "REAL",
    "duree_mesure_s": "REAL",
}


def _placettes():
    return gpd.GeoDataFrame({"plot_id": ["P01"]}, geometry=[Point(925000, 6700000)], crs=2154)


def test_colonnes_dans_le_schema():
    s = charger_schema()
    assert s["version"] == "1.6.0"
    centres = next(c for c in s["couches"] if c["nom"] == "centres_mesures")
    types = {c["nom"]: c["type"] for c in centres["colonnes"]}
    for nom, t in COLONNES.items():
        assert types[nom] == t
    assert all(len(n) <= 30 for n in types)


def test_gpkg_cree_porte_les_colonnes_vides(tmp_path):
    chemin = creer_gpkg(tmp_path / "t.gpkg", _placettes())
    assert set(COLONNES) <= set(gpd.read_file(chemin, layer="centres_mesures").columns)


def test_python_ecrit_et_relit_les_colonnes(tmp_path):
    centres = gpd.GeoDataFrame(
        {
            "plot_id": ["P01"], "qualite_gnss": ["fix"], "precision_h_m": [0.02],
            "nb_positions": [24], "nb_satellites": [11], "hdop": [0.9],
            "age_corrections_s": [1.5], "duree_mesure_s": [12.0],
        },
        geometry=[Point(925000, 6700000)], crs=2154,
    )
    chemin = creer_gpkg(tmp_path / "t.gpkg", _placettes(), centres_mesures=centres)
    lu = lire_points(chemin, "centres_mesures")[0]
    assert (lu["nb_positions"], lu["nb_satellites"]) == (24, 11)
    assert (lu["hdop"], lu["age_corrections_s"], lu["duree_mesure_s"]) == (0.9, 1.5, 12.0)
