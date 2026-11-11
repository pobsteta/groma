"""Mission 017: arbres saisis dans Groma, diamètre au ruban prioritaire (déclencheurs).

Expected API (to be built by mission 018):
  * new column `arbres.dbh_source` (TEXT, domaine `dbh_source` = ["estime", "mesure"], défaut
    `estime`, origine groma), same model as `h_source`;
  * `trg_detections_update` never overwrites `dbh_cm` of a row with `dbh_source = 'mesure'`,
    nor the attributes entered in Groma (essence_arbre, statut, qualite);
  * `trg_detections_insert` creates the row with `dbh_source = 'estime'`.
Without a tape measure the behaviour is unchanged (geometry, number, estimate follow the phone).
"""
import sqlite3

import pytest

gpd = pytest.importorskip("geopandas")
pyogrio = pytest.importorskip("pyogrio")
from shapely.geometry import Point

from groma.gpkg import charger_schema, creer_gpkg, ecrire_detections, lire_points


@pytest.fixture
def gpkg(tmp_path):
    placettes = gpd.GeoDataFrame(
        {"plot_id": ["P01"], "visit_order": [1], "type": ["Base"], "wgt": [12.5], "ip": [0.08], "stratum": ["s1"]},
        geometry=[Point(925000, 6700000)],
        crs=2154,
    )
    return creer_gpkg(tmp_path / "inventaire_video.gpkg", placettes)


def _detection(uuid="a", dbh=30.0, num="1", x=925003.0):
    return {"uuid": uuid, "plot_id": "P01", "x": x, "y": 6700004.0, "num_provisoire": num, "dbh_cm": dbh,
            "azimut": 36.87, "distance_m": 5.0, "etat_detection": "provisoire", "methode": "embarque"}


def _arbres(gpkg):
    return {a["uuid"]: a for a in lire_points(gpkg, "arbres")}


def test_schema_dbh_source():
    s = charger_schema()
    assert s["domaines"]["dbh_source"] == ["estime", "mesure"]
    arbres = next(c for c in s["couches"] if c["nom"] == "arbres")
    col = next(c for c in arbres["colonnes"] if c["nom"] == "dbh_source")
    assert col["domaine"] == "dbh_source" and col["defaut"] == "estime" and col["origine"] == "groma"


def test_dbh_source_estime_a_la_creation(gpkg):
    ecrire_detections(gpkg, [_detection()])
    a = _arbres(gpkg)["a"]
    assert a["dbh_source"] == "estime" and a["dbh_cm"] == 30.0


def test_detection_met_a_jour_un_diametre_estime(gpkg):
    ecrire_detections(gpkg, [_detection()])
    ecrire_detections(gpkg, [_detection(dbh=33.0)])
    a = _arbres(gpkg)["a"]
    assert a["dbh_cm"] == 33.0 and a["dbh_source"] == "estime"


def test_diametre_mesure_jamais_ecrase_par_une_detection(gpkg):
    ecrire_detections(gpkg, [_detection()])
    with sqlite3.connect(gpkg) as cx:
        cx.execute("UPDATE arbres SET dbh_cm=42.5, dbh_source='mesure', essence_arbre='hetre', statut='mort', qualite='B' WHERE uuid='a'")
    ecrire_detections(gpkg, [_detection(dbh=33.0, num="2")])  # phone re-run: new estimate, new number
    a = _arbres(gpkg)["a"]
    assert a["dbh_cm"] == 42.5 and a["dbh_source"] == "mesure"
    assert a["essence_arbre"] == "hetre" and a["statut"] == "mort" and a["qualite"] == "B"
    assert a["tree_id"] == "2"  # the rest still follows the phone


def test_diametre_mesure_nul_est_une_mesure(gpkg):
    ecrire_detections(gpkg, [_detection()])
    with sqlite3.connect(gpkg) as cx:
        cx.execute("UPDATE arbres SET dbh_cm=0, dbh_source='mesure' WHERE uuid='a'")
    ecrire_detections(gpkg, [_detection(dbh=12.0)])
    a = _arbres(gpkg)["a"]
    assert a["dbh_cm"] == 0 and a["dbh_source"] == "mesure"
    assert a["essence_arbre"] is None and a["statut"] == "vivant" and a["qualite"] is None


def test_un_arbre_mesure_ne_protege_pas_les_autres(gpkg):
    ecrire_detections(gpkg, [_detection("a"), _detection("b", dbh=20.0, num="2", x=924995.0)])
    with sqlite3.connect(gpkg) as cx:
        cx.execute("UPDATE arbres SET dbh_cm=42.5, dbh_source='mesure' WHERE uuid='a'")
    ecrire_detections(gpkg, [_detection("a", dbh=33.0), _detection("b", dbh=21.0, num="2", x=924995.0)])
    arbres = _arbres(gpkg)
    assert arbres["a"]["dbh_cm"] == 42.5
    assert arbres["b"]["dbh_cm"] == 21.0 and arbres["b"]["dbh_source"] == "estime"
