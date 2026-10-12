import json
import math
from pathlib import Path

import numpy as np
import pytest
from shapely import wkt

from groma.houppiers import (
    Houppier,
    affecter_hauteurs,
    controler_coherence,
    houppiers_sans_tronc,
    recaler_sur_apex,
)
from groma.synthetique import houppiers_synthetiques, placette_synthetique

CAS = Path(__file__).resolve().parents[2] / "fixtures" / "hauteurs_houppiers" / "cas.json"


def test_cas_communs_hauteurs():
    cas = json.loads(CAS.read_text(encoding="utf-8"))
    hs = [Houppier(h["houppier_id"], wkt.loads(h["wkt"]), h["h_max"]) for h in cas["houppiers"]]
    res = affecter_hauteurs(cas["arbres"], hs)
    for a, r in zip(cas["arbres"], res):
        att = a["attendu"]
        assert (r.h_m, r.h_source, r.houppier_id) == (att["h_m"], att["h_source"], att["houppier_id"]), a["id"]


def _troncs_apex(rotation=0.0):
    p = placette_synthetique()
    hs = houppiers_synthetiques(p)
    t = math.radians(rotation)
    troncs = []
    for a in p.arbres:
        # Trunk map rotated clockwise by `rotation` around the centre (heading error).
        x = a.x * math.cos(t) + a.y * math.sin(t)
        y = -a.x * math.sin(t) + a.y * math.cos(t)
        troncs.append((p.centre[0] + x, p.centre[1] + y))
    return p, hs, np.array(troncs), np.array([h.apex for h in hs])


def test_recalage_sans_erreur():
    p, _, troncs, apex = _troncs_apex(0.0)
    r = recaler_sur_apex(troncs, apex, p.centre)
    assert r is not None and abs(r.rotation_deg) < 2.0 and not r.alerte


def test_recalage_detecte_erreur_de_cap():
    p, _, troncs, apex = _troncs_apex(12.0)
    r = recaler_sur_apex(troncs, apex, p.centre)
    assert r is not None and r.alerte
    assert r.rotation_deg == pytest.approx(-12.0, abs=2.0)


def test_houppier_sans_tronc():
    p, hs, troncs, _ = _troncs_apex()
    # Remove the tallest tree inside the plot from the detections.
    dedans = [i for i, a in enumerate(p.arbres) if math.hypot(a.x, a.y) < 15]
    i_max = max(dedans, key=lambda i: p.arbres[i].h_m)
    restants = np.delete(troncs, i_max, axis=0)
    alertes = houppiers_sans_tronc(hs, restants, p.centre, 20.0)
    assert hs[i_max].houppier_id in alertes
    assert houppiers_sans_tronc(hs, troncs, p.centre, 20.0) == []


def test_coherence():
    out = controler_coherence([30, 30, 10], [25, 60, 12], [30, 30, 2000])
    motifs = {(o.indice, o.motif) for o in out}
    assert (1, "elancement") in motifs
    assert (2, "houppier_diametre") in motifs
    assert (0, "elancement") not in motifs
