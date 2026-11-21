"""Acceptance tests of mission 039 on the server side (written by the tester, mission 039a).

The expected values come from the 039/039a sheets and from independent reads of the source data
(rasterio on the 1 km tiles, sqlite3 on the example GeoPackage); nothing is copied from the code
of 039.

Tests 11 read files written by the Kotlin tests (``LidarPaquetTest``), in
``android/groma-core/build/lidar-039a/`` (not committed). Without them (gradle not run yet) these
tests are skipped with an explicit message: run ``gradle :groma-core:test`` first.
"""

from __future__ import annotations

import json
import sqlite3
import zipfile
from pathlib import Path

import numpy as np
import pytest

from groma_server.chaine import ConfigChaine, _charger_lidar, traiter_paquet
from groma_server.paquet import Centre, Lidar, Manifeste, Paquet, ecrire_paquet

RACINE = Path(__file__).resolve().parents[2]
EXEMPLE = RACINE / "exemples" / "inventaire_video"
KOTLIN = RACINE / "android" / "groma-core" / "build" / "lidar-039a"


def _kotlin(nom: str) -> Path:
    p = KOTLIN / nom
    if not p.exists():
        pytest.skip(f"{p} absent : lancer d'abord `gradle :groma-core:test --tests 'groma.core.LidarPaquetTest'` (rouge tant que 039 n'existe pas)")
    return p


def _paquet_du_kotlin(tmp_path: Path) -> tuple[Path, float, float]:
    cx, cy = (float(v) for v in _kotlin("centre.txt").read_text().split())
    man_lidar = json.loads(_kotlin("manifeste_lidar.json").read_text())
    fichiers = {n: (KOTLIN / n).read_bytes() for n in ("lidar/mnt.tif", "lidar/houppiers.geojson", "lidar/cvat.tif", "lidar/mnh.tif") if (KOTLIN / n).exists()}
    m = Manifeste(plot_id="P001", centre=Centre(x=cx, y=cy), images=[], lidar=Lidar.model_validate(man_lidar))
    return ecrire_paquet(tmp_path / "P001.groma.zip", m, fichiers), cx, cy


# ---- 11. paquet dont lidar/ vient du Kotlin ---------------------------------------------------


def test_paquet_du_kotlin_relu_par_ouvrir_et_charger_lidar(tmp_path):
    chemin, cx, cy = _paquet_du_kotlin(tmp_path)
    p = Paquet.ouvrir(chemin)
    try:
        l = p.manifeste.lidar
        assert l.mnt == "lidar/mnt.tif" and p.existe(l.mnt)
        assert l.houppiers == "lidar/houppiers.geojson" and p.existe(l.houppiers)
        assert l.remarque in (None, "")
        mnt, mnh, houppiers = _charger_lidar(p, tmp_path / "x")
        assert mnt is not None and mnh is None
        assert mnt.valeurs.shape == (200, 200) and mnt.res == pytest.approx(0.5)
        assert mnt.x0 + 100 == pytest.approx(cx, abs=0.25 + 1e-9) and mnt.y0 - 100 == pytest.approx(cy, abs=0.25 + 1e-9)
        # Independent expectation: crowns whose top is in the square, plot_id NULL included (sqlite BETWEEN).
        c = sqlite3.connect(f"file:{EXEMPLE / 'inventaire_video.gpkg'}?mode=ro", uri=True)
        attendus = sorted(r[0] for r in c.execute(
            "SELECT houppier_id FROM houppiers WHERE x_sommet BETWEEN ? AND ? AND y_sommet BETWEEN ? AND ?",
            (cx - 50, cx + 50, cy - 50, cy + 50)))
        assert len(attendus) == 3
        assert sorted(h.houppier_id for h in houppiers) == attendus
        # Coordinates are east, north: every ring lies within the forest extent.
        for h in houppiers:
            x0, y0, x1, y1 = h.polygone.bounds
            assert 924800 < x0 < x1 < 925400 and 6699800 < y0 < y1 < 6700300
    finally:
        p.fermer()


def test_rasterio_relit_le_mnt_du_kotlin_comme_la_fenetre_des_dalles(tmp_path):
    import rasterio
    from rasterio.merge import merge

    mnt = _kotlin("lidar/mnt.tif")
    with rasterio.open(mnt) as s:
        assert (s.width, s.height, s.count) == (200, 200, 1)
        assert s.dtypes[0] == "float32" and s.nodata == -9999.0 and s.crs.to_epsg() == 2154
        assert s.res == (0.5, 0.5)
        assert s.compression.name.upper() == "DEFLATE"
        assert s.tags(ns="IMAGE_STRUCTURE").get("PREDICTOR", "1") in ("1", "NONE")
        assert not s.profile.get("tiled")
        sortie = s.read(1)
        b = s.bounds
    tuiles = [rasterio.open(f) for f in sorted((EXEMPLE / "rasters").glob("mnt_*.tif"))]
    try:
        attendu, _ = merge(tuiles, bounds=tuple(b), nodata=-9999.0)
    finally:
        for t in tuiles:
            t.close()
    assert attendu[0].shape == sortie.shape
    assert not (sortie == -9999.0).any()
    np.testing.assert_array_equal(sortie, attendu[0])


def test_rasterio_relit_le_geotiff_ecrit_par_geotiff_ecrire():
    import rasterio

    with rasterio.open(_kotlin("geotiff_ecrire.tif")) as s:
        assert (s.width, s.height, s.count) == (7, 5, 1)
        assert s.dtypes[0] == "float32" and s.nodata == -9999.0 and s.crs.to_epsg() == 2154
        assert s.transform.c == pytest.approx(925000.5) and s.transform.f == pytest.approx(6700010.0)
        assert s.transform.a == pytest.approx(0.5) and s.transform.e == pytest.approx(-0.5)
        assert s.compression.name.upper() == "DEFLATE"
        a = s.read(1)
    attendu = np.fromfunction(lambda l, c: 400 + 0.25 * c + 0.5 * l, (5, 7)).astype("float32")
    attendu[1, 2] = attendu[4, 6] = -9999.0
    np.testing.assert_array_equal(a, attendu)


def test_cvat_du_kotlin_relu_par_rasterio():
    import rasterio

    chemin = KOTLIN / "lidar" / "cvat.tif"
    if not chemin.exists():
        pytest.skip("lidar/cvat.tif absent du paquet de référence")
    with rasterio.open(chemin) as s, rasterio.open(EXEMPLE / "rasters" / "cvat.tif") as src:
        assert (s.width, s.height) == (50, 50) and s.dtypes[0] == "uint8" and s.res == (2.0, 2.0)
        assert s.nodata == 0.0 and s.crs.to_epsg() == 2154
        w = src.window(*s.bounds)
        np.testing.assert_array_equal(s.read(1), src.read(1, window=w.round_offsets().round_lengths()))


# ---- 12. la remarque arrive dans les alertes du rapport ----------------------------------------


def _paquet_avec_remarque(paquet_synthetique, tmp_path, remarque):
    chemin, _ = paquet_synthetique
    p = Paquet.ouvrir(chemin)
    m = p.manifeste.model_copy(deep=True)
    fichiers = {n: p.lire(n) for n in p._zip.namelist() if n != "manifest.json"}
    p.fermer()
    m.lidar.remarque = remarque
    return ecrire_paquet(tmp_path / "remarque.groma.zip", m, fichiers)


def test_remarque_lidar_dans_les_alertes_du_rapport(paquet_synthetique, tmp_path):
    texte = "Ni dalle MNT ni raster de placette ne couvrent le centre relevé : sol reconstruit."
    chemin = _paquet_avec_remarque(paquet_synthetique, tmp_path, texte)
    r = traiter_paquet(chemin, tmp_path / "sortie", ConfigChaine(reconstructeurs=("profondeur",)))
    lignes = [a for a in r.rapport["alertes"] if a.startswith("LiDAR : ")]
    assert len(lignes) == 1 and texte in lignes[0]
    assert r.rapport["verdict"] == "a_verifier"
    assert "LiDAR : " in r.chemin_rapport_html.read_text() or texte in r.chemin_rapport_html.read_text()


def test_sans_remarque_aucune_alerte_lidar(paquet_synthetique, tmp_path):
    chemin, _ = paquet_synthetique
    r = traiter_paquet(chemin, tmp_path / "sortie", ConfigChaine(reconstructeurs=("profondeur",)))
    assert not any(a.startswith("LiDAR") for a in r.rapport["alertes"])


# ---- 13. anciens manifestes acceptés -------------------------------------------------------------


def _manifeste_json(**lidar) -> dict:
    d = {"format": "groma-paquet", "version": 1, "plot_id": "P001", "centre": {"x": 925000.0, "y": 6700000.0}, "images": []}
    d["lidar"] = lidar
    return d


def _ouvrir_manifeste(tmp_path, d: dict) -> Paquet:
    chemin = tmp_path / "m.groma.zip"
    with zipfile.ZipFile(chemin, "w") as z:
        z.writestr("manifest.json", json.dumps(d))
    return Paquet.ouvrir(chemin)


def test_manifeste_sans_cle_lidar_accepte(tmp_path):
    d = _manifeste_json()
    del d["lidar"]
    p = _ouvrir_manifeste(tmp_path, d)
    assert p.manifeste.lidar.mnt is None and p.manifeste.lidar.remarque is None
    assert _charger_lidar(p, tmp_path / "x") == (None, None, [])
    p.fermer()


def test_manifeste_lidar_sans_remarque_accepte(tmp_path):
    p = _ouvrir_manifeste(tmp_path, _manifeste_json(mnt="lidar/mnt.tif", houppiers="lidar/houppiers.geojson"))
    assert p.manifeste.lidar.mnt == "lidar/mnt.tif" and p.manifeste.lidar.remarque is None
    p.fermer()


def test_manifeste_lidar_avec_decoupe_et_remarque_accepte(tmp_path):
    p = _ouvrir_manifeste(tmp_path, _manifeste_json(
        mnt="lidar/mnt.tif", cvat="lidar/cvat.tif", decoupe={"x": 925150.0, "y": 6700000.0, "demi_cote_m": 50.0, "source_mnt": "dalles"},
        remarque="MNT partiel : 63 % de la placette"))
    assert p.manifeste.lidar.remarque == "MNT partiel : 63 % de la placette"
    p.fermer()


def test_remarque_sans_fichier_lidar_ne_casse_pas_la_lecture(tmp_path):
    p = _ouvrir_manifeste(tmp_path, _manifeste_json(remarque="Ni dalle MNT ni raster de placette ne couvrent le centre relevé : sol reconstruit."))
    assert p.manifeste.lidar.mnt is None
    assert p.manifeste.lidar.remarque.startswith("Ni dalle MNT")
    assert _charger_lidar(p, tmp_path / "x") == (None, None, [])
    p.fermer()
