"""Real example ``exemples/marsannay_p067/`` (mission 044) and the functions of its script.

Coordinates are Lambert-93 (EPSG:2154), always written (x = easting, y = northing).
Committed-example tests read only the repository; the comparison with the
original LiDAR source runs only on a machine that has it.
"""

from __future__ import annotations

import importlib.util
import json
import math
import sqlite3
import zipfile
from pathlib import Path

import numpy as np
import pytest

RACINE = Path(__file__).resolve().parents[2]
rasterio = pytest.importorskip("rasterio")
gpd = pytest.importorskip("geopandas")
shapely = pytest.importorskip("shapely")
from rasterio.transform import from_origin  # noqa: E402
from shapely.geometry import Point, box  # noqa: E402

from groma.dalles import verifier_projet  # noqa: E402
from groma.gpkg import decoder_point  # noqa: E402
from groma.mnt import Raster, lire_geotiff  # noqa: E402

EXEMPLE = RACINE / "exemples" / "marsannay_p067"
GPKG = EXEMPLE / "inventaire_video.gpkg"
PAQUET = EXEMPLE / "paquets" / "P067.groma.zip"
# Measured C0 of P067 on 2026-11-20 (phone GNSS), Lambert-93 (x, y) in metres.
C0_X, C0_Y = 849_659.22, 6_689_237.97
RAYON_M = 15.0


def _script():
    spec = importlib.util.spec_from_file_location("creer_marsannay_p067", RACINE / "exemples" / "creer_marsannay_p067.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


S = _script()


def _ro(chemin: Path) -> sqlite3.Connection:
    return sqlite3.connect(f"file:{chemin}?mode=ro", uri=True)


# ---- committed example ---------------------------------------------------------------


def test_exemple_conforme_a_verifier_projet():
    assert (EXEMPLE / "rasters" / "mnt_0849_6689.tif").is_file()
    assert verifier_projet(EXEMPLE) == []


def test_p067_centree_sur_le_c0_mesure():
    with _ro(GPKG) as cx:
        (pl,) = cx.execute("SELECT geom FROM placettes WHERE plot_id = 'P067'").fetchall()
        (ce,) = cx.execute("SELECT geom FROM centres_mesures WHERE plot_id = 'P067'").fetchall()
        rayon = cx.execute("SELECT rayon_m FROM placettes WHERE plot_id = 'P067'").fetchone()[0]
    x, y = decoder_point(pl[0])
    # Order (x, y) and system EPSG:2154 explicit: a swap or a wrong system lands kilometres away.
    assert x == pytest.approx(C0_X, abs=0.01) and y == pytest.approx(C0_Y, abs=0.01)
    assert decoder_point(ce[0]) == pytest.approx((x, y), abs=1e-6)
    with zipfile.ZipFile(PAQUET) as z:
        m = json.loads(z.read("manifest.json"))
    assert (m["centre"]["x"], m["centre"]["y"]) == pytest.approx((x, y), abs=1e-6)
    assert rayon == RAYON_M
    assert gpd.read_file(GPKG, layer="placettes").crs.to_epsg() == 2154


def test_cibles_du_paquet_placees_comme_par_le_telephone():
    """C1 to C3 recomputed from the package; the phone wrote them (real project,
    2026-11-20) at these Lambert-93 points: same place within 1 cm."""
    du_telephone = {"C1": (849_659.4395, 6_689_245.3689), "C2": (849_668.8514, 6_689_234.6049),
                    "C3": (849_651.8171, 6_689_233.6400)}
    c = gpd.read_file(GPKG, layer="cibles")
    assert sorted(c["id_cible"]) == ["C1", "C2", "C3"]
    for _, r in c.iterrows():
        assert (r.geometry.x, r.geometry.y) == pytest.approx(du_telephone[r["id_cible"]], abs=0.01)


def test_chaque_sommet_dans_son_houppier():
    h = gpd.read_file(GPKG, layer="houppiers")
    assert len(h) > 20
    assert h["houppier_id"].is_unique
    assert all(g.contains(Point(x, y)) for g, x, y in zip(h.geometry, h["x_sommet"], h["y_sommet"]))
    disque = Point(C0_X, C0_Y).buffer(RAYON_M, 64)
    dans = h.geometry.intersects(disque)
    assert (h.loc[dans, "plot_id"] == "P067").all() and h.loc[~dans, "plot_id"].isna().all()
    assert dans.sum() >= 5  # the plot is under a closed canopy (cover > 80 %)


def test_la_dalle_couvre_le_disque_de_la_placette():
    d = lire_geotiff(EXEMPLE / "rasters" / "mnt_0849_6689.tif")
    assert (d.x0, d.y0, d.res) == (849_000.0, 6_690_000.0, 0.5)
    gx, gy = d.centres()
    disque = np.hypot(gx - C0_X, gy - C0_Y) <= RAYON_M + 1.0
    assert disque.sum() > 2800
    assert np.isfinite(d.valeurs[disque]).all()
    # nodata far from the extent (the file stays small)
    assert np.isnan(d.valeurs[0, 0]) and np.isnan(d.valeurs[-1, -1])


def test_dalle_egale_au_bilineaire_du_mnt_a_1_m():
    """Every tile pixel = bilinear of the 1 m LiDAR DTM at its centre, within the 1/128 m rounding."""
    dalle = lire_geotiff(EXEMPLE / "rasters" / "mnt_0849_6689.tif")
    mnt = lire_geotiff(EXEMPLE / "controle" / "mnt_1m.tif")
    gx, gy = dalle.centres()
    f = np.isfinite(dalle.valeurs)
    assert f.sum() > 150_000
    assert np.abs(mnt.echantillonner(gx[f], gy[f]) - dalle.valeurs[f]).max() <= 1 / 256 + 1e-6


def test_dalle_proche_du_mnt_a_1_m_aux_noeuds_communs():
    """Each 1 m DTM node (pixel centre) is the corner shared by four 0.5 m pixels;
    the tile read there differs from the LiDAR value by 1/8 of the local second
    difference (bilinear smoothing). Measured on 2026-11-21: median 0.9 cm,
    95th percentile 4.0 cm, max 23 cm on a slope break 90 m east of the C0 (3 % of
    the nodes above 5 cm); inside the plot, max 5.6 cm. The fiche asked < 5 cm at
    every node: not met at slope breaks, raised to Pascal."""
    dalle = lire_geotiff(EXEMPLE / "rasters" / "mnt_0849_6689.tif")
    mnt = lire_geotiff(EXEMPLE / "controle" / "mnt_1m.tif")
    gx, gy = mnt.centres()
    z = dalle.echantillonner(gx.ravel(), gy.ravel()).reshape(gx.shape)
    ok = np.isfinite(z) & np.isfinite(mnt.valeurs)
    assert ok.sum() > 30_000
    e = np.abs(z - mnt.valeurs)[ok]
    assert np.median(e) < 0.01
    assert np.percentile(e, 95) < 0.05
    assert e.max() < 0.25
    disque = ok & (np.hypot(gx - C0_X, gy - C0_Y) <= RAYON_M)
    assert np.percentile(np.abs(z - mnt.valeurs)[disque], 99) < 0.05


def test_rasters_de_placette_autour_du_c0():
    for nom in ("P067_mnt.tif", "P067_mnh.tif"):
        with rasterio.open(EXEMPLE / "rasters" / nom) as src:
            assert src.crs.to_epsg() == 2154 and src.res == (0.5, 0.5)
            b = src.bounds
            assert b.left <= C0_X - 25 and b.right >= C0_X + 25 and b.bottom <= C0_Y - 25 and b.top >= C0_Y + 25
    mnt = lire_geotiff(EXEMPLE / "rasters" / "P067_mnt.tif")
    dalle = lire_geotiff(EXEMPLE / "rasters" / "mnt_0849_6689.tif")
    gx, gy = mnt.centres()
    assert np.nanmax(np.abs(dalle.echantillonner(gx.ravel(), gy.ravel()) - mnt.valeurs.ravel())) < 0.01


def test_paquet_reduit_lisible_et_leger():
    taille = PAQUET.stat().st_size
    assert taille < 5_000_000
    with zipfile.ZipFile(PAQUET) as z:
        m = json.loads(z.read("manifest.json"))
        noms = set(z.namelist())
    assert m["format"] == "groma-paquet" and m["plot_id"] == "P067" and m["projet_id"] == "exemple_marsannay_p067"
    assert len(m["images"]) == 525  # every frame kept (poses for the target triangulation)
    avec_image = [i for i in m["images"] if i["image"]]
    assert 0 < len(avec_image) <= math.ceil(len(m["images"]) / 10)
    for i in m["images"]:
        for k in ("image", "profondeur", "confiance"):
            assert i.get(k) is None or i[k] in noms
    assert {c["id_cible"] for c in m["cibles_mesurees"]} == {"C1", "C2", "C3"}
    assert m["detections_cibles"]


def test_exemple_sous_six_mo():
    taille = sum(p.stat().st_size for p in EXEMPLE.rglob("*") if p.is_file())
    assert taille < 6_000_000


def test_projet_distinct_du_projet_reel():
    with _ro(GPKG) as cx:
        (pid,) = cx.execute("SELECT projet_id FROM projet").fetchone()
    assert pid == "exemple_marsannay_p067"  # importing the example never overwrites 20261015_194200_yzoh


@pytest.mark.skipif(not S.MNT_DEFAUT.is_file(), reason="MNT LiDAR HD de nemeton absent de cette machine")
def test_controle_identique_a_la_source_lidar():
    c = lire_geotiff(EXEMPLE / "controle" / "mnt_1m.tif")
    lignes, colonnes = c.forme
    s = S.lire_fenetre(S.MNT_DEFAUT, c.x0, c.y0 - lignes * c.res, c.x0 + colonnes * c.res, c.y0)
    assert (s.x0, s.y0) == (c.x0, c.y0)
    np.testing.assert_array_equal(s.valeurs, c.valeurs)


# ---- script functions (tmp_path) -----------------------------------------------------


def _tif(chemin: Path, v: np.ndarray, x0: float, y0: float, res: float, dtype: str, nodata, crs="EPSG:2154"):
    with rasterio.open(chemin, "w", driver="GTiff", width=v.shape[1], height=v.shape[0], count=1, dtype=dtype,
                       crs=crs, transform=from_origin(x0, y0, res, res), nodata=nodata) as dst:
        dst.write(v.astype(dtype), 1)
    return chemin


def test_dalle_exacte_sur_un_plan(tmp_path):
    """Bilinear resampling is exact on a plane: tile values = plane within the 1/128 m rounding."""
    x0, y0 = 849_500.0, 6_689_400.0
    n = 300
    gx, gy = np.meshgrid(x0 + np.arange(n) + 0.5, y0 - np.arange(n) - 0.5)
    plan = lambda x, y: 300.0 + 0.4 * (x - 849_500) - 0.2 * (y - 6_689_000)  # noqa: E731
    src = Raster(plan(gx, gy), x0, y0, 1.0, None)
    emprise = Point(849_650, 6_689_250).buffer(60)
    (chemin,) = S.ecrire_dalle(src, emprise, tmp_path)
    assert chemin.relative_to(tmp_path).as_posix() == "rasters/mnt_0849_6689.tif"
    d = lire_geotiff(chemin)
    dx, dy = d.centres()
    dedans = shapely.contains_xy(emprise, dx, dy)
    assert np.isfinite(d.valeurs[dedans]).all() and np.isnan(d.valeurs[~dedans]).all()
    assert np.abs(d.valeurs[dedans] - plan(dx[dedans], dy[dedans])).max() <= 1 / 256 + 1e-6
    assert verifier_projet(tmp_path) == []


def test_segmentation_trouve_deux_cones_et_leurs_sommets():
    res, x0, y0 = 1.0, 1000.0, 2000.0
    gx, gy = np.meshgrid(x0 + np.arange(60) + 0.5, y0 - np.arange(40) - 0.5)
    sommets = [(1015.5, 1979.5, 25.0), (1040.5, 1979.5, 18.0)]  # pixel centres, x then y
    h = np.zeros(gx.shape)
    for sx, sy, hm in sommets:
        h = np.maximum(h, hm - 2.5 * np.hypot(gx - sx, gy - sy))
    hs = S.segmenter_houppiers(Raster(h, x0, y0, res, None), box(x0, y0 - 40, x0 + 60, y0))
    assert len(hs) == 2
    for (sx, sy, hm), c in zip(sommets, sorted(hs, key=lambda c: c["x_sommet"])):
        assert (c["x_sommet"], c["y_sommet"]) == (sx, sy)
        assert c["h_max"] == hm
        assert c["geometry"].contains(Point(sx, sy))
        assert c["surface_m2"] == pytest.approx(c["geometry"].area, abs=0.05)
    assert [c["houppier_id"] for c in hs] == [1, 2]


def test_segmentation_hors_emprise_vide():
    gx, gy = np.meshgrid(1000 + np.arange(20) + 0.5, 2000 - np.arange(20) - 0.5)
    h = 20 - 2 * np.hypot(gx - 1010.5, gy - 1989.5)
    assert S.segmenter_houppiers(Raster(h, 1000, 2000, 1.0, None), box(0, 0, 10, 10)) == []


def test_cvat_decoupe_sur_l_emprise(tmp_path):
    """--cvat: a small Byte raster at 2 m is clipped to the extent, values kept, 0 outside."""
    x0, y0 = 849_500.0, 6_689_400.0
    v = (np.arange(150 * 150).reshape(150, 150) % 254 + 1).astype("uint8")
    src = _tif(tmp_path / "cvat_nemetonshiny.tif", v, x0, y0, 2.0, "uint8", 0)
    emprise = Point(849_650, 6_689_250).buffer(60)
    (tmp_path / "projet" / "rasters").mkdir(parents=True)
    dest = S.decouper_cvat(src, emprise, tmp_path / "projet" / "rasters" / "cvat.tif")
    assert verifier_projet(tmp_path / "projet") == []
    with rasterio.open(dest) as d:
        o = d.read(1)
        t = d.transform
        assert d.dtypes[0] == "uint8" and d.nodata == 0 and d.crs.to_epsg() == 2154
    gx, gy = S.grille(t.c, t.f, 2.0, o.shape[1], o.shape[0])
    dedans = shapely.contains_xy(emprise, gx, gy)
    c0, l0 = int(round((t.c - x0) / 2)), int(round((y0 - t.f) / 2))
    np.testing.assert_array_equal(o[dedans], v[l0:l0 + o.shape[0], c0:c0 + o.shape[1]][dedans])
    assert (o[~dedans] == 0).all()
    assert t.c <= 849_590 and t.c + 2 * o.shape[1] >= 849_710


@pytest.mark.parametrize("dtype,res,nodata", [("float32", 2.0, 0), ("uint8", 1.0, 0), ("uint8", 2.0, 255)])
def test_cvat_hors_format_refuse(tmp_path, dtype, res, nodata):
    src = _tif(tmp_path / "c.tif", np.ones((50, 50)), 849_600.0, 6_689_300.0, res, dtype, nodata)
    with pytest.raises(ValueError):
        S.decouper_cvat(src, Point(849_630, 6_689_270).buffer(10), tmp_path / "cvat.tif")


def test_paquet_allege_garde_tout_le_manifeste(tmp_path):
    images = [{"id": i, "t": float(i), "pose": [0.0] * 16, "image": f"images/{i:06d}.jpg",
               "profondeur": f"profondeurs/{i:06d}.u16", "confiance": f"confiances/{i:06d}.u8"} for i in range(30)]
    m = {"format": "groma-paquet", "version": 1, "projet_id": "reel", "plot_id": "P067",
         "centre": {"x": C0_X, "y": C0_Y}, "images": images, "detections_cibles": [{"image": 7, "id_cible": "C1", "u": 1, "v": 2}]}
    src = tmp_path / "P067.groma.zip"
    with zipfile.ZipFile(src, "w") as z:
        z.writestr("manifest.json", json.dumps(m))
        for i in images:
            for k in ("image", "profondeur", "confiance"):
                z.writestr(i[k], b"x")
    dest = tmp_path / "out" / "P067.groma.zip"
    neuf = S.alleger_paquet(src, dest, pas=10, projet_id="exemple")
    with zipfile.ZipFile(dest) as z:
        lu = json.loads(z.read("manifest.json"))
        noms = set(z.namelist())
    assert lu == neuf and lu["projet_id"] == "exemple" and lu["centre"] == m["centre"]
    assert lu["detections_cibles"] == m["detections_cibles"]
    assert [i["pose"] for i in lu["images"]] == [i["pose"] for i in images]
    assert [i["id"] for i in lu["images"] if i["image"]] == [0, 10, 20]
    assert noms == {"manifest.json"} | {f"{d}/{i:06d}.{e}" for i in (0, 10, 20)
                                        for d, e in (("images", "jpg"), ("profondeurs", "u16"), ("confiances", "u8"))}
    with pytest.raises(ValueError):
        S.alleger_paquet(src, tmp_path / "x.zip", pas=5)


def test_position_cible_azimut_magnetique_vers_grille():
    # Due magnetic north, declination +2°, convergence +2°: grid north, 10 m north of C0.
    x, y = S.position_cible(C0_X, C0_Y, 0.0, 10.0, 2.0, 2.0)
    assert (x, y) == pytest.approx((C0_X, C0_Y + 10.0), abs=1e-9)
    # Magnetic east, no declination nor convergence: 10 m east.
    assert S.position_cible(C0_X, C0_Y, 90.0, 10.0, 0.0, 0.0) == pytest.approx((C0_X + 10.0, C0_Y), abs=1e-9)
