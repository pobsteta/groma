"""Mission 020, point 5: a tree added by hand in Groma (methode == "terrain", number "A<n>") through the final matching.

API EXPECTED (signatures unchanged): `groma.rapprochement.rapprocher`. A provisional row added in Groma
looks like the others, with `methode = "terrain"`, `tree_id = num_provisoire = "A1"`, `dbh_source =
"mesure"`, `etat_detection = "provisoire"`. Rules, written by hand (rule 5 of CLAUDE.md):

  * matched with a definitive detection (tolerance 0.5 m): the tape diameter and `dbh_source` are kept,
    and so are essence, statut, qualite, notes, photos; the position becomes the definitive one;
    `methode` becomes the definitive one (as for every matched tree; the origin stays readable in
    `num_provisoire == "A1"`); the final number is a normal definitive number (digits), never "A1";
    `etat_detection` = definitif when an essence is filled, else a_completer; the `ecart_dbh` alert
    is raised as for any tape diameter;
  * NOT matched: the tree is kept (never deleted), `a_verifier`, number `V<n>`, with its position, diameter,
    dbh_source and attributes, and listed in `non_retrouves`; this holds even with no attribute other than
    the diameter, a diameter under the threshold, or a position outside the radius;
  * the numbers of the other trees do not depend on the unmatched added trees; every `tree_id` is unique;
  * the schema domain `methode` contains "terrain" and the schema version is at least 1.5.0.

Several of these already pass with the existing code (characterisation, they must stay green).
"""
import json
import re
from pathlib import Path

import pytest

from groma.numerotation import ParametresNumerotation
from groma.rapprochement import ParametresRapprochement, rapprocher

SCHEMA = Path(__file__).resolve().parents[1] / "src/groma/donnees/inventaire_video.schema.json"

# Centre (1000, 2000); video tree at 270 deg / 8 m; added tree at 45 deg / 6 m; another added tree at 180 deg / 5 m.
P_VIDEO = (992.0, 2000.0)
P_AJOUTE = (1004.242641, 2004.242641)
P_AJOUTE2 = (1000.0, 1995.0)
P_LOIN = (1000.0, 1970.0)  # 30 m from the centre, radius 20 m


def _prov(uuid, tree_id, pos, dbh, source, **kw):
    base = {"uuid": uuid, "plot_id": "P01", "tree_id": tree_id, "dbh_cm": dbh, "dbh_source": source,
            "statut": "vivant", "etat_detection": "provisoire", "methode": "embarque", "h_m": None,
            "h_source": "aucune", "essence_arbre": None, "qualite": None, "notes": None, "photos": None,
            "x": pos[0], "y": pos[1]}
    base.update(kw)
    return base


def _ajoute(uuid, tree_id, pos, dbh, **kw):
    kw.setdefault("methode", "terrain")
    kw.setdefault("num_provisoire", tree_id)
    return _prov(uuid, tree_id, pos, dbh, "mesure", **kw)


def _def(pos, dbh):
    return {"plot_id": "P01", "dbh_cm": dbh, "methode": "classique", "x": pos[0], "y": pos[1]}


def _executer(provisoires, definitifs):
    compteur = iter(range(1000))
    return rapprocher(provisoires, definitifs, (1000.0, 2000.0), 0.0,
                      ParametresNumerotation(20.0, 7.5, "deg", 1.0), ParametresRapprochement(tolerance_m=0.5),
                      generer_uuid=lambda: f"n{next(compteur)}")


def _par_uuid(res):
    return {a["uuid"]: a for a in res.arbres}


# --- matched ----------------------------------------------------------------------------------------------

@pytest.fixture
def apparie():
    prov = [
        _prov("v1", "1", P_VIDEO, 20.0, "estime"),
        _ajoute("t1", "A1", P_AJOUTE, 38.0, essence_arbre="hetre", statut="mort", qualite="B",
                notes="fourche à 3 m", photos="DCIM/P01_A1_1.jpg;DCIM/P01_A1_2.jpg"),
    ]
    res = _executer(prov, [_def((1004.30, 2004.20), 31.0), _def(P_VIDEO, 22.0)])
    return res, _par_uuid(res)


def test_arbre_ajoute_apparie_garde_ruban_et_saisies(apparie):
    _, arbres = apparie
    t = arbres["t1"]
    assert (t["dbh_cm"], t["dbh_source"]) == (38.0, "mesure")
    assert (t["essence_arbre"], t["statut"], t["qualite"]) == ("hetre", "mort", "B")
    assert t["notes"] == "fourche à 3 m"
    assert t["photos"] == "DCIM/P01_A1_1.jpg;DCIM/P01_A1_2.jpg"
    assert t["uuid"] == "t1"


def test_arbre_ajoute_apparie_prend_la_position_et_la_methode_du_definitif(apparie):
    _, arbres = apparie
    t = arbres["t1"]
    assert (t["x"], t["y"]) == (1004.30, 2004.20)
    assert t["methode"] == "classique"
    assert t["num_provisoire"] == "A1"
    assert t["etat_detection"] == "definitif"


def test_numero_definitif_normal_et_unique(apparie):
    res, arbres = apparie
    assert re.fullmatch(r"\d+", arbres["t1"]["tree_id"]), arbres["t1"]["tree_id"]
    ids = [a["tree_id"] for a in res.arbres]
    assert len(ids) == len(set(ids))
    assert "t1" in {u for u, _, _ in res.apparies}
    assert "t1" not in res.non_retrouves and res.nouveaux == []


def test_arbre_ajoute_sans_essence_apparie_a_completer():
    res = _executer([_ajoute("t1", "A1", P_AJOUTE, 38.0)], [_def(P_AJOUTE, 31.0)])
    t = _par_uuid(res)["t1"]
    assert (t["dbh_cm"], t["dbh_source"], t["etat_detection"]) == (38.0, "mesure", "a_completer")


def test_ecart_de_diametre_signale_pour_un_arbre_ajoute():
    res = _executer([_ajoute("t1", "A1", P_AJOUTE, 52.0, essence_arbre="hetre")], [_def(P_AJOUTE, 31.0)])
    assert ("t1", "ecart_dbh") in {(a["uuid"], a["motif"]) for a in res.alertes}  # 21 cm > 10 cm and > 15.5 cm
    assert _par_uuid(res)["t1"]["dbh_cm"] == 52.0


# --- not matched ---------------------------------------------------------------------------------------------

def test_arbre_ajoute_non_apparie_conserve_a_verifier():
    prov = [
        _prov("v1", "1", P_VIDEO, 20.0, "estime"),
        _ajoute("t1", "A1", P_AJOUTE2, 45.0, essence_arbre="chene_sessile", statut="chablis", qualite="C", notes="oublié"),
    ]
    res = _executer(prov, [_def(P_VIDEO, 22.0)])
    arbres = _par_uuid(res)
    t = arbres["t1"]
    assert t["etat_detection"] == "a_verifier"
    assert re.fullmatch(r"V\d+", t["tree_id"]), t["tree_id"]
    assert t["num_provisoire"] == "A1"
    assert (t["dbh_cm"], t["dbh_source"], t["methode"]) == (45.0, "mesure", "terrain")
    assert (t["essence_arbre"], t["statut"], t["qualite"], t["notes"]) == ("chene_sessile", "chablis", "C", "oublié")
    assert (t["x"], t["y"]) == P_AJOUTE2
    assert "t1" in res.non_retrouves and "t1" not in res.nouveaux
    # The video tree keeps number "1": an unmatched added tree shifts nothing.
    assert arbres["v1"]["tree_id"] == "1"


def test_arbre_ajoute_non_apparie_conserve_meme_sans_aucune_saisie_autre_que_le_ruban():
    res = _executer([_ajoute("t1", "A1", P_AJOUTE2, 45.0)], [])
    t = _par_uuid(res)["t1"]
    assert t["etat_detection"] == "a_verifier" and t["dbh_cm"] == 45.0 and t["dbh_source"] == "mesure"


def test_arbre_ajoute_sous_le_seuil_non_apparie_conserve():
    res = _executer([_ajoute("t1", "A1", P_AJOUTE2, 5.0)], [])  # threshold 7.5 cm
    t = _par_uuid(res)["t1"]
    assert t["etat_detection"] == "a_verifier" and t["dbh_cm"] == 5.0


def test_arbre_ajoute_hors_rayon_non_apparie_conserve():
    res = _executer([_ajoute("t1", "A1", P_LOIN, 40.0, essence_arbre="hetre")], [])
    t = _par_uuid(res)["t1"]
    assert t["etat_detection"] == "a_verifier" and t["essence_arbre"] == "hetre" and (t["x"], t["y"]) == P_LOIN


def test_arbre_ajoute_et_detection_proche_mais_hors_tolerance_coexistent():
    # Definitive trunk 0.8 m from the added tree (tolerance 0.5 m): two trees, none lost.
    res = _executer([_ajoute("t1", "A1", P_AJOUTE2, 45.0, essence_arbre="hetre")], [_def((1000.0, 1994.2), 44.0)])
    assert len(res.arbres) == 2
    arbres = _par_uuid(res)
    assert arbres["t1"]["etat_detection"] == "a_verifier" and arbres["t1"]["dbh_cm"] == 45.0
    nouveau = arbres[res.nouveaux[0]]
    assert nouveau["etat_detection"] == "a_completer"
    ids = [a["tree_id"] for a in res.arbres]
    assert len(ids) == len(set(ids))


def test_deux_arbres_ajoutes_dont_un_seul_apparie():
    prov = [
        _ajoute("t1", "A1", P_AJOUTE, 38.0, essence_arbre="hetre"),
        _ajoute("t2", "A2", P_AJOUTE2, 41.0),
    ]
    res = _executer(prov, [_def(P_AJOUTE, 36.0)])
    arbres = _par_uuid(res)
    assert arbres["t1"]["etat_detection"] == "definitif" and re.fullmatch(r"\d+", arbres["t1"]["tree_id"])
    assert arbres["t2"]["etat_detection"] == "a_verifier" and arbres["t2"]["tree_id"] == "V1"
    assert {a["num_provisoire"] for a in res.arbres} == {"A1", "A2"}


# --- schema --------------------------------------------------------------------------------------------------------

def test_schema_methode_terrain_et_version():
    schema = json.loads(SCHEMA.read_text())
    assert "terrain" in schema["domaines"]["methode"]
    assert {"embarque", "classique", "ia"} <= set(schema["domaines"]["methode"]), "valeurs existantes conservées"
    version = tuple(int(n) for n in schema["version"].split("."))
    assert version >= (1, 5, 0), "version mineure suivante (023)"
