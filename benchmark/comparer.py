"""Compare each method (embarque, classique, ia, houppiers) to the manual
measures of a test plot: detection rate, DBH RMSE vs tape, position error,
height error.

Inputs
------
* ``--manuel``: CSV of the manual inventory, one row per tree:
  ``tree_id,azimut,distance_m,dbh_cm,h_m`` (azimuth in the plot unit,
  magnetic, as read with the compass; horizontal distance; ``h_m`` optional);
* ``--placette``: plot parameters (JSON: centre x/y, unite_azimut,
  declinaison_deg, convergence_deg, rayon_m, seuil_diam_cm);
* one or more ``--methode nom=chemin.gpkg`` (``detections`` layer, or
  ``arbres`` for a provisional GeoPackage).

Output: a table on stdout and ``--sortie resultats.json``.

Example:
  python benchmark/comparer.py --manuel P001_manuel.csv --placette P001.json \
      --methode classique=P001_definitif.gpkg --methode embarque=inventaire_video.gpkg
"""

from __future__ import annotations

import argparse
import csv
import json
import math
from pathlib import Path

import numpy as np
from scipy.optimize import linear_sum_assignment

from groma.azimut import depuis_unite, geographique_vers_grille, magnetique_vers_geographique


def lire_manuel(chemin: Path, p: dict) -> list[dict]:
    out = []
    with open(chemin, newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            az = geographique_vers_grille(
                magnetique_vers_geographique(depuis_unite(float(r["azimut"]), p.get("unite_azimut", "deg")), p.get("declinaison_deg", 0.0)),
                p.get("convergence_deg", 0.0),
            )
            # The manual distance is measured to the bark facing the centre: add the radius.
            d = float(r["distance_m"]) + float(r["dbh_cm"]) / 200.0
            a = math.radians(az)
            out.append({
                "tree_id": r["tree_id"],
                "x": p["centre"][0] + d * math.sin(a),
                "y": p["centre"][1] + d * math.cos(a),
                "dbh_cm": float(r["dbh_cm"]),
                "h_m": float(r["h_m"]) if r.get("h_m") else None,
            })
    return out


def lire_methode(chemin: Path, plot_id: str | None) -> list[dict]:
    from groma.gpkg import lire_points

    import sqlite3

    with sqlite3.connect(chemin) as cx:
        couches = {r[0] for r in cx.execute("SELECT table_name FROM gpkg_contents")}
    couche = "detections" if "detections" in couches else "arbres"
    return [d for d in lire_points(chemin, couche, plot_id) if d.get("etat_detection") != "faux_positif"]


def comparer(manuel: list[dict], estime: list[dict], tolerance_m: float = 1.0) -> dict:
    if not manuel or not estime:
        return {"n_manuel": len(manuel), "n_estime": len(estime), "taux_detection": 0.0}
    cout = np.array([[math.hypot(m["x"] - e["x"], m["y"] - e["y"]) for e in estime] for m in manuel])
    i, j = linear_sum_assignment(np.where(cout <= tolerance_m, cout, 1e6))
    ok = cout[i, j] <= tolerance_m
    i, j = i[ok], j[ok]
    ddbh = np.array([estime[b]["dbh_cm"] - manuel[a]["dbh_cm"] for a, b in zip(i, j)])
    dh = np.array([estime[b]["h_m"] - manuel[a]["h_m"] for a, b in zip(i, j) if manuel[a]["h_m"] and estime[b].get("h_m")])
    return {
        "n_manuel": len(manuel),
        "n_estime": len(estime),
        "n_apparies": int(ok.sum()),
        "taux_detection": round(ok.sum() / len(manuel), 3),
        "faux_positifs": len(estime) - int(ok.sum()),
        "rmse_dbh_cm": round(float(np.sqrt(np.mean(ddbh**2))), 2) if len(ddbh) else None,
        "biais_dbh_cm": round(float(np.mean(ddbh)), 2) if len(ddbh) else None,
        "erreur_position_moyenne_m": round(float(cout[i, j].mean()), 3) if ok.any() else None,
        "erreur_position_max_m": round(float(cout[i, j].max()), 3) if ok.any() else None,
        "rmse_hauteur_m": round(float(np.sqrt(np.mean(dh**2))), 2) if len(dh) else None,
        "objectifs": {
            "detection_95pct": bool(ok.sum() / len(manuel) >= 0.95),
            "rmse_dbh_2cm": bool(len(ddbh) and np.sqrt(np.mean(ddbh**2)) <= 2.0),
            "position_50cm": bool(ok.any() and cout[i, j].max() <= 0.5),
        },
    }


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--manuel", required=True, type=Path)
    ap.add_argument("--placette", required=True, type=Path)
    ap.add_argument("--methode", action="append", default=[], help="nom=chemin.gpkg")
    ap.add_argument("--plot-id")
    ap.add_argument("--sortie", type=Path)
    a = ap.parse_args()
    p = json.loads(a.placette.read_text(encoding="utf-8"))
    manuel = lire_manuel(a.manuel, p)
    resultats = {}
    for m in a.methode:
        nom, chemin = m.split("=", 1)
        resultats[nom] = comparer(manuel, lire_methode(Path(chemin), a.plot_id))
    print(f"{'méthode':<12}{'détection':>10}{'RMSE dbh':>10}{'biais':>8}{'pos. max':>10}{'RMSE h':>8}")
    for nom, r in resultats.items():
        print(f"{nom:<12}{r['taux_detection']:>10}{str(r.get('rmse_dbh_cm')):>10}{str(r.get('biais_dbh_cm')):>8}"
              f"{str(r.get('erreur_position_max_m')):>10}{str(r.get('rmse_hauteur_m')):>8}")
    if a.sortie:
        a.sortie.write_text(json.dumps(resultats, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
