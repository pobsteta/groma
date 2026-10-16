import datetime as dt
import json
from pathlib import Path

import pytest

from groma.declinaison import annee_decimale, declinaison_deg

FIXTURE = json.loads((Path(__file__).parents[2] / "fixtures" / "declinaison" / "wmm2025.json").read_text("utf-8"))
TOL = FIXTURE["tolerance_deg"]


@pytest.mark.parametrize("pt", FIXTURE["points"] + FIXTURE["france"],
                         ids=lambda p: p.get("nom") or f"{p['annee']}_{p['lat']}_{p['lon']}")
def test_valeurs_officielles(pt):
    d = declinaison_deg(pt["lat"], pt["lon"], pt["annee"], pt["altitude_km"] * 1000)
    assert d == pytest.approx(pt["declinaison_deg"], abs=TOL)


def test_annee_decimale():
    assert annee_decimale(dt.date(2028, 7, 2)) == 2028.5  # 2028 is a leap year
    assert annee_decimale(dt.date(2026, 1, 1)) == 2026.0
    assert annee_decimale(dt.datetime(2026, 1, 1, 18)) == 2026.0


def test_hors_validite():
    with pytest.raises(ValueError, match="validité"):
        declinaison_deg(47.0, 5.0, 2031.0)
    with pytest.raises(ValueError, match="validité"):
        declinaison_deg(47.0, 5.0, dt.date(2024, 12, 31))
