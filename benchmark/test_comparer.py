import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from comparer import comparer, lire_manuel  # noqa: E402


def test_comparaison_parfaite(tmp_path):
    csv = tmp_path / "m.csv"
    csv.write_text("tree_id,azimut,distance_m,dbh_cm,h_m\n1,90,4.85,30,20\n2,200,10,40,\n", encoding="utf-8")
    p = {"centre": [1000.0, 2000.0], "unite_azimut": "deg"}
    manuel = lire_manuel(csv, p)
    assert manuel[0]["x"] == 1005.0 and abs(manuel[0]["y"] - 2000.0) < 1e-9
    estime = [{"x": m["x"] + 0.1, "y": m["y"], "dbh_cm": m["dbh_cm"] + 1, "h_m": 21.0} for m in manuel]
    r = comparer(manuel, estime)
    assert r["taux_detection"] == 1.0 and r["rmse_dbh_cm"] == 1.0 and r["rmse_hauteur_m"] == 1.0
    assert r["objectifs"] == {"detection_95pct": True, "rmse_dbh_2cm": True, "position_50cm": True}
