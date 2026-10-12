"""Build the shared reconciliation cases (fixtures/rapprochement/*.json).

Positions are written in polar form around the centre for readability.
Expected results are written by hand from the rules (see README.md).
Run: python fixtures/rapprochement/generer.py
"""

from __future__ import annotations

import json
import math
from pathlib import Path

ICI = Path(__file__).resolve().parent
CX, CY = 1000.0, 2000.0
NUM = {"rayon_m": 20.0, "seuil_diam_cm": 7.5, "unite_azimut": "deg", "tolerance_deg": 1.0}


def pos(az, d, dx=0.0, dy=0.0):
    a = math.radians(az)
    return {"x": round(CX + d * math.sin(a) + dx, 6), "y": round(CY + d * math.cos(a) + dy, 6)}


def prov(uuid, tree_id, az, d, dbh, **attrs):
    base = {"uuid": uuid, "plot_id": "P01", "tree_id": tree_id, "dbh_cm": dbh, "statut": "vivant",
            "etat_detection": "provisoire", "methode": "embarque", "h_m": None, "h_source": "aucune",
            "essence_arbre": None, "qualite": None, "notes": None, "photos": None}
    base.update(pos(az, d))
    base.update(attrs)
    return base


def defi(az, d, dbh, dx=0.0, dy=0.0, **attrs):
    base = {"plot_id": "P01", "dbh_cm": dbh, "methode": "classique"}
    base.update(pos(az, d, dx, dy))
    base.update(attrs)
    return base


CAS = [
    {
        "id": "01_nominal",
        "description": "Trois arbres retrouvés à 20 cm près : attributs terrain conservés, position et diamètre mis à jour, numéros inchangés. Sans essence, l'arbre est à compléter.",
        "provisoires": [
            prov("u1", "1", 30, 5, 28, essence_arbre="hetre", notes="fourche"),
            prov("u2", "2", 120, 8, 35, essence_arbre="chene_sessile", qualite="B"),
            prov("u3", "3", 250, 10, 22),
        ],
        "definitifs": [defi(30, 5, 29.5, dx=0.2), defi(120, 8, 34.0, dy=0.2), defi(250, 10, 23.1, dx=-0.15)],
        "attendu": {
            "par_uuid": {
                "u1": {"tree_id": "1", "etat_detection": "definitif", "essence_arbre": "hetre", "notes": "fourche", "dbh_cm": 29.5, "num_provisoire": "1"},
                "u2": {"tree_id": "2", "etat_detection": "definitif", "essence_arbre": "chene_sessile", "qualite": "B", "dbh_cm": 34.0},
                "u3": {"tree_id": "3", "etat_detection": "a_completer", "dbh_cm": 23.1},
            },
            "nouveaux": [],
            "non_retrouves": [],
            "numeros_modifies": [],
        },
    },
    {
        "id": "02_nouveau_et_non_retrouve",
        "description": "Le serveur trouve un arbre de plus (az 60°) et ne retrouve pas u3 : le nouvel arbre prend le n° 2 et est à compléter, u2 passe du n° 2 au n° 3, u3 est à vérifier (V1) avec son essence.",
        "provisoires": [
            prov("u1", "1", 30, 5, 28, essence_arbre="hetre"),
            prov("u2", "2", 120, 8, 35, essence_arbre="chene_sessile"),
            prov("u3", "3", 200, 12, 40, essence_arbre="douglas", notes="cible C2"),
        ],
        "definitifs": [defi(30, 5, 28.4), defi(60, 6, 25.0), defi(120, 8, 35.2)],
        "attendu": {
            "par_uuid": {
                "u1": {"tree_id": "1", "etat_detection": "definitif"},
                "u2": {"tree_id": "3", "etat_detection": "definitif", "num_provisoire": "2"},
                "u3": {"tree_id": "V1", "etat_detection": "a_verifier", "essence_arbre": "douglas", "notes": "cible C2", "num_provisoire": "3", "dbh_cm": 40},
            },
            "nouveaux": [{"az": 60, "d": 6, "tree_id": "2", "etat_detection": "a_completer", "dbh_cm": 25.0}],
            "non_retrouves": ["u3"],
            "numeros_modifies": [["u2", "2", "3"], ["u3", "3", "V1"]],
        },
    },
    {
        "id": "03_affectation_optimale",
        "description": "Deux arbres provisoires à 60 cm l'un de l'autre. Un appariement glouton donnerait D1 à u2 et laisserait u1 sans partenaire (0,9 m > 0,5 m) ; l'affectation optimale apparie u1-D1 et u2-D2.",
        "provisoires": [
            dict(prov("u1", "1", 0, 5, 30, essence_arbre="hetre"), x=CX + 0.0, y=CY + 5.0),
            dict(prov("u2", "2", 0, 5, 30, essence_arbre="sapin_pectine"), x=CX + 0.6, y=CY + 5.0),
        ],
        "definitifs": [
            {"plot_id": "P01", "x": CX + 0.3, "y": CY + 5.0, "dbh_cm": 30.0, "methode": "classique"},
            {"plot_id": "P01", "x": CX + 0.9, "y": CY + 5.0, "dbh_cm": 30.0, "methode": "classique"},
        ],
        "attendu": {
            "par_uuid": {
                "u1": {"tree_id": "1", "etat_detection": "definitif", "essence_arbre": "hetre", "x": CX + 0.3},
                "u2": {"tree_id": "2", "etat_detection": "definitif", "essence_arbre": "sapin_pectine", "x": CX + 0.9},
            },
            "nouveaux": [],
            "non_retrouves": [],
            "numeros_modifies": [],
        },
    },
    {
        "id": "04_hauteur_mesuree_faux_positif_ecart_dbh",
        "description": "Une hauteur mesurée n'est jamais écrasée par le MNH ; un faux positif non retrouvé reste faux positif (F1) ; un écart de diamètre de 20 à 45 cm déclenche une alerte sans casser l'appariement.",
        "provisoires": [
            prov("u1", "1", 45, 6, 30, essence_arbre="hetre", h_m=25.0, h_source="mesuree"),
            prov("u2", "2", 140, 7, 15, etat_detection="faux_positif", notes="souche"),
            prov("u3", "3", 260, 9, 20, essence_arbre="frene"),
        ],
        "definitifs": [defi(45, 6, 31.0, h_m=30.0, h_source="mnh", houppier_id=12), defi(260, 9, 45.0)],
        "attendu": {
            "par_uuid": {
                "u1": {"tree_id": "1", "h_m": 25.0, "h_source": "mesuree", "etat_detection": "definitif"},
                "u2": {"tree_id": "F1", "etat_detection": "faux_positif", "notes": "souche"},
                "u3": {"tree_id": "2", "dbh_cm": 45.0, "etat_detection": "definitif", "essence_arbre": "frene"},
            },
            "nouveaux": [],
            "non_retrouves": ["u2"],
            "numeros_modifies": [["u2", "2", "F1"], ["u3", "3", "2"]],
            "alertes": [{"uuid": "u3", "motif": "ecart_dbh"}],
        },
    },
    {
        "id": "05_sort_de_l_inventaire",
        "description": "u2, mesuré à 6 cm par le serveur, passe sous le seuil : il porte une essence, donc il est gardé à vérifier (V1). Un nouvel arbre de 5 cm sans attribut est simplement ignoré.",
        "provisoires": [
            prov("u1", "1", 10, 4, 25, essence_arbre="charme"),
            prov("u2", "2", 90, 3, 9, essence_arbre="charme"),
        ],
        "definitifs": [defi(10, 4, 25.5), defi(90, 3, 6.0), defi(180, 5, 5.0)],
        "attendu": {
            "par_uuid": {
                "u1": {"tree_id": "1", "etat_detection": "definitif"},
                "u2": {"tree_id": "V1", "etat_detection": "a_verifier", "essence_arbre": "charme", "dbh_cm": 6.0},
            },
            "nouveaux": [],
            "nombre_arbres": 2,
            "non_retrouves": [],
            "numeros_modifies": [["u2", "2", "V1"]],
            "alertes": [{"uuid": "u2", "motif": "sous_seuil"}],
        },
    },
]


def main() -> None:
    for cas in CAS:
        doc = {
            "id": cas["id"],
            "description": cas["description"],
            "parametres": {"tolerance_m": 0.5, **NUM},
            "centre": [CX, CY],
            "convergence_deg": 0.0,
            "provisoires": cas["provisoires"],
            "definitifs": cas["definitifs"],
            "attendu": cas["attendu"],
        }
        (ICI / f"{cas['id']}.json").write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(CAS)} cas écrits dans {ICI}")


if __name__ == "__main__":
    main()
