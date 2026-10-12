import numpy as np
import pytest

from groma.mnt import Raster, controler_calage_vertical, decouper, pente_exposition, recouvrement_pct
from groma.synthetique import mnh_synthetique, mnt_synthetique, placette_synthetique


def test_echantillonnage_bilineaire_plan_exact():
    p = placette_synthetique()
    mnt = mnt_synthetique(p)
    x = p.centre[0] + np.array([0.0, 3.3, -7.25])
    y = p.centre[1] + np.array([0.0, -2.1, 9.9])
    assert mnt.echantillonner(x, y) == pytest.approx(p.z_sol(x, y), abs=1e-9)
    assert np.isnan(mnt.echantillonner(p.centre[0] + 100, p.centre[1]))[0]


def test_pente_exposition():
    p = placette_synthetique()  # dz/dx = 0.12, dz/dy = -0.03: descent towards az 284° (W)
    pente, expo = pente_exposition(mnt_synthetique(p), *p.centre, 20)
    assert pente == pytest.approx(12.4, abs=0.1)
    assert expo == "W"
    plat = Raster(np.full((40, 40), 300.0), 0, 40, 1.0)
    assert pente_exposition(plat, 20, 20, 10) == (0.0, "flat")


def test_exposition_tient_compte_de_la_convergence():
    # Descent due north in grid coordinates, convergence -30°: geographic NW.
    gx, gy = np.meshgrid(np.arange(40) + 0.5, 40 - (np.arange(40) + 0.5))
    r = Raster(500 - 0.2 * gy, 0, 40, 1.0, None)
    assert pente_exposition(r, 20, 20, 10)[1] == "N"
    assert pente_exposition(r, 20, 20, 10, convergence_deg=-30)[1] == "NW"


def test_recouvrement_et_decoupe():
    p = placette_synthetique()
    mnh = mnh_synthetique(p)
    rec = recouvrement_pct(mnh, *p.centre, 20)
    assert 0 < rec < 100
    d = decouper(mnh, *p.centre, 10)
    assert d.forme == (40, 40)
    assert recouvrement_pct(d, *p.centre, 10) == pytest.approx(recouvrement_pct(mnh, *p.centre, 10))


def test_calage_vertical():
    p = placette_synthetique()
    mnt = mnt_synthetique(p)
    rng = np.random.default_rng(0)
    x = p.centre[0] + rng.uniform(-15, 15, 500)
    y = p.centre[1] + rng.uniform(-15, 15, 500)
    z = p.z_sol(x, y) + 0.05 + rng.normal(0, 0.03, 500)
    c = controler_calage_vertical(np.column_stack([x, y, z]), mnt)
    assert c.conforme and c.decalage_median_m == pytest.approx(0.05, abs=0.01)
    c2 = controler_calage_vertical(np.column_stack([x, y, z + 1.0]), mnt)
    assert not c2.conforme
