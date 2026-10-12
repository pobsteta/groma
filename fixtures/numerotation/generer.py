"""Build the shared numbering test cases (fixtures/numerotation/*.json).

Trees are described here in polar form (azimuth in degrees from geographic
north, horizontal distance in metres) for readability, and written as x/y
offsets. The EXPECTED results are written by hand from the rule (see
README.md), never computed by an implementation: that is what makes these
cases a contract shared by Python, Kotlin and R.

Run: python fixtures/numerotation/generer.py
"""

from __future__ import annotations

import json
import math
from pathlib import Path

ICI = Path(__file__).resolve().parent
PARAMS = {"rayon_m": 20.0, "seuil_diam_cm": 7.5, "unite_azimut": "deg", "tolerance_deg": 1.0}


def arbre(id_, az_deg, d_m, dbh_cm):
    a = math.radians(az_deg)
    return {
        "id": id_,
        "x": round(d_m * math.sin(a), 6),
        "y": round(d_m * math.cos(a), 6),
        "dbh_cm": dbh_cm,
    }


CAS = [
    {
        "id": "01_simple",
        "description": "Quatre arbres bien séparés : ordre des azimuts croissants depuis 0°.",
        "arbres": [arbre("A", 30, 5, 30), arbre("B", 120, 5, 30), arbre("C", 210, 5, 30), arbre("D", 300, 5, 30)],
        "attendu": {"numeros": {"A": "1", "B": "2", "C": "3", "D": "4"}, "exclus": {}},
    },
    {
        "id": "02_masque_fin_de_tour",
        "description": "A (az 5°) est caché par B (az 5,5°, plus proche, 60 cm) : B est le n° 1 et A, d'azimut inférieur, est numéroté en fin de tour.",
        "arbres": [arbre("A", 5, 12, 30), arbre("B", 5.5, 4, 60), arbre("C", 90, 6, 30), arbre("D", 200, 8, 30), arbre("E", 300, 10, 30)],
        "attendu": {
            "numeros": {"B": "1", "C": "2", "D": "3", "E": "4", "A": "5"},
            "exclus": {},
            "visibles": ["B", "C", "D", "E"],
        },
    },
    {
        "id": "03_egalite_plus_proche",
        "description": "T2 (az 100°, 12 m) et T3 (az 100,6°, 6 m) sont à égalité d'azimut (écart < 1°) : le plus proche (T3) passe d'abord.",
        "arbres": [arbre("T1", 20, 5, 30), arbre("T2", 100, 12, 30), arbre("T3", 100.6, 6, 30), arbre("T4", 250, 7, 30)],
        "attendu": {"numeros": {"T1": "1", "T3": "2", "T2": "3", "T4": "4"}, "exclus": {}},
    },
    {
        "id": "04_rayon_et_seuil",
        "description": "Bornes incluses : arbre à exactement 20 m et 7,5 cm retenu ; 20,5 m exclu (hors_rayon) ; 7,4 cm exclu (sous_seuil).",
        "arbres": [
            arbre("BORD", 0, 20, 7.5),
            arbre("N45", 45, 10, 25),
            arbre("PETIT", 90, 5, 7.4),
            arbre("S180", 180, 10, 25),
            arbre("LOIN", 270, 20.5, 40),
        ],
        "attendu": {
            "numeros": {"BORD": "1", "N45": "2", "S180": "3"},
            "exclus": {"PETIT": "sous_seuil", "LOIN": "hors_rayon"},
        },
    },
    {
        "id": "05_grades",
        "description": "Placette en grades. La tolérance d'égalité reste 1° (1,11 gr) : G3 à 101 gr et G2 à 100 gr sont à égalité, le plus proche (G3) d'abord. Les azimuts sont rendus en grades.",
        "parametres": {**PARAMS, "unite_azimut": "gr"},
        "arbres": [arbre("G1", 27, 6, 30), arbre("G2", 90, 10, 30), arbre("G3", 90.9, 5, 30), arbre("G4", 270, 8, 30)],
        "attendu": {
            "numeros": {"G1": "1", "G3": "2", "G2": "3", "G4": "4"},
            "exclus": {},
            "azimuts": {"G1": 30.0, "G2": 100.0, "G3": 101.0, "G4": 300.0},
        },
    },
    {
        "id": "06_egalite_numero_un",
        "description": "Deux arbres visibles à égalité pour le n° 1 (az 2,0° à 15 m et 2,7° à 6 m) : le plus proche est le n° 1, l'autre le n° 2.",
        "arbres": [arbre("TA", 2.0, 15, 30), arbre("TB", 2.7, 6, 10), arbre("TC", 180, 8, 30)],
        "attendu": {"numeros": {"TB": "1", "TA": "2", "TC": "3"}, "exclus": {}, "visibles": ["TA", "TB", "TC"]},
    },
    {
        "id": "07_pas_de_bouclage_a_zero",
        "description": "Le n° 1 est le plus petit azimut à partir de 0° : Q (0,3°) est le n° 1, P (359,6°, visible) vient en fin de tour malgré un écart de 0,7° avec Q.",
        "arbres": [arbre("P", 359.6, 10, 30), arbre("Q", 0.3, 5, 10), arbre("R", 90, 5, 30)],
        "attendu": {"numeros": {"Q": "1", "R": "2", "P": "3"}, "exclus": {}, "visibles": ["P", "Q", "R"]},
    },
    {
        "id": "08_vide",
        "description": "Placette sans arbre.",
        "arbres": [],
        "attendu": {"numeros": {}, "exclus": {}},
    },
    {
        "id": "09_gros_bois_masquant",
        "description": "Un gros bois de 120 cm à 3 m (az 50°) masque trois arbres ; H1 (az 10°) est masqué par H0 (az 11°) qui devient le n° 1, H1 est numéroté en dernier.",
        "arbres": [
            arbre("H1", 10, 15, 30),
            arbre("H0", 11, 5, 50),
            arbre("T30", 30, 8, 30),
            arbre("H40", 40, 12, 30),
            arbre("H45", 45, 9, 30),
            arbre("BIG", 50, 3, 120),
            arbre("H55", 55, 10, 30),
        ],
        "attendu": {
            "numeros": {"H0": "1", "T30": "2", "H40": "3", "H45": "4", "BIG": "5", "H55": "6", "H1": "7"},
            "exclus": {},
            "visibles": ["H0", "T30", "BIG"],
        },
    },
    {
        "id": "10_groupe_ancre",
        "description": "Les groupes d'égalité sont ancrés sur leur premier arbre : 50,0° et 50,6° forment un groupe (le plus proche d'abord), 51,3° (à 1,3° de l'ancre) est un groupe à part même s'il est le plus proche.",
        "arbres": [arbre("N1", 10, 5, 30), arbre("K500", 50.0, 9, 30), arbre("K506", 50.6, 5, 30), arbre("K513", 51.3, 3, 30)],
        "attendu": {"numeros": {"N1": "1", "K506": "2", "K500": "3", "K513": "4"}, "exclus": {}},
    },
]


def main() -> None:
    for cas in CAS:
        doc = {
            "id": cas["id"],
            "description": cas["description"],
            "parametres": cas.get("parametres", PARAMS),
            "arbres": cas["arbres"],
            "attendu": cas["attendu"],
        }
        (ICI / f"{cas['id']}.json").write_text(
            json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    print(f"{len(CAS)} cas écrits dans {ICI}")


if __name__ == "__main__":
    main()
