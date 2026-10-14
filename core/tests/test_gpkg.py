import sqlite3

import pytest

gpd = pytest.importorskip("geopandas")
pyogrio = pytest.importorskip("pyogrio")
from shapely.geometry import Point

from groma.gpkg import (
    charger_schema,
    creer_gpkg,
    decoder_point,
    ecrire_detections,
    encoder_point,
    essences,
    lire_points,
    mettre_a_jour_etat,
    noms_colonnes,
)


@pytest.fixture
def gpkg(tmp_path):
    placettes = gpd.GeoDataFrame(
        {"plot_id": ["P01", "P02"], "visit_order": [1, 2], "type": ["Base", "Over"], "wgt": [12.5, 12.5], "ip": [0.08, 0.08], "stratum": ["s1", "s2"]},
        geometry=[Point(925000, 6700000), Point(925200, 6700100)],
        crs=2154,
    )
    return creer_gpkg(tmp_path / "inventaire_video.gpkg", placettes)


def test_schema_noms_nmt():
    for c in charger_schema()["couches"]:
        assert len(c["nom"]) <= 30
        for col in c["colonnes"]:
            assert len(col["nom"]) <= 30 and col["nom"] == col["nom"].lower()
    assert len({e["code"] for e in essences()}) == len(essences())


def test_couches_et_colonnes(gpkg):
    couches = dict(pyogrio.list_layers(gpkg))
    assert set(couches) == {"placettes", "centres_mesures", "cibles", "arbres", "detections", "houppiers", "parcelles", "projet"}
    assert couches["parcelles"] == "MultiPolygon"
    assert couches["projet"] is None  # non-spatial table
    p = gpd.read_file(gpkg, layer="placettes")
    assert list(p.columns[:-1]) == noms_colonnes("placettes")
    assert p.loc[0, "wgt"] == 12.5 and p.loc[0, "rayon_m"] == 20 and p.loc[0, "unite_azimut"] == "deg"
    assert p.loc[0, "convergence_deg"] == pytest.approx(0.725608 * (p.to_crs(4326).geometry.x[0] - 3), abs=1e-4)
    a = gpd.read_file(gpkg, layer="arbres")
    assert list(a.columns[:-1]) == noms_colonnes("arbres")
    # No spatial index on the layers written by the phone (no ST_* functions on Android).
    with sqlite3.connect(gpkg) as cx:
        noms = {r[0] for r in cx.execute("SELECT name FROM sqlite_master WHERE type='trigger'")}
    assert not any(n.startswith(("rtree_placettes", "rtree_arbres", "rtree_detections")) for n in noms)
    assert {"trg_detections_insert", "trg_detections_update"} <= noms


def test_table_projet(tmp_path):
    placettes = gpd.GeoDataFrame(
        {"plot_id": ["P01", "P02", "P03"], "type": ["Base", "Over", "Base"]},
        geometry=[Point(925000, 6700000), Point(925200, 6700100), Point(925400, 6700000)],
        crs=2154,
    )
    g = creer_gpkg(tmp_path / "a.gpkg", placettes, projet={"projet_id": "20261008_x", "projet_nom": "Forêt", "nb_ug": 4, "description": None})
    with sqlite3.connect(g) as cx:
        cx.row_factory = sqlite3.Row
        lignes = [dict(r) for r in cx.execute("SELECT * FROM projet")]
        assert cx.execute("SELECT data_type FROM gpkg_contents WHERE table_name = 'projet'").fetchone()[0] == "attributes"
    assert len(lignes) == 1
    r = lignes[0]
    assert (r["projet_id"], r["projet_nom"], r["nb_ug"], r["description"]) == ("20261008_x", "Forêt", 4, None)
    # Completed from the plots.
    assert (r["nb_placettes_base"], r["nb_placettes_rempl"]) == (2, 1)
    assert r["emprise"].startswith("POLYGON ((") and r["prepare_le"] and r["prepare_par"].startswith("groma ")
    # Without projet: empty table, same columns.
    g = creer_gpkg(tmp_path / "b.gpkg", placettes)
    with sqlite3.connect(g) as cx:
        assert cx.execute("SELECT COUNT(*) FROM projet").fetchone()[0] == 0
        assert [c[1] for c in cx.execute("PRAGMA table_info(projet)")][1:] == noms_colonnes("projet")


@pytest.mark.parametrize("projet", [{"projet_id": "x"}, {"projet_id": "x", "projet_nom": "y", "inconnue": 1}])
def test_table_projet_invalide(tmp_path, projet):
    placettes = gpd.GeoDataFrame({"plot_id": ["P01"]}, geometry=[Point(925000, 6700000)], crs=2154)
    with pytest.raises(ValueError, match="projet"):
        creer_gpkg(tmp_path / "a.gpkg", placettes, projet=projet)
    assert not (tmp_path / "a.gpkg").exists()  # checked before anything is written


def test_couche_parcelles(tmp_path):
    from shapely.geometry import MultiPolygon, box

    placettes = gpd.GeoDataFrame({"plot_id": ["P01"]}, geometry=[Point(925000, 6700000)], crs=2154)
    parcelles = gpd.GeoDataFrame(
        {"idu": ["212000000A0036", "212000000A0030"], "commune": ["21200", "21200"], "section": ["A", "A"],
         "numero": ["36", "30"], "contenance_m2": [10000.0, 7500.0]},
        geometry=[box(925000, 6700000, 925100, 6700100), MultiPolygon([box(925200, 6700000, 925250, 6700100), box(925300, 6700000, 925350, 6700100)])],
        crs=2154,
    ).to_crs(4326)
    g = creer_gpkg(tmp_path / "a.gpkg", placettes, parcelles=parcelles)
    lu = gpd.read_file(g, layer="parcelles")
    assert list(lu.columns[:-1]) == noms_colonnes("parcelles")
    assert lu.crs.to_epsg() == 2154 and set(lu.geom_type) == {"MultiPolygon"}
    assert lu.loc[0, "idu"] == "212000000A0036" and lu.loc[1, "contenance_m2"] == 7500
    assert lu.area.round().tolist() == [10000, 10000]
    # Without parcels: empty layer, same columns.
    g = creer_gpkg(tmp_path / "b.gpkg", placettes)
    assert len(gpd.read_file(g, layer="parcelles")) == 0


def test_geometrie_point_aller_retour():
    assert decoder_point(encoder_point(925001.25, 6700002.5)) == (925001.25, 6700002.5)


def test_protocole_detections(gpkg):
    det = [
        {"uuid": "a", "plot_id": "P01", "x": 925003.0, "y": 6700004.0, "num_provisoire": "1", "dbh_cm": 30.0,
         "azimut": 36.87, "distance_m": 5.0, "h_m": 24.0, "h_source": "mnh", "etat_detection": "provisoire", "methode": "embarque"},
        {"uuid": "b", "plot_id": "P01", "x": 924995.0, "y": 6700000.0, "num_provisoire": "2", "dbh_cm": 18.0,
         "etat_detection": "provisoire", "methode": "embarque"},
        {"uuid": "c", "plot_id": "P01", "x": 925000.0, "y": 6699990.0, "num_provisoire": "3", "dbh_cm": 9.0,
         "etat_detection": "faux_positif", "methode": "embarque"},
    ]
    assert ecrire_detections(gpkg, det) == (3, 0)
    arbres = {a["uuid"]: a for a in lire_points(gpkg, "arbres")}
    assert set(arbres) == {"a", "b"}  # faux positif never promoted
    assert arbres["a"]["tree_id"] == "1" and arbres["a"]["statut"] == "vivant"
    assert (arbres["a"]["x"], arbres["a"]["y"]) == (925003.0, 6700004.0)

    # QField: the operator fills the species and measures a height.
    with sqlite3.connect(gpkg) as cx:
        cx.execute("UPDATE arbres SET essence_arbre='hetre', notes='fourche', h_m=27.5, h_source='mesuree' WHERE uuid='a'")
        cx.execute("UPDATE arbres SET essence_arbre='charme' WHERE uuid='b'")

    # Phone re-run: new measures, new numbers.
    det[0].update(dbh_cm=31.5, num_provisoire="2", h_m=24.5)
    det[1].update(dbh_cm=19.0, num_provisoire="1")
    assert ecrire_detections(gpkg, det[:2]) == (0, 2)
    arbres = {a["uuid"]: a for a in lire_points(gpkg, "arbres")}
    assert arbres["a"]["dbh_cm"] == 31.5 and arbres["a"]["tree_id"] == "2"
    assert arbres["a"]["essence_arbre"] == "hetre" and arbres["a"]["notes"] == "fourche"
    assert arbres["a"]["h_m"] == 27.5 and arbres["a"]["h_source"] == "mesuree"
    assert arbres["b"]["essence_arbre"] == "charme" and arbres["b"]["tree_id"] == "1"

    # A tree already made definitive is never touched by a phone write.
    with sqlite3.connect(gpkg) as cx:
        cx.execute("UPDATE arbres SET etat_detection='definitif' WHERE uuid='b'")
    det[1].update(dbh_cm=50.0)
    ecrire_detections(gpkg, det[1:2])
    assert {a["uuid"]: a for a in lire_points(gpkg, "arbres")}["b"]["dbh_cm"] == 19.0

    # GDAL still reads a consistent layer.
    lu = gpd.read_file(gpkg, layer="arbres")
    assert len(lu) == 2 and lu.crs.to_epsg() == 2154


def test_etat_traitement(gpkg):
    mettre_a_jour_etat(gpkg, "P02", "provisoire")
    assert {p["plot_id"]: p["etat_traitement"] for p in lire_points(gpkg, "placettes")}["P02"] == "provisoire"
    with pytest.raises(ValueError):
        mettre_a_jour_etat(gpkg, "P02", "fini")


def test_verrou_attendu(gpkg):
    # Another writer holds the lock: the write fails cleanly after the timeout, nothing half-written.
    cx = sqlite3.connect(gpkg, isolation_level=None)
    cx.execute("BEGIN IMMEDIATE")
    try:
        with pytest.raises(sqlite3.OperationalError):
            ecrire_detections(gpkg, [{"uuid": "z", "plot_id": "P01", "x": 0.0, "y": 0.0}], delai_verrou_ms=200)
    finally:
        cx.execute("ROLLBACK")
        cx.close()
    assert lire_points(gpkg, "detections") == []
