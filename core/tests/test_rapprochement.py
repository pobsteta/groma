import json
import math
from pathlib import Path

import pytest

from groma.numerotation import ParametresNumerotation
from groma.rapprochement import ATTRIBUTS_TERRAIN, ParametresRapprochement, rapprocher

DOSSIER = Path(__file__).resolve().parents[2] / "fixtures" / "rapprochement"
CAS = sorted(DOSSIER.glob("*.json"))


def _executer(cas):
    p = cas["parametres"]
    compteur = iter(range(1000))
    return rapprocher(
        cas["provisoires"],
        cas["definitifs"],
        cas["centre"],
        cas["convergence_deg"],
        ParametresNumerotation(p["rayon_m"], p["seuil_diam_cm"], p["unite_azimut"], p["tolerance_deg"]),
        ParametresRapprochement(tolerance_m=p["tolerance_m"]),
        generer_uuid=lambda: f"n{next(compteur)}",
    )


@pytest.mark.parametrize("chemin", CAS, ids=lambda p: p.stem)
def test_cas_commun(chemin):
    cas = json.loads(chemin.read_text(encoding="utf-8"))
    res = _executer(cas)
    att = cas["attendu"]
    par_uuid = {a["uuid"]: a for a in res.arbres}

    for u, valeurs in att["par_uuid"].items():
        for k, v in valeurs.items():
            assert par_uuid[u][k] == pytest.approx(v) if isinstance(v, float) else par_uuid[u][k] == v, (u, k)

    nouveaux = [par_uuid[u] for u in res.nouveaux if u in par_uuid]
    assert len(nouveaux) == len(att["nouveaux"])
    cx, cy = cas["centre"]
    for n in att["nouveaux"]:
        a = math.radians(n["az"])
        x, y = cx + n["d"] * math.sin(a), cy + n["d"] * math.cos(a)
        trouve = [t for t in nouveaux if math.hypot(t["x"] - x, t["y"] - y) < 1e-3]
        assert len(trouve) == 1
        for k in ("tree_id", "etat_detection", "dbh_cm"):
            assert trouve[0][k] == n[k]

    assert sorted(res.non_retrouves) == sorted(att["non_retrouves"])
    assert {tuple(t) for t in res.numeros_modifies} == {tuple(t) for t in att["numeros_modifies"]}
    for alerte in att.get("alertes", []):
        assert any(a["uuid"] == alerte["uuid"] and a["motif"] == alerte["motif"] for a in res.alertes)
    if "nombre_arbres" in att:
        assert len(res.arbres) == att["nombre_arbres"]


@pytest.mark.parametrize("chemin", CAS, ids=lambda p: p.stem)
def test_aucune_perte_d_attribut_terrain(chemin):
    cas = json.loads(chemin.read_text(encoding="utf-8"))
    res = _executer(cas)
    par_uuid = {a["uuid"]: a for a in res.arbres}
    for p in cas["provisoires"]:
        assert p["uuid"] in par_uuid, "arbre provisoire perdu"
        for k in ATTRIBUTS_TERRAIN:
            assert par_uuid[p["uuid"]].get(k) == p.get(k), (p["uuid"], k)
        if p.get("h_source") == "mesuree":
            assert par_uuid[p["uuid"]]["h_m"] == p["h_m"]


def test_tree_id_unique_et_non_vide():
    for chemin in CAS:
        res = _executer(json.loads(chemin.read_text(encoding="utf-8")))
        ids = [a["tree_id"] for a in res.arbres]
        assert all(ids) and len(ids) == len(set(ids))
