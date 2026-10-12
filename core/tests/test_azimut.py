import pytest

from groma import azimut as az


def test_normaliser_et_ecart():
    assert az.normaliser_deg(-10) == pytest.approx(350)
    assert az.normaliser_deg(720) == 0
    assert az.ecart_deg(350, 10) == pytest.approx(-20)
    assert az.ecart_deg(10, 350) == pytest.approx(20)


def test_grades():
    assert az.vers_unite(90, "gr") == pytest.approx(100)
    assert az.depuis_unite(300, "gr") == pytest.approx(270)
    with pytest.raises(ValueError):
        az.vers_unite(10, "mil")


def test_chaine_magnetique_grille():
    # Compass 100°, declination +2° (east), convergence +2.18° at 6°E:
    # geographic 102°, grid 99.82°.
    geo = az.magnetique_vers_geographique(100, 2)
    assert geo == pytest.approx(102)
    assert az.geographique_vers_grille(geo, az.convergence_lambert93_deg(6.0)) == pytest.approx(102 - 2.17682, abs=1e-4)


def test_convergence_lambert93_contre_pyproj():
    pyproj = pytest.importorskip("pyproj")
    f = pyproj.Proj(2154).get_factors(6.5, 47.2)
    assert az.convergence_lambert93_deg(6.5) == pytest.approx(f.meridian_convergence, abs=1e-6)


def test_distance_horizontale():
    assert az.distance_horizontale(10, 6) == pytest.approx(8)
    with pytest.raises(ValueError):
        az.distance_horizontale(5, 6)
