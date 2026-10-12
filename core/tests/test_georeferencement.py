import math

import numpy as np
import pytest

from groma.georeferencement import CibleMesuree, georeferencer


def _scenario(rotation_vraie=37.0, echelle_vraie=1.0, decl=1.5, conv=2.0, unite="deg"):
    # True grid positions of targets around the RTK centre.
    centre = np.array([925000.0, 6700000.0])
    cibles = {"C1": (20.0, 8.0), "C2": (140.0, 12.0), "C3": (260.0, 6.5)}  # grid az, distance
    t = math.radians(rotation_vraie)
    locales, mesurees = {}, []
    c0_local = np.array([3.0, -1.0])
    for k, (az_g, d) in cibles.items():
        a = math.radians(az_g)
        dx, dy = d * math.sin(a), d * math.cos(a)
        # local = rotate counter-clockwise (azimuth -rotation) and unscale
        lx = (dx * math.cos(t) - dy * math.sin(t)) / echelle_vraie
        ly = (dx * math.sin(t) + dy * math.cos(t)) / echelle_vraie
        locales[k] = (c0_local[0] + lx, c0_local[1] + ly)
        az_mag = (az_g + conv - decl) % 360
        val = az_mag / 0.9 if unite == "gr" else az_mag
        mesurees.append(CibleMesuree(k, val, d))
    return centre, c0_local, locales, mesurees, cibles


@pytest.mark.parametrize("unite", ["deg", "gr"])
def test_rotation_retrouvee(unite):
    centre, c0, loc, mes, cibles = _scenario(unite=unite)
    g = georeferencer(c0, loc, mes, centre, unite=unite, declinaison_deg=1.5, convergence_deg=2.0)
    assert g.rotation_deg == pytest.approx(37.0, abs=1e-6)
    assert not g.echelle_appliquee
    assert g.rmse_azimut_deg < 1e-6
    for k, (az_g, d) in cibles.items():
        p = g.vers_grille(np.array([loc[k]]))[0]
        a = math.radians(az_g)
        assert p == pytest.approx([centre[0] + d * math.sin(a), centre[1] + d * math.cos(a)], abs=1e-6)


def test_echelle_corrigee_si_justifiee():
    centre, c0, loc, mes, _ = _scenario(echelle_vraie=1.04)
    g = georeferencer(c0, loc, mes, centre, declinaison_deg=1.5, convergence_deg=2.0)
    assert g.echelle_appliquee and g.echelle == pytest.approx(1.04, abs=1e-6)
    assert max(g.residus_position_m.values()) < 1e-6


def test_echelle_non_appliquee_si_incoherente():
    centre, c0, loc, mes, _ = _scenario()
    mes[0] = CibleMesuree("C1", mes[0].azimut_mesure, mes[0].distance_mesuree_m * 1.08)
    g = georeferencer(c0, loc, mes, centre, declinaison_deg=1.5, convergence_deg=2.0)
    assert not g.echelle_appliquee
    assert g.residus_distance_m["C1"] == pytest.approx(-0.64, abs=1e-6)


def test_sans_cible_commune():
    with pytest.raises(ValueError):
        georeferencer((0, 0), {"C1": (1, 1)}, [CibleMesuree("C2", 10, 5)], (0, 0))
