import datetime as dt
import importlib.util
from pathlib import Path

_spec = importlib.util.spec_from_file_location("version_calendaire", Path(__file__).with_name("version_calendaire.py"))
vc = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(vc)

NOV = dt.date(2026, 11, 11)


def test_premiere_version_calendaire_apres_semver():
    assert vc.version_suivante(["v0.13.0", "v0.14.0"], NOV) == "2026.11.1"


def test_compteur_du_mois_sans_zero_initial():
    tags = ["v0.14.0", "v2026.11.1", "v2026.11.2", "v2026.11.10", "v2026.10.7"]
    assert vc.version_suivante(tags, NOV) == "2026.11.11"
    assert vc.version_suivante(tags, dt.date(2026, 12, 1)) == "2026.12.1"
    assert vc.version_suivante(tags, dt.date(2027, 1, 3)) == "2027.1.1"


def test_tag_d_un_autre_mois_ne_compte_pas():
    # v2026.1.x must not be read as a January prefix of v2026.11.x, nor the reverse
    assert vc.version_suivante(["v2026.1.5"], NOV) == "2026.11.1"
    assert vc.version_suivante(["v2026.11.4"], dt.date(2026, 1, 2)) == "2026.1.1"


def test_commits_publiables():
    assert vc.publiable("feat(server): planche A4\n\ncorps")
    assert vc.publiable("docs: x\n\nfix: y")
    assert vc.publiable("perf: plus vite")
    assert vc.publiable("refactor!: API changée")
    assert vc.publiable("chore: x\n\nBREAKING CHANGE: format du paquet")


def test_commits_non_publiables():
    assert not vc.publiable("docs(pilotage): cycle 7\n\nbuild: Bump x\nci: y\nchore: publier la version 0.15.0")
    assert not vc.publiable("")
    assert not vc.publiable("refactor(carte): terrain-carte\n\nUn feat: dans le corps ne compte pas en milieu de ligne")
