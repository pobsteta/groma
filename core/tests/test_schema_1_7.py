"""Mission 037a: acceptance tests of schema 1.7.0 (brief 2026-11-20, card 037).

Written without reading the implementation. Tile size and margins are read from the
schema, never hard-coded (card 037, "valeur provisoire").
"""
from __future__ import annotations

import json
import re
import shutil
import sqlite3
from pathlib import Path

import pytest

gpd = pytest.importorskip("geopandas")
pytest.importorskip("pyogrio")
from shapely.geometry import Point, Polygon

from groma import gpkg
from groma.gpkg import charger_schema, creer_gpkg, lire_points

RACINE = Path(__file__).resolve().parents[2]


def _version(v: str) -> tuple[int, ...]:
    return tuple(int(p) for p in v.split("."))


def _houppiers_couche():
    return next(c for c in charger_schema()["couches"] if c["nom"] == "houppiers")


# ---- schema ---------------------------------------------------------------


def test_version_au_moins_1_7_0():
    assert _version(charger_schema()["version"]) >= (1, 7, 0)


def test_version_exactement_1_7_0_pour_cette_mission():
    assert charger_schema()["version"] == "1.7.0"


def test_rasters_par_placette_inchanges():
    r = charger_schema()["rasters"]
    assert r["chemins"] == {"mnt": "rasters/{plot_id}_mnt.tif", "mnh": "rasters/{plot_id}_mnh.tif"}
    assert r["resolution_m"] == 0.5
    assert r["marge_m"] == 10
    assert "Float32" in r["format"] and "-9999" in r["format"] and "2154" in r["format"]


def test_cle_dalles_mnt():
    d = charger_schema()["rasters"]["dalles"]
    assert d["mnt"] == "rasters/mnt_{x}_{y}.tif"
    assert d["cote_m"] == 1000
    assert d["marge_foret_m"] == 50


def test_cote_de_dalle_multiple_du_pixel_et_du_kilometre():
    r = charger_schema()["rasters"]
    assert r["dalles"]["cote_m"] % r["resolution_m"] == 0
    assert r["dalles"]["cote_m"] % 1000 == 0  # tiles aligned on the kilometre


def test_pas_de_dalle_mnh():
    d = charger_schema()["rasters"]["dalles"]
    assert "mnh" not in d
    assert not any("mnh" in str(v).lower() for k, v in d.items() if k != "remarque")


def test_cle_cvat():
    c = charger_schema()["rasters"]["cvat"]
    assert c["chemin"] == "rasters/cvat.tif"
    texte = json.dumps(c, ensure_ascii=False)
    assert "Byte" in texte and "2154" in texte and "DEFLATE" in texte
    assert "254" in texte  # 1 + round(v * 254)


def test_chemin_cvat_et_dalles_a_plat():
    r = charger_schema()["rasters"]
    for chemin in (r["dalles"]["mnt"], r["cvat"]["chemin"]):
        assert re.fullmatch(r"rasters/[^/]+\.tif", chemin)


def test_houppiers_colonnes_sommet_requises():
    cols = {c["nom"]: c for c in _houppiers_couche()["colonnes"]}
    for nom in ("x_sommet", "y_sommet"):
        assert cols[nom]["type"] == "REAL"
        assert cols[nom].get("requis") is True


def test_houppiers_plot_id_reste_facultatif():
    cols = {c["nom"]: c for c in _houppiers_couche()["colonnes"]}
    assert not cols["plot_id"].get("requis")


def test_houppiers_colonnes_historiques_conservees():
    noms = [c["nom"] for c in _houppiers_couche()["colonnes"]]
    assert {"plot_id", "houppier_id", "h_max", "surface_m2", "x_sommet", "y_sommet"} <= set(noms)
    assert all(len(n) <= 30 and n == n.lower() and n.isascii() for n in noms)


def test_houppiers_remarque_toute_l_emprise():
    assert "emprise" in _houppiers_couche()["remarque"].lower()


def test_index_sommet_declare_dans_le_schema():
    autres = {k: v for k, v in _houppiers_couche().items() if k != "colonnes"}
    texte = json.dumps(autres)
    assert "x_sommet" in texte and "y_sommet" in texte


# ---- creer_gpkg -----------------------------------------------------------


def _placettes():
    return gpd.GeoDataFrame({"plot_id": ["P01"]}, geometry=[Point(849_500, 6_689_500)], crs=2154)


def _houppiers():
    def carre(x, y, c=5.0):
        return Polygon([(x - c, y - c), (x + c, y - c), (x + c, y + c), (x - c, y + c)])

    return gpd.GeoDataFrame(
        {
            "plot_id": ["P01", None, None],
            "houppier_id": [1, 2, 3],
            "h_max": [20.5, 18.0, 25.0],
            "surface_m2": [100.0, 100.0, 100.0],
            "x_sommet": [849_500.5, 849_700.0, 850_100.25],
            "y_sommet": [6_689_500.5, 6_689_800.0, 6_689_900.75],
        },
        geometry=[carre(849_500.5, 6_689_500.5), carre(849_700, 6_689_800), carre(850_100.25, 6_689_900.75)],
        crs=2154,
    )


@pytest.fixture
def gpkg_17(tmp_path):
    return creer_gpkg(tmp_path / "t.gpkg", _placettes(), houppiers=_houppiers())


def _sql(chemin, requete, *args):
    cx = sqlite3.connect(f"file:{chemin}?mode=ro", uri=True)
    try:
        return cx.execute(requete, args).fetchall()
    finally:
        cx.close()


def _index_colonnes(chemin):
    return [[r[2] for r in _sql(chemin, f"PRAGMA index_info('{i[1]}')")] for i in _sql(chemin, "PRAGMA index_list(houppiers)")]


def test_gpkg_houppiers_a_les_colonnes_sommet(gpkg_17):
    cols = [r[1] for r in _sql(gpkg_17, "PRAGMA table_info(houppiers)")]
    assert "x_sommet" in cols and "y_sommet" in cols


def test_gpkg_houppiers_vide_a_aussi_les_colonnes(tmp_path):
    chemin = creer_gpkg(tmp_path / "v.gpkg", _placettes())
    cols = [r[1] for r in _sql(chemin, "PRAGMA table_info(houppiers)")]
    assert "x_sommet" in cols and "y_sommet" in cols


def test_gpkg_sommets_aller_retour(gpkg_17):
    lus = gpd.read_file(gpkg_17, layer="houppiers").sort_values("houppier_id")
    assert list(lus["x_sommet"]) == [849_500.5, 849_700.0, 850_100.25]
    assert list(lus["y_sommet"]) == [6_689_500.5, 6_689_800.0, 6_689_900.75]


def test_gpkg_sommets_dans_leur_polygone(gpkg_17):
    for _, h in gpd.read_file(gpkg_17, layer="houppiers").iterrows():
        assert h.geometry.contains(Point(h["x_sommet"], h["y_sommet"]))


def test_gpkg_index_btree_sur_les_sommets(gpkg_17):
    assert ["x_sommet", "y_sommet"] in _index_colonnes(gpkg_17)


def test_gpkg_index_present_aussi_sans_houppier(tmp_path):
    chemin = creer_gpkg(tmp_path / "v.gpkg", _placettes())
    assert ["x_sommet", "y_sommet"] in _index_colonnes(chemin)


def test_gpkg_filtre_between_sur_les_sommets(gpkg_17):
    # The phone filters with BETWEEN: only the 2 houppiers east of x = 849 600.
    lignes = _sql(
        gpkg_17,
        "SELECT houppier_id FROM houppiers WHERE x_sommet BETWEEN ? AND ? AND y_sommet BETWEEN ? AND ? ORDER BY 1",
        849_600, 851_000, 6_689_000, 6_690_000,
    )
    assert lignes == [(2,), (3,)]


def test_gpkg_plot_id_null_accepte(gpkg_17):
    lignes = _sql(gpkg_17, "SELECT houppier_id, plot_id FROM houppiers ORDER BY houppier_id")
    assert lignes == [(1, "P01"), (2, None), (3, None)]


def test_gpkg_houppiers_toute_l_emprise_nombre_conserve(gpkg_17):
    assert _sql(gpkg_17, "SELECT count(*) FROM houppiers") == [(3,)]


def test_gpkg_houppier_sans_colonne_plot_id(tmp_path):
    h = _houppiers().drop(columns=["plot_id"])
    chemin = creer_gpkg(tmp_path / "s.gpkg", _placettes(), houppiers=h)
    assert [r[0] for r in _sql(chemin, "SELECT plot_id FROM houppiers")] == [None, None, None]


def test_gpkg_n_ajoute_pas_de_table_de_dalles(gpkg_17):
    tables = {r[0] for r in _sql(gpkg_17, "SELECT name FROM sqlite_master WHERE type='table'")}
    assert not any("dalle" in t.lower() or "cvat" in t.lower() for t in tables)


# ---- schema 1.6.0 stays readable ---------------------------------------------


@pytest.fixture
def gpkg_16(tmp_path):
    src = RACINE / "fixtures" / "schema_1_6_0" / "inventaire_video.gpkg"
    dst = tmp_path / "inventaire_video.gpkg"
    shutil.copy(src, dst)
    return dst


def test_fixture_16_est_bien_sans_sommets(gpkg_16):
    cols = [r[1] for r in _sql(gpkg_16, "PRAGMA table_info(houppiers)")]
    assert "x_sommet" not in cols and "y_sommet" not in cols


def test_gpkg_16_relu_sans_erreur(gpkg_16):
    assert [p["plot_id"] for p in lire_points(gpkg_16, "placettes")] == ["P001", "P002"]
    assert len(gpd.read_file(gpkg_16, layer="houppiers")) == 50


def test_gpkg_16_ecriture_protocole_telephone_toujours_possible(gpkg_16):
    gpkg.mettre_a_jour_etat(gpkg_16, "P001", "en_cours")
    assert [p["etat_traitement"] for p in lire_points(gpkg_16, "placettes", "P001")] == ["en_cours"]


# ---- committed example -------------------------------------------------------


EXEMPLE = RACINE / "exemples" / "inventaire_video"


def test_exemple_houppiers_avec_sommets_dans_leur_polygone():
    h = gpd.read_file(EXEMPLE / "inventaire_video.gpkg", layer="houppiers")
    assert len(h) > 0
    assert h["x_sommet"].notna().all() and h["y_sommet"].notna().all()
    assert all(g.contains(Point(x, y)) for g, x, y in zip(h.geometry, h["x_sommet"], h["y_sommet"]))


def test_exemple_a_deux_dalles_et_un_cvat_sans_mnh_de_dalle():
    noms = sorted(p.name for p in (EXEMPLE / "rasters").glob("*.tif"))
    assert len([n for n in noms if re.fullmatch(r"mnt_\d{4}_\d{4}\.tif", n)]) >= 2
    assert "cvat.tif" in noms
    assert not [n for n in noms if re.fullmatch(r"mnh_\d{4}_\d{4}\.tif", n)]
    assert {"P001_mnt.tif", "P001_mnh.tif"} <= set(noms)  # per-plot rasters unchanged


def test_exemple_conforme_a_verifier_projet():
    pytest.importorskip("rasterio")
    from groma.dalles import verifier_projet

    assert verifier_projet(EXEMPLE) == []


def test_exemple_leger():
    taille = sum(
        p.stat().st_size
        for p in (EXEMPLE / "rasters").glob("*.tif")
        if re.fullmatch(r"(mnt_\d{4}_\d{4}|cvat)\.tif", p.name)
    )
    assert taille < 2_000_000
