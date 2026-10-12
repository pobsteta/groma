import math

import numpy as np
import pytest

from groma.cercle import ajuster_cercle_algebrique, ajuster_cercle_geometrique, ajuster_cercle_ransac


def arc(xc, yc, r, a0, a1, n=200, bruit=0.0, graine=0):
    rng = np.random.default_rng(graine)
    t = rng.uniform(a0, a1, n)
    return np.column_stack([xc + r * np.cos(t), yc + r * np.sin(t)]) + rng.normal(0, bruit, (n, 2))


def test_cercle_complet_exact():
    pts = arc(3, -2, 0.25, 0, 2 * math.pi)
    xc, yc, r = ajuster_cercle_algebrique(pts)
    assert (xc, yc, r) == pytest.approx((3, -2, 0.25), abs=1e-9)


def test_arc_court_geometrique_meilleur_que_algebrique():
    pts = arc(0, 0, 0.4, 0, math.radians(70), bruit=0.004, graine=3)
    _, _, r_alg = ajuster_cercle_algebrique(pts)
    _, _, r_geo = ajuster_cercle_geometrique(pts)
    assert abs(r_geo - 0.4) <= abs(r_alg - 0.4) + 1e-9
    assert r_geo == pytest.approx(0.4, abs=0.02)


def test_ransac_rejette_les_aberrants():
    pts = arc(925000.0, 6700000.0, 0.3, 0, math.pi, n=150, bruit=0.003)
    rng = np.random.default_rng(1)
    aberrants = np.column_stack([rng.uniform(924999, 925001, 60), rng.uniform(6699999, 6700001, 60)])
    c = ajuster_cercle_ransac(np.vstack([pts, aberrants]), seuil_m=0.01)
    assert c is not None
    assert c.dbh_cm == pytest.approx(60, abs=1.0)
    assert math.hypot(c.xc - 925000, c.yc - 6700000) < 0.01
    assert c.couverture_deg == pytest.approx(180, abs=15)


def test_ransac_trop_peu_de_points():
    assert ajuster_cercle_ransac(np.zeros((3, 2))) is None
