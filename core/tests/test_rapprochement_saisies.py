"""Mission 017: attributes entered in Groma survive the final matching (rule 5 of CLAUDE.md).

Expected API (signatures unchanged): `groma.rapprochement.rapprocher`; the `provisoires` rows now
carry `dbh_source` ("estime" | "mesure"). Rules, written by hand:
  * a provisional tree with dbh_source == "mesure" keeps dbh_cm and dbh_source after matching, even
    when the definitive detection has another diameter; the `ecart_dbh` alert is still raised
    (gap > 10 cm AND > 50 % of the smaller, thresholds unchanged, compared with the tape diameter);
  * dbh_source == "estime" behaves as before (diameter replaced by the definitive one);
  * essence_arbre, statut, qualite entered in Groma (and dbh_source) are never lost;
  * `dbh_source` belongs to ATTRIBUTS_TERRAIN.
"""
import pytest

from groma.numerotation import ParametresNumerotation
from groma.rapprochement import ATTRIBUTS_TERRAIN, ParametresRapprochement, rapprocher

# Centre (1000, 2000); u1 at azimuth 45 deg / 6 m, u2 at 270 deg / 8 m, u3 at 180 deg / 5 m.
P1 = (1004.242641, 2004.242641)
P2 = (992.0, 2000.0)
P3 = (1000.0, 1995.0)


def _prov(uuid, tree_id, pos, dbh, source, **kw):
    base = {"uuid": uuid, "plot_id": "P01", "tree_id": tree_id, "dbh_cm": dbh, "dbh_source": source,
            "statut": "vivant", "etat_detection": "provisoire", "methode": "embarque", "h_m": None,
            "h_source": "aucune", "essence_arbre": None, "qualite": None, "notes": None, "photos": None,
            "x": pos[0], "y": pos[1]}
    base.update(kw)
    return base


def _def(pos, dbh):
    return {"plot_id": "P01", "dbh_cm": dbh, "methode": "classique", "x": pos[0], "y": pos[1]}


def _executer(provisoires, definitifs):
    compteur = iter(range(1000))
    return rapprocher(provisoires, definitifs, (1000.0, 2000.0), 0.0,
                      ParametresNumerotation(20.0, 7.5, "deg", 1.0), ParametresRapprochement(tolerance_m=0.5),
                      generer_uuid=lambda: f"n{next(compteur)}")


@pytest.fixture
def resultat():
    prov = [
        _prov("u1", "1", P1, 52.0, "mesure", essence_arbre="hetre", statut="mort", qualite="B"),
        _prov("u2", "2", P2, 20.0, "estime", essence_arbre="frene", qualite="A"),
        _prov("u3", "3", P3, 30.0, "mesure"),
    ]
    res = _executer(prov, [_def(P1, 31.0), _def(P2, 22.0), _def(P3, 31.0)])
    return prov, {a["uuid"]: a for a in res.arbres}, res


def test_dbh_source_fait_partie_des_attributs_terrain():
    assert {"dbh_source", "essence_arbre", "statut", "qualite"} <= set(ATTRIBUTS_TERRAIN)


def test_diametre_mesure_conserve_apres_rapprochement(resultat):
    _, arbres, _ = resultat
    assert arbres["u1"]["dbh_cm"] == 52.0 and arbres["u1"]["dbh_source"] == "mesure"
    assert arbres["u3"]["dbh_cm"] == 30.0 and arbres["u3"]["dbh_source"] == "mesure"
    assert arbres["u1"]["etat_detection"] == "definitif"


def test_ecart_dbh_reste_emis_pour_un_diametre_mesure(resultat):
    _, _, res = resultat
    motifs = {(a["uuid"], a["motif"]) for a in res.alertes}
    assert ("u1", "ecart_dbh") in motifs      # 52 vs 31 cm: 21 cm > 10 cm and > 15.5 cm (50 % of the smaller)
    assert ("u3", "ecart_dbh") not in motifs  # 30 vs 31 cm: 1 cm


def test_diametre_estime_remplace_comme_avant(resultat):
    _, arbres, _ = resultat
    assert arbres["u2"]["dbh_cm"] == 22.0 and arbres["u2"]["dbh_source"] == "estime"


def test_attributs_saisis_dans_groma_conserves(resultat):
    prov, arbres, _ = resultat
    assert (arbres["u1"]["essence_arbre"], arbres["u1"]["statut"], arbres["u1"]["qualite"]) == ("hetre", "mort", "B")
    assert (arbres["u2"]["essence_arbre"], arbres["u2"]["statut"], arbres["u2"]["qualite"]) == ("frene", "vivant", "A")
    for p in prov:
        for k in ATTRIBUTS_TERRAIN:
            if k == "dbh_cm" and p["dbh_source"] == "estime":
                continue  # an estimate is replaced by the definitive diameter
            assert arbres[p["uuid"]].get(k) == p.get(k), (p["uuid"], k)


def test_arbre_mesure_non_retrouve_garde_son_diametre():
    res = _executer([_prov("u1", "1", P1, 45.0, "mesure", essence_arbre="hetre")], [])
    a = {x["uuid"]: x for x in res.arbres}["u1"]
    assert a["dbh_cm"] == 45.0 and a["dbh_source"] == "mesure" and a["essence_arbre"] == "hetre"
