"""Provisional (phone) -> definitive (server) tree reconciliation.

Reference implementation of the function the nemeton brief asks for
(``rapprocher_arbres()``); the shared cases are in ``fixtures/rapprochement``.

Rules
-----
* Every tree carries a stable ``uuid``, distinct from its number ``tree_id``.
* Optimal assignment (Hungarian algorithm) on the horizontal position, pairs
  farther than ``tolerance_m`` (0.5 m) forbidden; the diameter difference
  only breaks ties between close candidates (1 cm of DBH ~ 1 cm of position)
  and raises an alert when it is implausible.
* Matched tree: field attributes kept, position and diameter updated, height
  updated unless measured; ``definitif`` if the species is filled in, else
  ``a_completer``. A tree marked ``faux_positif`` by the operator keeps that
  state (alert raised if the server does find a trunk there).
* New tree (no provisional match): new uuid, ``a_completer``.
* Provisional tree not found: ``a_verifier``, attributes and position kept.
* Definitive numbering recomputed; the former number goes to
  ``num_provisoire`` and changed numbers are reported. Trees in ``a_verifier``
  get ``V1, V2...`` (by azimuth) so that ``tree_id`` stays unique and non-null
  without shifting the numbers of the trees actually found; ``faux_positif``
  trees get ``F1, F2...`` and are dropped by nemeton on import.
* No field attribute is ever lost: a tree that falls out of the inventory
  (outside the radius or under the threshold once measured) is kept as
  ``a_verifier`` when it carries field attributes.
"""

from __future__ import annotations

import math
import uuid as uuidlib
from dataclasses import dataclass, field
from typing import Sequence

import numpy as np
from scipy.optimize import linear_sum_assignment

from .azimut import vers_unite
from .numerotation import ArbreANumeroter, ParametresNumerotation, numeroter, offsets_geographiques

ATTRIBUTS_TERRAIN = ("essence_arbre", "espece", "statut", "qualite", "notes", "photos")
INTERDIT = 1e9


@dataclass(frozen=True)
class ParametresRapprochement:
    tolerance_m: float = 0.5
    poids_dbh_m_par_cm: float = 0.01
    ecart_dbh_alerte_cm: float = 10.0
    ecart_dbh_alerte_rel: float = 0.5


@dataclass
class ResultatRapprochement:
    arbres: list[dict] = field(default_factory=list)
    apparies: list[tuple[str, int, float]] = field(default_factory=list)
    nouveaux: list[str] = field(default_factory=list)
    non_retrouves: list[str] = field(default_factory=list)
    numeros_modifies: list[tuple[str, str | None, str]] = field(default_factory=list)
    alertes: list[dict] = field(default_factory=list)


def _a_attributs_terrain(a: dict) -> bool:
    return any(a.get(k) not in (None, "") for k in ("essence_arbre", "qualite", "notes", "photos")) or (
        a.get("statut") not in (None, "", "vivant")
    )


def rapprocher(
    provisoires: Sequence[dict],
    definitifs: Sequence[dict],
    centre: Sequence[float],
    convergence_deg: float = 0.0,
    numerotation: ParametresNumerotation = ParametresNumerotation(),
    params: ParametresRapprochement = ParametresRapprochement(),
    generer_uuid=lambda: str(uuidlib.uuid4()),
) -> ResultatRapprochement:
    """Reconcile the trees of ONE plot.

    ``provisoires``: rows of the ``arbres`` layer (dicts with at least uuid,
    x, y, dbh_cm, tree_id and the field attributes). ``definitifs``: server
    detections (x, y, dbh_cm, optional h_m, houppier_id, methode, score).
    Coordinates in Lambert-93; ``centre`` is the measured plot centre.
    """
    res = ResultatRapprochement()
    n_p, n_d = len(provisoires), len(definitifs)

    cout = np.full((n_p, n_d), INTERDIT)
    for i, p in enumerate(provisoires):
        for j, d in enumerate(definitifs):
            dist = math.hypot(p["x"] - d["x"], p["y"] - d["y"])
            if dist <= params.tolerance_m:
                ddbh = abs((p.get("dbh_cm") or 0.0) - (d.get("dbh_cm") or 0.0))
                cout[i, j] = dist + params.poids_dbh_m_par_cm * ddbh

    paires: list[tuple[int, int]] = []
    if n_p and n_d:
        lignes, colonnes = linear_sum_assignment(cout)
        paires = [(int(i), int(j)) for i, j in zip(lignes, colonnes) if cout[i, j] < INTERDIT]
    apparie_p = {i for i, _ in paires}
    apparie_d = {j for _, j in paires}

    sortie: list[dict] = []
    for i, j in paires:
        p, d = provisoires[i], definitifs[j]
        a = dict(p)
        a["num_provisoire"] = p.get("tree_id") or p.get("num_provisoire")
        a["x"], a["y"], a["dbh_cm"] = d["x"], d["y"], d["dbh_cm"]
        a["methode"] = d.get("methode", a.get("methode"))
        if "houppier_id" in d:
            a["houppier_id"] = d.get("houppier_id")
        if p.get("h_source") != "mesuree" and "h_m" in d:
            a["h_m"] = d.get("h_m")
            a["h_source"] = d.get("h_source") or ("mnh" if d.get("h_m") is not None else "aucune")
        if p.get("etat_detection") == "faux_positif":
            res.alertes.append({"uuid": p["uuid"], "motif": "faux_positif_retrouve"})
        else:
            a["etat_detection"] = "definitif" if p.get("essence_arbre") else "a_completer"
        dist = math.hypot(p["x"] - d["x"], p["y"] - d["y"])
        res.apparies.append((p["uuid"], j, round(dist, 3)))
        dp, dd = p.get("dbh_cm"), d.get("dbh_cm")
        if dp and dd:
            ecart = abs(dp - dd)
            if ecart > params.ecart_dbh_alerte_cm and ecart > params.ecart_dbh_alerte_rel * min(dp, dd):
                res.alertes.append(
                    {"uuid": p["uuid"], "motif": "ecart_dbh", "dbh_provisoire": dp, "dbh_definitif": dd}
                )
        sortie.append(a)

    for i, p in enumerate(provisoires):
        if i in apparie_p:
            continue
        a = dict(p)
        a["num_provisoire"] = p.get("tree_id") or p.get("num_provisoire")
        if p.get("etat_detection") != "faux_positif":
            a["etat_detection"] = "a_verifier"
        res.non_retrouves.append(p["uuid"])
        sortie.append(a)

    for j, d in enumerate(definitifs):
        if j in apparie_d:
            continue
        a = {
            "uuid": generer_uuid(),
            "plot_id": provisoires[0].get("plot_id") if provisoires else d.get("plot_id"),
            "x": d["x"],
            "y": d["y"],
            "dbh_cm": d["dbh_cm"],
            "h_m": d.get("h_m"),
            "h_source": d.get("h_source") or ("mnh" if d.get("h_m") is not None else "aucune"),
            "houppier_id": d.get("houppier_id"),
            "statut": "vivant",
            "methode": d.get("methode"),
            "num_provisoire": None,
            "etat_detection": "a_completer",
        }
        if d.get("plot_id"):
            a["plot_id"] = d["plot_id"]
        res.nouveaux.append(a["uuid"])
        sortie.append(a)

    _renumeroter(sortie, centre, convergence_deg, numerotation, res)
    res.arbres = sortie
    return res


def _renumeroter(
    arbres: list[dict],
    centre: Sequence[float],
    convergence_deg: float,
    params: ParametresNumerotation,
    res: ResultatRapprochement,
) -> None:
    for a in arbres:
        dx, dy = offsets_geographiques(a["x"] - centre[0], a["y"] - centre[1], convergence_deg)
        a["_dx"], a["_dy"] = dx, dy
        a["distance_m"] = round(math.hypot(dx, dy), 3)
        a["azimut"] = round(vers_unite(math.degrees(math.atan2(dx, dy)), params.unite_azimut), 4)

    candidats = [a for a in arbres if a.get("etat_detection") not in ("a_verifier", "faux_positif")]
    num = numeroter([ArbreANumeroter(a["uuid"], a["_dx"], a["_dy"], a["dbh_cm"]) for a in candidats], params)

    hors_inventaire = []
    for a in candidats:
        if a["uuid"] in num.numeros:
            a["tree_id"] = num.numeros[a["uuid"]]
        else:
            hors_inventaire.append(a)
            res.alertes.append({"uuid": a["uuid"], "motif": num.exclus[a["uuid"]]})
            a["etat_detection"] = "a_verifier"

    # Trees leaving the inventory without field attributes carry no
    # information: drop them. The others stay, flagged, for a field check.
    for a in hors_inventaire:
        if not _a_attributs_terrain(a) and a.get("num_provisoire") is None:
            arbres.remove(a)

    def suffixe(etat: str, prefixe: str) -> None:
        groupe = sorted((a for a in arbres if a.get("etat_detection") == etat), key=lambda a: (a["azimut"], a["uuid"]))
        for k, a in enumerate(groupe, start=1):
            a["tree_id"] = f"{prefixe}{k}"

    suffixe("a_verifier", "V")
    suffixe("faux_positif", "F")

    for a in arbres:
        a.pop("_dx", None)
        a.pop("_dy", None)
        ancien = a.get("num_provisoire")
        if ancien is not None and ancien != a["tree_id"]:
            res.numeros_modifies.append((a["uuid"], ancien, a["tree_id"]))
