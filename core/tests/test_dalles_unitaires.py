"""Mission 037: unit tests of ``groma.dalles`` and of the schema 1.7.0 writes of
``creer_gpkg`` (developer side; the acceptance tests are those of 037a).

Coordinates are Lambert-93 (EPSG:2154), metres, in the order (x = easting, y = northing).
"""
from __future__ import annotations

import copy
import math
from pathlib import Path

import pytest

np = pytest.importorskip("numpy")
rasterio = pytest.importorskip("rasterio")
from rasterio.transform import from_origin

from groma import dalles
from groma.gpkg import charger_schema, sql_index

RACINE = Path(__file__).resolve().parents[2]
EXEMPLE = RACINE / "exemples" / "inventaire_video"
_R = charger_schema()["rasters"]
RES = _R["resolution_m"]
N = int(round(_R["dalles"]["cote_m"] / RES))


# ---- naming -------------------------------------------------------------------


@pytest.mark.parametrize("x_km,y_km", [(10_000, 6689), (849, 10_000), (-1, 6689)])
def test_nom_dalle_hors_quatre_chiffres_refuse(x_km, y_km):
    with pytest.raises(ValueError):
        dalles.nom_dalle(x_km, y_km)


def test_nom_dalle_suit_le_gabarit_du_schema():
    assert dalles.nom_dalle(0, 0) == _R["dalles"]["mnt"].format(x="0000", y="0000")


def test_rectangle_reduit_a_un_point_donne_sa_dalle():
    assert dalles.dalles_du_rectangle(849_500.0, 6_689_500.0, 849_500.0, 6_689_500.0) == [(849, 6689)]


def test_rectangle_pile_sur_les_kilometres_une_seule_dalle():
    assert dalles.dalles_du_rectangle(849_000.0, 6_689_000.0, 850_000.0, 6_690_000.0) == [(849, 6689)]


def test_rectangle_inverse_refuse():
    with pytest.raises(ValueError):
        dalles.dalles_du_rectangle(850_000.0, 6_689_000.0, 849_000.0, 6_690_000.0)


def test_rectangle_trie_par_x_puis_y():
    assert dalles.dalles_du_rectangle(849_900.0, 6_689_900.0, 850_100.0, 6_690_100.0) == [
        (849, 6689), (849, 6690), (850, 6689), (850, 6690),
    ]


def test_cote_lue_dans_le_schema(monkeypatch):
    s = copy.deepcopy(charger_schema())
    s["rasters"]["dalles"]["cote_m"] = 2000
    monkeypatch.setattr(dalles, "charger_schema", lambda: s)
    # 2 km tiles: corners on even kilometres.
    assert dalles.dalle_du_point(851_500.0, 6_689_500.0) == (850, 6688)
    assert dalles.dalles_du_rectangle(849_000.0, 6_689_000.0, 852_000.0, 6_690_000.0) == [(848, 6688), (850, 6688)]


def test_point_du_terrain_marsannay_exemple_du_brief():
    # Brief § 2.2: 849 000-850 000 m in X and 6 689 000-6 690 000 m in Y is mnt_0849_6689.
    assert dalles.nom_dalle(*dalles.dalle_du_point(849_312.4, 6_689_871.9)) == "rasters/mnt_0849_6689.tif"


# ---- verifier_projet: extra defects -------------------------------------------------


def _dalle(chemin, **opts):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    profil = dict(driver="GTiff", width=N, height=N, count=1, dtype="float32", crs="EPSG:2154",
                  transform=from_origin(849_000.0, 6_690_000.0, RES, RES), nodata=-9999.0, compress="deflate",
                  tiled=False)
    profil.update(opts)
    with rasterio.open(chemin, "w", **profil) as dst:
        dst.write(np.full((N, N), 300.0, dtype=profil["dtype"]), 1)


def test_fichier_illisible(tmp_path):
    (tmp_path / "rasters").mkdir()
    (tmp_path / "rasters" / "mnt_0849_6689.tif").write_bytes(b"pas un tiff")
    ecarts = dalles.verifier_projet(tmp_path)
    assert len(ecarts) == 1 and "illisible" in ecarts[0] and "mnt_0849_6689.tif" in ecarts[0]


def test_cvat_illisible(tmp_path):
    (tmp_path / "rasters").mkdir()
    (tmp_path / "rasters" / "cvat.tif").write_bytes(b"")
    ecarts = dalles.verifier_projet(tmp_path)
    assert len(ecarts) == 1 and "illisible" in ecarts[0] and "cvat.tif" in ecarts[0]


def test_dalle_mnh_signalee(tmp_path):
    _dalle(tmp_path / "rasters" / "mnh_0849_6689.tif")
    ecarts = dalles.verifier_projet(tmp_path)
    assert len(ecarts) == 1 and "mnh_0849_6689.tif" in ecarts[0] and "MNH" in ecarts[0]


def test_dalle_tuilee_signalee(tmp_path):
    _dalle(tmp_path / "rasters" / "mnt_0849_6689.tif", tiled=True, blockxsize=256, blockysize=256)
    ecarts = dalles.verifier_projet(tmp_path)
    assert len(ecarts) == 1 and "tuilée" in ecarts[0]


def test_dalle_avec_predicteur_signalee(tmp_path):
    _dalle(tmp_path / "rasters" / "mnt_0849_6689.tif", predictor=3)
    ecarts = dalles.verifier_projet(tmp_path)
    assert len(ecarts) == 1 and "prédicteur" in ecarts[0]


def test_dalle_mauvais_nodata_signale(tmp_path):
    _dalle(tmp_path / "rasters" / "mnt_0849_6689.tif", nodata=0.0)
    ecarts = dalles.verifier_projet(tmp_path)
    assert len(ecarts) == 1 and "nodata" in ecarts[0]


def test_constantes_conformes_au_texte_du_schema():
    assert "Float32" in _R["format"] and f"nodata {dalles.NODATA_MNT:g}" in _R["format"]
    assert dalles.TYPE_MNT == "float32"
    assert "Byte" in _R["cvat"]["format"] and f"{dalles.NODATA_CVAT:g} = nodata" in _R["cvat"]["format"]
    assert dalles.TYPE_CVAT == "uint8"


# ---- creer_gpkg (refusals happen before anything is written: fast) ------------------


def _gdfs():
    gpd = pytest.importorskip("geopandas")
    from shapely.geometry import Point

    placettes = gpd.GeoDataFrame({"plot_id": ["P01"]}, geometry=[Point(849_500, 6_689_500)], crs=2154)
    houppiers = gpd.GeoDataFrame(
        {"houppier_id": [1], "h_max": [20.0], "surface_m2": [78.0], "x_sommet": [849_500.0], "y_sommet": [6_689_500.0]},
        geometry=[Point(849_500, 6_689_500).buffer(5)], crs=2154,
    )
    return placettes, houppiers


@pytest.mark.parametrize("colonne", ["x_sommet", "y_sommet"])
def test_creer_gpkg_refuse_houppiers_sans_sommet(tmp_path, colonne):
    from groma.gpkg import creer_gpkg

    placettes, houppiers = _gdfs()
    with pytest.raises(ValueError, match=colonne):
        creer_gpkg(tmp_path / "t.gpkg", placettes, houppiers=houppiers.drop(columns=[colonne]))
    assert not (tmp_path / "t.gpkg").exists()


def test_creer_gpkg_refuse_sommet_vide(tmp_path):
    from groma.gpkg import creer_gpkg

    placettes, houppiers = _gdfs()
    houppiers["x_sommet"] = [math.nan]
    with pytest.raises(ValueError, match="x_sommet"):
        creer_gpkg(tmp_path / "t.gpkg", placettes, houppiers=houppiers)


def test_sql_index_depuis_le_schema():
    assert sql_index() == ["CREATE INDEX IF NOT EXISTS houppiers_sommet ON houppiers (x_sommet, y_sommet)"]


# ---- the committed example: a point of the ground lands at the right place ---------


def test_exemple_centres_des_placettes_dans_la_bonne_dalle_et_meme_altitude():
    from groma.gpkg import lire_points
    from groma.mnt import lire_geotiff

    for p in lire_points(EXEMPLE / "inventaire_video.gpkg", "placettes"):
        # P001 is exactly on the corner (925 000, 6 700 000): it belongs to the tile north-east
        # of it, but a bilinear sample within half a pixel of a tile edge needs the
        # neighbouring tile. Sample 1 m north-east of each centre, inside one tile.
        assert dalles.nom_dalle(*dalles.dalle_du_point(p["x"], p["y"])) == "rasters/mnt_0925_6700.tif"
        x, y = p["x"] + 1.0, p["y"] + 1.0  # Lambert-93, (easting, northing)
        nom = dalles.nom_dalle(*dalles.dalle_du_point(x, y))
        assert nom == "rasters/mnt_0925_6700.tif"
        z_dalle = lire_geotiff(EXEMPLE / nom).echantillonner(x, y)[0]
        z_placette = lire_geotiff(EXEMPLE / "rasters" / f"{p['plot_id']}_mnt.tif").echantillonner(x, y)[0]
        assert abs(z_dalle - z_placette) < 0.01, p["plot_id"]


def test_exemple_dalles_couvrent_l_emprise_de_la_foret():
    gpd = pytest.importorskip("geopandas")
    import shapely

    ugf = gpd.read_file(EXEMPLE / "inventaire_video.gpkg", layer="ugf")
    emprise = shapely.union_all(ugf.geometry.values).buffer(_R["dalles"]["marge_foret_m"])
    attendues = {dalles.nom_dalle(*k) for k in dalles.dalles_du_rectangle(*emprise.bounds)}
    presentes = {f"rasters/{p.name}" for p in (EXEMPLE / "rasters").glob("mnt_*_*.tif")}
    assert attendues == presentes and len(presentes) == 4
    # Points along the edge of the extent (2 m inside) have a ground height in their tile.
    from groma.mnt import lire_geotiff

    bord = emprise.buffer(-2.0).exterior
    for d in np.linspace(0, bord.length, 40, endpoint=False):
        pt = bord.interpolate(d)
        r = lire_geotiff(EXEMPLE / dalles.nom_dalle(*dalles.dalle_du_point(pt.x, pt.y)))
        assert not math.isnan(r.echantillonner(pt.x, pt.y)[0]), (pt.x, pt.y)


def test_exemple_houppiers_hors_placette_sans_plot_id():
    gpd = pytest.importorskip("geopandas")

    h = gpd.read_file(EXEMPLE / "inventaire_video.gpkg", layer="houppiers")
    assert h["plot_id"].isna().sum() > 0
    assert h["plot_id"].notna().sum() > 0


def test_exemple_cvat_octets_et_nodata_hors_emprise():
    with rasterio.open(EXEMPLE / "rasters" / "cvat.tif") as src:
        v = src.read(1)
        assert src.dtypes[0] == "uint8" and src.nodata == 0
    assert (v == 0).any() and v.max() <= 255 and (v[v > 0] >= 1).all()
