import json
import math

import geopandas as gpd
import pytest

from groma_server.chaine import ConfigChaine, traiter_paquet
from groma_server.paquet import Paquet, ecrire_paquet


def _evaluer(det, sc):
    pl = sc.placette
    vrais = [a for a in pl.arbres if math.hypot(a.x, a.y) <= 20 - 0.1 and a.dbh_cm >= 7.5]
    trouves, err_d, err_p = 0, [], []
    for a in vrais:
        x, y = pl.position(a)
        c = min(det, key=lambda d: math.hypot(d["x"] - x, d["y"] - y))
        e = math.hypot(c["x"] - x, c["y"] - y)
        if e <= 0.5:
            trouves += 1
            err_d.append(c["dbh_cm"] - a.dbh_cm)
            err_p.append(e)
    fantomes = [d for d in det if min(math.hypot(d["x"] - pl.position(a)[0], d["y"] - pl.position(a)[1]) for a in pl.arbres) > 0.5]
    return trouves / len(vrais), math.sqrt(sum(e * e for e in err_d) / len(err_d)), max(err_p), fantomes


def test_chaine_bout_en_bout(paquet_synthetique, tmp_path):
    chemin, sc = paquet_synthetique
    r = traiter_paquet(chemin, tmp_path, ConfigChaine(reconstructeurs=("profondeur",)))
    taux, rmse_dbh, err_pos, fantomes = _evaluer(r.retenue.detections, sc)
    # Acceptance criteria of the brief (here on synthetic data).
    assert taux >= 0.95
    assert rmse_dbh <= 2.0
    assert err_pos <= 0.5
    assert fantomes == []
    assert r.rapport["duree_traitement_s"] < 15 * 60
    assert r.rapport["georeferencement"]["rotation_deg"] == pytest.approx(sc.rotation_deg, abs=0.5)
    assert all(abs(c["ecart_dbh_cm"]) <= 2.0 for c in r.rapport["cibles"])
    assert r.rapport["sol"]["source"] == "mnt" and r.rapport["sol"]["dispersion_mnt_m"] < 0.05
    assert r.rapport["controle_houppiers"]["alerte"] is False
    assert r.rapport["placette"]["exposition"] in ("W", "NW", "SW")

    # Outputs: GeoPackage of definitive detections + JSON and HTML report.
    g = gpd.read_file(r.chemin_gpkg, layer="detections")
    assert len(g) == len(r.retenue.detections) and g.crs.to_epsg() == 2154
    assert set(g["etat_detection"]) == {"definitif"}
    assert sorted(int(n) for n in g["num_provisoire"]) == list(range(1, len(g) + 1))
    assert json.loads(r.chemin_rapport_json.read_text())["plot_id"] == "P001"
    assert "Placette P001" in r.chemin_rapport_html.read_text()


def test_erreur_de_cap_detectee_par_les_houppiers(paquet_synthetique, tmp_path):
    """A 10° error on the three compass readings: the crowns raise the alert."""
    chemin, _ = paquet_synthetique
    p = Paquet.ouvrir(chemin)
    m = p.manifeste.model_copy(deep=True)
    fichiers = {n: p.lire(n) for n in p._zip.namelist() if n != "manifest.json"}
    p.fermer()
    for c in m.cibles_mesurees:
        c.azimut_mesure = (c.azimut_mesure + 10) % 360
    faux = ecrire_paquet(tmp_path / "faux.groma.zip", m, fichiers)
    r = traiter_paquet(faux, tmp_path / "sortie", ConfigChaine(reconstructeurs=("profondeur",)))
    assert r.rapport["controle_houppiers"]["alerte"] is True
    assert r.rapport["verdict"] == "a_verifier"
    assert any("Orientation" in a for a in r.rapport["alertes"])


def test_sans_c0_erreur_explicite(paquet_synthetique, tmp_path):
    chemin, _ = paquet_synthetique
    p = Paquet.ouvrir(chemin)
    m = p.manifeste.model_copy(deep=True)
    fichiers = {n: p.lire(n) for n in p._zip.namelist() if n != "manifest.json"}
    p.fermer()
    m.detections_cibles = [d for d in m.detections_cibles if d.id_cible != "C0"]
    faux = ecrire_paquet(tmp_path / "sans_c0.groma.zip", m, fichiers)
    with pytest.raises(ValueError, match="C0"):
        traiter_paquet(faux, tmp_path / "sortie", ConfigChaine(reconstructeurs=("profondeur",)))
