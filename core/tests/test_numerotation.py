import json
import math
from pathlib import Path

import pytest

from groma.numerotation import (
    ArbreANumeroter,
    ParametresNumerotation,
    est_masque,
    numeroter,
    offsets_geographiques,
)

DOSSIER = Path(__file__).resolve().parents[2] / "fixtures" / "numerotation"
CAS = sorted(DOSSIER.glob("*.json"))


def test_les_cas_communs_existent():
    assert len(CAS) >= 10


@pytest.mark.parametrize("chemin", CAS, ids=lambda p: p.stem)
def test_cas_commun(chemin: Path):
    cas = json.loads(chemin.read_text(encoding="utf-8"))
    p = cas["parametres"]
    params = ParametresNumerotation(
        rayon_m=p["rayon_m"],
        seuil_diam_cm=p["seuil_diam_cm"],
        unite_azimut=p["unite_azimut"],
        tolerance_deg=p["tolerance_deg"],
    )
    arbres = [ArbreANumeroter(a["id"], a["x"], a["y"], a["dbh_cm"]) for a in cas["arbres"]]
    res = numeroter(arbres, params)
    attendu = cas["attendu"]
    assert res.numeros == attendu["numeros"]
    assert res.exclus == attendu["exclus"]
    if "visibles" in attendu:
        assert res.visibles == set(attendu["visibles"])
    for id_, az in attendu.get("azimuts", {}).items():
        assert res.azimuts[id_] == pytest.approx(az, abs=1e-4)


def test_identifiants_en_double():
    with pytest.raises(ValueError):
        numeroter([ArbreANumeroter("A", 1, 1, 20), ArbreANumeroter("A", 2, 2, 20)])


def test_masque_ignore_un_tronc_contenant_le_centre():
    gros = ArbreANumeroter("G", 0.1, 0.1, 80)
    cible = ArbreANumeroter("C", 0, 10, 20)
    assert not est_masque(cible, [gros, cible])


def test_rotation_convergence():
    # A grid vector pointing at grid azimuth 0 has geographic azimuth +gamma.
    x, y = offsets_geographiques(0.0, 10.0, 2.0)
    assert math.degrees(math.atan2(x, y)) == pytest.approx(2.0)
    assert math.hypot(x, y) == pytest.approx(10.0)
