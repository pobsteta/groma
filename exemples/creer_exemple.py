"""Build the example GeoPackage ``exemples/inventaire_video/``.

Two plots from the synthetic generator (same plan columns as
nemeton::create_sampling_plan()), clipped DTM and CHM per plot, the RTK
centre and the three targets, and the forest-wide data of schema 1.7.0:
crowns of the whole forest with their apex (``x_sommet``, ``y_sommet``; NULL
``plot_id`` outside the plots), the 1 km DTM tiles touching the forest extent
(union of the ``ugf`` plus ``rasters.dalles.marge_foret_m``: four tiles, the
extent crosses x = 925 km and y = 6 700 km) and a small ``cvat.tif``. Both
plots share one ground plane so that tiles and per-plot DTMs agree. Then:

* P001: provisional detections written with the phone protocol
  (``ecrire_detections``), and field attributes typed "in QField";
* P002: still to do;
* one cadastral parcel in two parts (the first with a hole) for the map,
  each part a forest management unit (``ugf``).

Also writes the definitive server result of P001
(``resultats/P001_definitif.gpkg``), noisier positions removed, used by the
integration test of the nemeton brief.

Run: python exemples/creer_exemple.py
"""

from __future__ import annotations

import math
import shutil
import sqlite3
from pathlib import Path

import geopandas as gpd
import numpy as np
import rasterio
import shapely
from rasterio.transform import from_origin
from shapely.geometry import MultiPolygon, Point, Polygon

from groma.azimut import convergence_lambert93_deg
from groma.dalles import NODATA_CVAT, dalles_du_rectangle, nom_dalle
from groma.gpkg import charger_schema, creer_gpkg, ecrire_detections, lire_points, mettre_a_jour_etat
from groma.mnt import Raster, decouper, ecrire_geotiff
from groma.placette import ParametresPlacette, construire_detections
from groma.synthetique import houppiers_synthetiques, mnh_synthetique, mnt_synthetique, placette_synthetique

ICI = Path(__file__).resolve().parent
SORTIE = ICI / "inventaire_video"
RESULTATS = ICI / "resultats"

# Management units of the example (each part of the cadastral parcel is one UGF).
UGF = [
    Polygon([(924900, 6699900), (925100, 6699900), (925100, 6700200), (924900, 6700200)]),
    Polygon([(925100, 6699950), (925300, 6699950), (925300, 6700200), (925100, 6700200)]),
]


def emprise_foret():
    """Forest extent (schema 1.7.0): union of the UGF plus ``marge_foret_m``."""
    return shapely.union_all(UGF).buffer(charger_schema()["rasters"]["dalles"]["marge_foret_m"])


def ecrire_dalles(sortie: Path, z_sol, emprise) -> list[str]:
    """1 km DTM tiles touching ``emprise``; ground from ``z_sol``, nodata outside the extent."""
    r = charger_schema()["rasters"]
    res, cote = r["resolution_m"], r["dalles"]["cote_m"]
    n = int(round(cote / res))
    noms = []
    for x_km, y_km in dalles_du_rectangle(*emprise.bounds):
        x0, y0 = x_km * 1000.0, y_km * 1000.0 + cote
        xs = x0 + (np.arange(n) + 0.5) * res
        ys = y0 - (np.arange(n) + 0.5) * res
        gx, gy = np.meshgrid(xs, ys)
        # Rounded to 1/128 m (error under 4 mm): the low mantissa bits become zero and
        # DEFLATE without predictor (the format of the schema) keeps the example small.
        z = np.where(shapely.contains_xy(emprise, gx, gy), np.round(z_sol(gx, gy) * 128) / 128, np.nan)
        nom = nom_dalle(x_km, y_km)
        ecrire_geotiff(Raster(z, x0, y0, res, None), sortie / nom)
        noms.append(nom)
    return noms


def ecrire_cvat(sortie: Path, emprise) -> None:
    """Synthetic CVAT relief on the extent: Byte, 1 + round(v * 254), 0 (nodata) outside."""
    c = charger_schema()["rasters"]["cvat"]
    res = c["resolution_m"]
    xmin, ymin, xmax, ymax = emprise.bounds
    x0, y0 = math.floor(xmin / res) * res, math.ceil(ymax / res) * res
    w, h = int(math.ceil((xmax - x0) / res)), int(math.ceil((y0 - ymin) / res))
    gx, gy = np.meshgrid(x0 + (np.arange(w) + 0.5) * res, y0 - (np.arange(h) + 0.5) * res)
    # Relief in [0, 1]: gentle ridges and hollows, deterministic.
    v = np.clip(0.5 + 0.3 * np.sin(gx / 37.0) * np.cos(gy / 23.0) + 0.15 * np.sin((gx + gy) / 61.0), 0.0, 1.0)
    octets = np.where(shapely.contains_xy(emprise, gx, gy), 1 + np.round(v * 254), NODATA_CVAT).astype("uint8")
    with rasterio.open(
        sortie / c["chemin"], "w", driver="GTiff", width=w, height=h, count=1, dtype="uint8", crs="EPSG:2154",
        transform=from_origin(x0, y0, res, res), nodata=NODATA_CVAT, compress="deflate",
    ) as dst:
        dst.write(octets, 1)


def houppiers_hors_placette(centres: list[tuple[float, float]]) -> list[dict]:
    """Crowns of the forest away from the plots (``plot_id`` NULL), on a jittered grid in the UGF."""
    rng = np.random.default_rng(11)
    foret = shapely.union_all(UGF)
    out, hid = [], 1001
    for x in range(924_930, 925_300, 60):
        for y in range(6_699_930, 6_700_200, 60):
            cx, cy = x + rng.uniform(-10, 10), y + rng.uniform(-10, 10)
            rayon = round(float(rng.uniform(2.5, 4.5)), 2)
            if not foret.contains(Point(cx, cy).buffer(rayon)):
                continue
            if any(math.hypot(cx - a, cy - b) < 45 for a, b in centres):
                continue  # the plots keep their own crowns
            poly = Point(cx, cy).buffer(rayon, 16)
            out.append({"plot_id": None, "houppier_id": hid, "h_max": round(float(rng.uniform(15, 30)), 1),
                        "surface_m2": round(poly.area, 1), "x_sommet": cx, "y_sommet": cy, "geometry": poly})
            hid += 1
    return out


def main() -> None:
    shutil.rmtree(SORTIE, ignore_errors=True)
    shutil.rmtree(RESULTATS, ignore_errors=True)
    (SORTIE / "rasters").mkdir(parents=True)
    RESULTATS.mkdir()

    placettes_syn = {
        "P001": placette_synthetique(centre=(925_000.0, 6_700_000.0), graine=42),
        "P002": placette_synthetique(centre=(925_180.0, 6_700_090.0), graine=7, gros_bois=False),
    }
    # One ground plane for the whole forest: P002 is raised onto the plane of P001
    # (same slopes), so the forest-wide tiles agree with both per-plot DTMs.
    p1, p2 = placettes_syn["P001"], placettes_syn["P002"]
    assert (p1.pente_x, p1.pente_y) == (p2.pente_x, p2.pente_y)
    p2.z0 = float(p1.z_sol(*p2.centre))
    plan = gpd.GeoDataFrame(
        {
            "plot_id": list(placettes_syn),
            "visit_order": [1, 2],
            "type": ["Base", "Base"],
            "wgt": [24.6, 24.6],
            "ip": [0.0407, 0.0407],
            "stratum": ["haute_feuillus", "haute_feuillus"],
            "strat_height": ["haute", "haute"],
            "strat_type": ["feuillus", "feuillus"],
            "strat_topo": ["pente", "pente"],
            "declinaison_deg": [2.4, 2.4],
            # Prepared before the 15 m default (mission 035): the example keeps its 20 m plots.
            "rayon_m": [20.0, 20.0],
        },
        geometry=[Point(p.centre) for p in placettes_syn.values()],
        crs=2154,
    )

    houppiers, centres, cibles = [], [], []
    for pid, p in placettes_syn.items():
        mnt, mnh = mnt_synthetique(p), mnh_synthetique(p)
        ecrire_geotiff(decouper(mnt, *p.centre, 30), SORTIE / "rasters" / f"{pid}_mnt.tif")
        ecrire_geotiff(decouper(mnh, *p.centre, 30), SORTIE / "rasters" / f"{pid}_mnh.tif")
        for h, a in zip(houppiers_synthetiques(p), p.arbres):
            # Apex = centre of the synthetic crown disc (top of the CHM cone).
            xs, ys = p.position(a)
            houppiers.append({"plot_id": pid, "houppier_id": h.houppier_id, "h_max": h.h_max,
                              "surface_m2": h.surface_m2, "x_sommet": xs + a.decalage_apex[0],
                              "y_sommet": ys + a.decalage_apex[1], "geometry": h.polygone})
        # RTK centre measured on the planned point (synthetic case).
        centres.append({"plot_id": pid, "qualite_gnss": "fix", "precision_h_m": 0.02, "ecart_prevu_m": 0.0,
                        "date_mesure": "2026-10-12T09:30:00", "geometry": Point(p.centre)})
        for id_cible, id_arbre in p.cibles.items():
            a = next(t for t in p.arbres if t.id == id_arbre)
            d_tronc = math.hypot(a.x, a.y)
            d = d_tronc - a.dbh_cm / 200.0  # target stapled on the bark, facing the centre
            az_grille = math.degrees(math.atan2(a.x, a.y)) % 360
            conv = convergence_lambert93_deg(float(plan.loc[plan.plot_id == pid, "geometry"].to_crs(4326).x.iloc[0]))
            az_mag = (az_grille + conv - 2.4) % 360
            cibles.append({"plot_id": pid, "id_cible": id_cible, "azimut_mesure": round(az_mag, 1),
                           "distance_mesuree_m": round(d, 2), "dbh_mesure_cm": a.dbh_cm, "source_azimut": "boussole_visee",
                           "geometry": Point(p.centre[0] + d * a.x / d_tronc, p.centre[1] + d * a.y / d_tronc)})

    houppiers += houppiers_hors_placette([p.centre for p in placettes_syn.values()])
    emprise = emprise_foret()
    ecrire_dalles(SORTIE, p1.z_sol, emprise)
    ecrire_cvat(SORTIE, emprise)

    gpkg = creer_gpkg(
        SORTIE / "inventaire_video.gpkg",
        plan,
        houppiers=gpd.GeoDataFrame(houppiers, crs=2154),
        cibles=gpd.GeoDataFrame(cibles, crs=2154),
        centres_mesures=gpd.GeoDataFrame(centres, crs=2154),
        # One parcel of two parts around the plots, the first with a hole (map of the phone).
        parcelles=gpd.GeoDataFrame(
            {"idu": ["000000000A0001"], "commune": ["00000"], "section": ["A"], "numero": ["1"], "contenance_m2": [125000.0]},
            geometry=[MultiPolygon([
                Polygon([(924900, 6699900), (925100, 6699900), (925100, 6700200), (924900, 6700200)],
                        [[(924950, 6700150), (924980, 6700150), (924980, 6700180), (924950, 6700180)]]),
                Polygon([(925100, 6699950), (925300, 6699950), (925300, 6700200), (925100, 6700200)]),
            ])],
            crs=2154,
        ),
        # Each part of the parcel is a management unit, ONF numbers 1 and 2.
        ugf=gpd.GeoDataFrame(
            {"ug_id": ["ug_001", "ug_002"], "libelle": ["Forêt d'exemple — parcelle 1", "Forêt d'exemple — parcelle 2"],
             "foret": ["Forêt d'exemple"] * 2, "parcelle_onf": ["1", "2"], "surface_m2": [60000.0, 50000.0]},
            geometry=UGF,
            crs=2154,
        ),
        projet={
            "projet_id": "exemple_synthetique",
            "projet_nom": "Forêt d'exemple (synthétique)",
            "description": "Deux placettes du générateur synthétique",
            "communes": "Exemple (00000)",
            "surface_zone_ha": 12.5,
            "nb_parcelles": 1,
            "nb_ug": 2,
            "plan_genere_le": "2026-10-12 08:00:00",
            "prepare_le": "2026-10-12 09:00:00",
            "prepare_par": "exemples/creer_exemple.py",
        },
    )

    # --- P001: provisional pass on the phone -------------------------------
    p = placettes_syn["P001"]
    ligne = next(l for l in lire_points(gpkg, "placettes") if l["plot_id"] == "P001")
    params = ParametresPlacette.depuis_ligne(ligne, p.centre)
    hs = houppiers_synthetiques(p)
    rng = np.random.default_rng(3)
    troncs_tel = [
        {"uuid": f"00000000-0000-4000-8000-{i:012d}", "x": p.centre[0] + a.x + rng.normal(0, 0.15),
         "y": p.centre[1] + a.y + rng.normal(0, 0.15), "dbh_cm": a.dbh_cm * rng.normal(1, 0.08), "score": 0.6}
        for i, a in enumerate(p.arbres)
        if a.id != "S05"  # missed by the phone
    ]
    det = construire_detections(troncs_tel, params, hs, methode="embarque", etat="provisoire")
    ecrire_detections(gpkg, det)
    mettre_a_jour_etat(gpkg, "P001", "provisoire")

    # --- "QField": species and notes typed on the field -------------------
    codes = ["hetre", "chene_sessile", "charme", "sapin_pectine", "douglas", "frene"]
    with sqlite3.connect(gpkg) as cx:
        uuids = [r[0] for r in cx.execute("SELECT uuid FROM arbres WHERE plot_id='P001' ORDER BY CAST(tree_id AS INTEGER)")]
        for k, u in enumerate(uuids[:-3]):  # the last three are left to complete
            cx.execute("UPDATE arbres SET essence_arbre=?, qualite=? WHERE uuid=?", (codes[k % len(codes)], "BCDA"[k % 4], u))
        cx.execute("UPDATE arbres SET notes='cible C1', h_m=26.5, h_source='mesuree' WHERE uuid=?", (uuids[0],))
        cx.execute("UPDATE placettes SET observateur='P. Obstetar', date_visite='2026-10-12T09:45:00' WHERE plot_id='P001'")

    # --- Server: definitive result of P001 ---------------------------------
    troncs_srv = [
        {"x": p.centre[0] + a.x + rng.normal(0, 0.03), "y": p.centre[1] + a.y + rng.normal(0, 0.03),
         "dbh_cm": a.dbh_cm + rng.normal(0, 1.0), "score": 0.9}
        for a in p.arbres
    ]
    det_srv = construire_detections(troncs_srv, params, hs, methode="classique", etat="definitif")
    gpd.GeoDataFrame(
        [{k: v for k, v in d.items() if k not in ("x", "y")} | {"geometry": Point(d["x"], d["y"])} for d in det_srv],
        crs=2154,
    ).rename_geometry("geom").to_file(RESULTATS / "P001_definitif.gpkg", layer="detections", driver="GPKG")
    print(f"Exemple écrit dans {SORTIE} et {RESULTATS}")


if __name__ == "__main__":
    main()
