import pytest

gpd = pytest.importorskip("geopandas")
pyogrio = pytest.importorskip("pyogrio")
from shapely.geometry import Point

from groma.gpkg import lire_points
from groma.preparer import lire_plan, main, preparer_projet


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
          "--unite", "gr", "--placettes", "P01, P03"])
    lignes = lire_points(tmp_path / "cli" / "inventaire_video.gpkg", "placettes")
    assert [(r["plot_id"], r["rayon_m"], r["unite_azimut"]) for r in lignes] == [("P01", 17.5, "gr"), ("P03", 17.5, "gr")]
    with pytest.raises(SystemExit):  # rayon obligatoire
        main([str(plan), "--sortie", str(tmp_path / "x"), "--declinaison", "1"])
