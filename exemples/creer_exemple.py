"""Build the example GeoPackage ``exemples/inventaire_video/``.

Two plots from the synthetic generator (same plan columns as
nemeton::create_sampling_plan()), clipped DTM and CHM per plot, crowns, the
RTK centre and the three targets, then:

* P001: provisional detections written with the phone protocol
  (``ecrire_detections``), and field attributes typed "in QField";
* P002: still to do.

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
from shapely.geometry import Point

from groma.azimut import convergence_lambert93_deg
from groma.gpkg import creer_gpkg, ecrire_detections, lire_points, mettre_a_jour_etat
from groma.mnt import decouper, ecrire_geotiff
from groma.placette import ParametresPlacette, construire_detections
from groma.synthetique import houppiers_synthetiques, mnh_synthetique, mnt_synthetique, placette_synthetique

ICI = Path(__file__).resolve().parent
SORTIE = ICI / "inventaire_video"
RESULTATS = ICI / "resultats"


def main() -> None:
    shutil.rmtree(SORTIE, ignore_errors=True)
    shutil.rmtree(RESULTATS, ignore_errors=True)
    (SORTIE / "rasters").mkdir(parents=True)
    RESULTATS.mkdir()

    placettes_syn = {
        "P001": placette_synthetique(centre=(925_000.0, 6_700_000.0), graine=42),
        "P002": placette_synthetique(centre=(925_180.0, 6_700_090.0), graine=7, gros_bois=False),
    }
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
        },
        geometry=[Point(p.centre) for p in placettes_syn.values()],
        crs=2154,
    )

    houppiers, centres, cibles = [], [], []
    for pid, p in placettes_syn.items():
        mnt, mnh = mnt_synthetique(p), mnh_synthetique(p)
        ecrire_geotiff(decouper(mnt, *p.centre, 30), SORTIE / "rasters" / f"{pid}_mnt.tif")
        ecrire_geotiff(decouper(mnh, *p.centre, 30), SORTIE / "rasters" / f"{pid}_mnh.tif")
        for h in houppiers_synthetiques(p):
            houppiers.append({"plot_id": pid, "houppier_id": h.houppier_id, "h_max": h.h_max,
                              "surface_m2": h.surface_m2, "geometry": h.polygone})
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
                           "distance_mesuree_m": round(d, 2), "dbh_mesure_cm": a.dbh_cm,
                           "geometry": Point(p.centre[0] + d * a.x / d_tronc, p.centre[1] + d * a.y / d_tronc)})

    gpkg = creer_gpkg(
        SORTIE / "inventaire_video.gpkg",
        plan,
        houppiers=gpd.GeoDataFrame(houppiers, crs=2154),
        cibles=gpd.GeoDataFrame(cibles, crs=2154),
        centres_mesures=gpd.GeoDataFrame(centres, crs=2154),
        projet={
            "projet_id": "exemple_synthetique",
            "projet_nom": "Forêt d'exemple (synthétique)",
            "description": "Deux placettes du générateur synthétique",
            "communes": "Exemple (00000)",
            "surface_zone_ha": 12.5,
            "nb_parcelles": 1,
            "nb_ug": 1,
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
