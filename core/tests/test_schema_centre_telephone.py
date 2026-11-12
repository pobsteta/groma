"""Mission 024: the phone's GNSS centre. Schema side (written before mission 025).

Expected: domain `qualite_gnss` gains "telephone" (kept: "fix", "float"), schema >= 1.6.0,
and the `centres_mesures.qualite_gnss` column still points at that domain.
"""
from groma.gpkg import charger_schema


def _version(s):
    return tuple(int(n) for n in s["version"].split("."))


def test_telephone_dans_le_domaine_qualite_gnss():
    s = charger_schema()
    dom = s["domaines"]["qualite_gnss"]
    assert "telephone" in dom
    assert {"fix", "float"} <= set(dom)
    assert dom.count("telephone") == 1


def test_version_au_moins_1_6_0():
    assert _version(charger_schema()) >= (1, 6, 0)


def test_colonne_qualite_gnss_des_centres_utilise_le_domaine():
    s = charger_schema()
    centres = next(c for c in s["couches"] if c["nom"] == "centres_mesures")
    col = next(c for c in centres["colonnes"] if c["nom"] == "qualite_gnss")
    assert col["domaine"] == "qualite_gnss"
    assert {c["nom"] for c in centres["colonnes"]} >= {"precision_h_m", "ecart_prevu_m"}
