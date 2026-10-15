import json
import sqlite3

import pytest

gpd = pytest.importorskip("geopandas")
pyogrio = pytest.importorskip("pyogrio")
from shapely.geometry import Point

from groma.gpkg import lire_points
from groma.preparer import lire_metadonnees, lire_parcelles, lire_plan, lire_ugf, main, preparer_projet


@pytest.fixture
def plan(tmp_path):
    """samples.gpkg as saved by nemetonshiny's sampling tab (layer plots)."""
    p = gpd.GeoDataFrame(
        {
            "plot_id": ["P01", "P02", "P03"],
            "type": ["Base", "Base", "Over"],
            "visit_order": [1, 2, 3],
            "stratum": ["s1", "s2", "s1"],
            "ip": [0.08, 0.04, 0.08],
            "wgt": [12.5, 25.0, 12.5],
            "colonne_inconnue": ["x", "y", "z"],
        },
        geometry=[Point(925000, 6700000), Point(925200, 6700100), Point(925400, 6700200)],
        crs=2154,
    )
    chemin = tmp_path / "samples.gpkg"
    pyogrio.write_dataframe(p, chemin, layer="plots", driver="GPKG")
    return chemin


def test_plan_vers_projet(plan, tmp_path):
    gpkg = preparer_projet(lire_plan(plan), tmp_path / "terrain", rayon_m=15, declinaison_deg=2.1)
    lignes = {r["plot_id"]: r for r in lire_points(gpkg, "placettes")}
    assert sorted(lignes) == ["P01", "P02", "P03"]
    p2 = lignes["P02"]
    # Groma settings applied to every plot.
    assert (p2["rayon_m"], p2["seuil_diam_cm"], p2["unite_azimut"], p2["declinaison_deg"]) == (15.0, 7.5, "deg", 2.1)
    assert p2["etat_traitement"] == "a_faire" and p2["convergence_deg"] is not None
    # Plan design columns kept as they are.
    assert (p2["type"], p2["visit_order"], p2["stratum"], p2["ip"], p2["wgt"]) == ("Base", 2, "s2", 0.04, 25.0)
    assert (p2["x"], p2["y"]) == pytest.approx((925200, 6700100))
    assert "colonne_inconnue" not in p2
    assert {"arbres", "detections", "cibles", "centres_mesures", "houppiers"} <= {c[0] for c in pyogrio.list_layers(gpkg)}


def test_selection(plan, tmp_path):
    g = preparer_projet(lire_plan(plan), tmp_path / "a", rayon_m=20, declinaison_deg=0, base_seulement=True)
    assert [r["plot_id"] for r in lire_points(g, "placettes")] == ["P01", "P02"]
    g = preparer_projet(lire_plan(plan), tmp_path / "b", rayon_m=20, declinaison_deg=0, plot_ids=["P03"])
    assert [r["plot_id"] for r in lire_points(g, "placettes")] == ["P03"]
    with pytest.raises(ValueError, match="P09"):
        preparer_projet(lire_plan(plan), tmp_path / "c", rayon_m=20, declinaison_deg=0, plot_ids=["P09"])


def test_couche_placettes_prioritaire(plan, tmp_path):
    # QField export: layer placettes, read before plots.
    autre = lire_plan(plan).iloc[:1]
    pyogrio.write_dataframe(autre, plan, layer="placettes", driver="GPKG", append=False)
    assert list(lire_plan(plan)["plot_id"]) == ["P01"]
    assert len(lire_plan(plan, "plots")) == 3
    with pytest.raises(ValueError, match="absente"):
        lire_plan(plan, "inconnue")


@pytest.mark.parametrize("reglage", [{"rayon_m": 0.5}, {"rayon_m": 60}, {"declinaison_deg": 45}, {"unite_azimut": "rad"}])
def test_reglages_hors_domaine(plan, tmp_path, reglage):
    args = {"rayon_m": 15, "declinaison_deg": 1.0} | reglage
    with pytest.raises(ValueError):
        preparer_projet(lire_plan(plan), tmp_path / "x", **args)


def test_ne_remplace_pas_un_projet_existant(plan, tmp_path):
    preparer_projet(lire_plan(plan), tmp_path / "t", rayon_m=15, declinaison_deg=1)
    with pytest.raises(FileExistsError):
        preparer_projet(lire_plan(plan), tmp_path / "t", rayon_m=15, declinaison_deg=1)
    preparer_projet(lire_plan(plan), tmp_path / "t", rayon_m=12, declinaison_deg=1, ecraser=True)
    assert lire_points(tmp_path / "t" / "inventaire_video.gpkg", "placettes")[0]["rayon_m"] == 12.0


def test_ligne_de_commande(plan, tmp_path, capsys):
    main([str(plan), "--sortie", str(tmp_path / "cli"), "--rayon", "17.5", "--declinaison", "1.8",
          "--unite", "gr", "--placettes", "P01, P03", "--nom", "Essai"])
    lignes = lire_points(tmp_path / "cli" / "inventaire_video.gpkg", "placettes")
    assert [(r["plot_id"], r["rayon_m"], r["unite_azimut"]) for r in lignes] == [("P01", 17.5, "gr"), ("P03", 17.5, "gr")]
    with pytest.raises(SystemExit):  # rayon obligatoire
        main([str(plan), "--sortie", str(tmp_path / "x"), "--declinaison", "1"])


def _ligne_projet(gpkg) -> dict:
    with sqlite3.connect(gpkg) as cx:
        cx.row_factory = sqlite3.Row
        return dict(cx.execute("SELECT * FROM projet").fetchone())


@pytest.fixture
def projet_nemetonshiny(plan, tmp_path):
    """<projets>/<id>/ as nemetonshiny keeps it: metadata.json, data/samples.gpkg, parcels, communes."""
    racine = tmp_path / "projects" / "20261008_212542_uywc"
    (racine / "data").mkdir(parents=True)
    plan.rename(racine / "data" / "samples.gpkg")
    (racine / "metadata.json").write_text(json.dumps({
        "id": "20261008_212542_uywc", "name": "Forêt communale de Couchey (ONF)", "description": "19 parcelles",
        "owner": "", "parcels_count": 2, "ug_count": 3, "samples_generated_at": "2026-10-12 20:05:07",
    }), "utf-8")
    from shapely.geometry import box

    parcelles = gpd.GeoDataFrame(
        {"id": ["212000000A0036", "212000000A0030"], "commune": ["21200", "21200"], "section": ["A", "A"],
         "numero": ["36", "30"], "contenance": [10000, 5000], "arpente": [False, False]},
        geometry=[box(925000, 6700000, 925100, 6700100), box(925100, 6700000, 925150, 6700100)],
        crs=2154,
    ).to_crs(4326)  # nemetonshiny keeps parcels in WGS84
    pyogrio.write_dataframe(parcelles, racine / "data" / "parcels.gpkg", layer="parcels", driver="GPKG")
    communes = gpd.GeoDataFrame({"code": ["21200"], "nom": ["Couchey"]}, geometry=[box(924000, 6699000, 926000, 6701000)], crs=2154)
    pyogrio.write_dataframe(communes, racine / "data" / "commune.gpkg", layer="commune", driver="GPKG")
    # Two UGs: parcel 30 alone (ONF number 10), parcel 36 split in two tenements (ONF number 2).
    tenements = gpd.GeoDataFrame(
        {"tenement_id": ["t1", "t2", "t3"], "parent_parcelle_id": ["212000000A0036", "212000000A0036", "212000000A0030"],
         "ug_id": ["ug_b", "ug_b", "ug_a"], "surface_m2": [6000.0, 4000.0, 5000.0]},
        geometry=[box(925000, 6700000, 925060, 6700100), box(925060, 6700000, 925100, 6700100), box(925100, 6700000, 925150, 6700100)],
        crs=2154,
    ).to_crs(4326)
    pyogrio.write_dataframe(tenements, racine / "data" / "tenements.gpkg", layer="tenements", driver="GPKG")
    (racine / "data" / "ugs.json").write_text(json.dumps({
        "ug_id": ["ug_a", "ug_b"], "label": ["Forêt communale de Couchey — parcelle 10", "Forêt communale de Couchey — parcelle 2"],
        "groupe": [None, None], "onf_foret_nom": ["Forêt communale de Couchey"] * 2, "onf_parcelle": ["10", "2"],
    }), "utf-8")
    return racine / "data" / "samples.gpkg"


def test_metadonnees_nemetonshiny(projet_nemetonshiny):
    m = lire_metadonnees(projet_nemetonshiny)
    assert m["projet_id"] == "20261008_212542_uywc" and m["projet_nom"] == "Forêt communale de Couchey (ONF)"
    assert (m["description"], m["proprietaire"], m["nb_parcelles"], m["nb_ug"]) == ("19 parcelles", None, 2, 3)
    assert m["plan_genere_le"] == "2026-10-12 20:05:07"
    assert m["surface_zone_ha"] == pytest.approx(1.5, abs=0.01) and m["surface_cadastre_ha"] == 1.5
    assert m["communes"] == "Couchey (21200)"


def test_metadonnees_hors_projet(plan, tmp_path):
    assert lire_metadonnees(plan) == {}
    # Unreadable parcels: the project still comes out, without areas.
    (tmp_path / "p" / "data").mkdir(parents=True)
    (tmp_path / "p" / "metadata.json").write_text('{"name": "Sans parcelles"}', "utf-8")
    (tmp_path / "p" / "data" / "parcels.gpkg").write_text("pas un GeoPackage")
    m = lire_metadonnees(tmp_path / "p" / "data" / "samples.gpkg")
    assert m["projet_id"] == "p" and m["projet_nom"] == "Sans parcelles" and "surface_zone_ha" not in m


def test_ligne_de_commande_projet_nemetonshiny(projet_nemetonshiny, tmp_path, monkeypatch):
    # Output folder named after the project id, in the current directory.
    monkeypatch.chdir(tmp_path)
    main([str(projet_nemetonshiny), "--rayon", "15", "--declinaison", "2"])
    gpkg = tmp_path / "20261008_212542_uywc" / "inventaire_video.gpkg"
    r = _ligne_projet(gpkg)
    assert (r["projet_id"], r["projet_nom"], r["communes"], r["nb_ug"]) == (
        "20261008_212542_uywc", "Forêt communale de Couchey (ONF)", "Couchey (21200)", 3)
    assert (r["nb_placettes_base"], r["nb_placettes_rempl"]) == (2, 1)
    assert r["prepare_par"].startswith("groma-preparer ")
    # --nom wins over the project name.
    main([str(projet_nemetonshiny), "--rayon", "15", "--declinaison", "2", "--nom", "Couchey 2026", "--ecraser"])
    assert _ligne_projet(gpkg)["projet_nom"] == "Couchey 2026"


def test_parcelles_copiees(projet_nemetonshiny, tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    main([str(projet_nemetonshiny), "--rayon", "15", "--declinaison", "2"])
    p = gpd.read_file(tmp_path / "20261008_212542_uywc" / "inventaire_video.gpkg", layer="parcelles")
    assert p["idu"].tolist() == ["212000000A0036", "212000000A0030"]
    assert (p.loc[0, "section"], p.loc[0, "numero"], p.loc[0, "contenance_m2"]) == ("A", "36", 10000)
    assert "arpente" not in p.columns and p.crs.to_epsg() == 2154
    assert p.area.sum() == pytest.approx(15000, rel=1e-3)
    # Outside a nemetonshiny project: no parcels, empty layer.
    assert lire_parcelles(tmp_path / "ailleurs" / "samples.gpkg") is None


def test_ugf_copiees(projet_nemetonshiny, tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    main([str(projet_nemetonshiny), "--rayon", "15", "--declinaison", "2"])
    u = gpd.read_file(tmp_path / "20261008_212542_uywc" / "inventaire_video.gpkg", layer="ugf")
    # Tenements merged by UG, ONF numbers in natural order (2 before 10).
    assert u["ug_id"].tolist() == ["ug_b", "ug_a"] and u["parcelle_onf"].tolist() == ["2", "10"]
    assert u.loc[0, "libelle"] == "Forêt communale de Couchey — parcelle 2" and u.loc[0, "foret"] == "Forêt communale de Couchey"
    assert u.loc[0, "surface_m2"] == 10000 and u.crs.to_epsg() == 2154 and set(u.geom_type) == {"MultiPolygon"}
    assert u.area.round().tolist() == [10000, 5000]
    assert lire_ugf(tmp_path / "ailleurs" / "samples.gpkg") is None


def test_ugf_sans_attributs(projet_nemetonshiny):
    # Without ugs.json the outlines still come, without labels.
    (projet_nemetonshiny.parent / "ugs.json").unlink()
    u = lire_ugf(projet_nemetonshiny)
    assert sorted(u["ug_id"]) == ["ug_a", "ug_b"] and "libelle" not in u.columns


def test_ligne_de_commande_hors_projet(plan, tmp_path):
    with pytest.raises(SystemExit):  # --nom obligatoire hors projet nemetonshiny
        main([str(plan), "--sortie", str(tmp_path / "a"), "--rayon", "15", "--declinaison", "1"])
    with pytest.raises(SystemExit):  # --sortie aussi
        main([str(plan), "--nom", "Essai", "--rayon", "15", "--declinaison", "1"])
    main([str(plan), "--sortie", str(tmp_path / "a"), "--nom", "Essai", "--rayon", "15", "--declinaison", "1"])
    r = _ligne_projet(tmp_path / "a" / "inventaire_video.gpkg")
    assert (r["projet_id"], r["projet_nom"], r["surface_zone_ha"]) == ("a", "Essai", None)
