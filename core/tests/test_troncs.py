import math

import numpy as np
import pytest

from groma.synthetique import nuage_synthetique, placette_synthetique
from groma.troncs import dbscan, detecter_troncs, extraire_tranche


def test_dbscan_deux_groupes():
    a = np.random.default_rng(0).normal(0, 0.01, (30, 2))
    b = a + 1.0
    lab = dbscan(np.vstack([a, b, [[5, 5]]]), 0.05, 5)
    assert len(set(lab[:30])) == 1 and len(set(lab[30:60])) == 1
    assert lab[0] != lab[30] and lab[-1] == -1


def test_detection_placette_synthetique():
    p = placette_synthetique()
    nuage = nuage_synthetique(p)
    sol = lambda x, y: p.z_sol(x + p.centre[0], y + p.centre[1])
    tranche = extraire_tranche(nuage, sol)
    troncs = detecter_troncs(tranche)
    vrais = {a.id: a for a in p.arbres}
    trouves = 0
    erreurs = []
    for a in vrais.values():
        proches = [t for t in troncs if math.hypot(t.x - a.x, t.y - a.y) < 0.3 + a.dbh_cm / 400]
        if proches:
            trouves += 1
            erreurs.append(proches[0].dbh_cm - a.dbh_cm)
    assert trouves / len(vrais) >= 0.95
    rmse = math.sqrt(sum(e * e for e in erreurs) / len(erreurs))
    assert rmse <= 2.0, f"RMSE diamètre {rmse:.2f} cm"
    # No phantom trunks.
    assert len(troncs) <= len(vrais) + 1


def test_gros_bois_arc_partiel():
    p = placette_synthetique()
    nuage = nuage_synthetique(p)
    sol = lambda x, y: p.z_sol(x + p.centre[0], y + p.centre[1])
    troncs = detecter_troncs(extraire_tranche(nuage, sol))
    gros = [t for t in troncs if math.hypot(t.x - 6.4, t.y - 6.4) < 0.5]
    assert len(gros) == 1
    assert gros[0].dbh_cm == pytest.approx(210, abs=4)
