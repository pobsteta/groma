"""HTML rendering of the quality report (French, self-contained)."""

from __future__ import annotations

import html
from pathlib import Path


def _ligne(*cellules) -> str:
    return "<tr>" + "".join(f"<td>{html.escape('' if c is None else str(c))}</td>" for c in cellules) + "</tr>"


def ecrire_html(chemin: Path, r: dict) -> Path:
    g = r["georeferencement"]
    verdict = "Conforme" if r["verdict"] == "conforme" else "À vérifier"
    couleur = "#2e7d32" if r["verdict"] == "conforme" else "#c62828"
    alertes = "".join(f"<li>{html.escape(a)}</li>" for a in r["alertes"]) or "<li>Aucune alerte.</li>"
    cibles = "".join(_ligne(c["id_cible"], c["tree_id"], c["dbh_mesure_cm"], c["dbh_estime_cm"], c["ecart_dbh_cm"]) for c in r["cibles"])
    residus = "".join(
        _ligne(k, g["residus_azimut_deg"].get(k), g["residus_distance_m"].get(k), g["residus_position_m"].get(k))
        for k in sorted(g["residus_azimut_deg"])
    )
    recs = "".join(_ligne(x["nom"], x["methode"], x["n_points"], x["n_arbres"], x["duree_s"]) for x in r["reconstructions"])
    comps = "".join(
        _ligne(k, v.get("n_apparies"), v.get("ecart_position_moyen_m"), v.get("ecart_dbh_moyen_cm"), v.get("ecart_dbh_rmse_cm"))
        for k, v in r["comparaison_reconstructions"].items()
    )
    cap = r["controle_houppiers"]
    page = f"""<!doctype html>
<html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Groma – placette {html.escape(r['plot_id'])}</title>
<style>
body{{font-family:system-ui,sans-serif;max-width:60rem;margin:1rem auto;padding:0 1rem;color:#1b1b1b;background:#fff}}
table{{border-collapse:collapse;width:100%;margin:.5rem 0 1.5rem}}td,th{{border:1px solid #ccc;padding:.3rem .5rem;text-align:left}}
th{{background:#f2f2f2}}.verdict{{color:{couleur};font-weight:700}}
</style></head><body>
<h1>Placette {html.escape(r['plot_id'])}</h1>
<p class="verdict">{verdict}</p>
<p>{r['nombre_arbres']} arbres · reconstruction retenue : {html.escape(r['reconstruction_retenue'])} ({html.escape(r['methode'])}) · traitement {r['duree_traitement_s']} s</p>
<h2>Alertes</h2><ul>{alertes}</ul>
<h2>Arbres cibles</h2>
<table><tr><th>Cible</th><th>N°</th><th>Diamètre ruban (cm)</th><th>Diamètre estimé (cm)</th><th>Écart (cm)</th></tr>{cibles}</table>
<h2>Géoréférencement</h2>
<p>Rotation {g['rotation_deg']}° · échelle {g['echelle']} ({'appliquée' if g['echelle_appliquee'] else 'non appliquée'}) · résidu d'orientation {g['rmse_azimut_deg']}°</p>
<table><tr><th>Cible</th><th>Résidu azimut (°)</th><th>Résidu distance (m)</th><th>Résidu position (m)</th></tr>{residus}</table>
<h2>Contrôles LiDAR</h2>
<p>Sol : {html.escape(r['sol']['source'])}{f" (décalage {r['sol']['decalage_mnt_m']:.2f} m, dispersion {r['sol']['dispersion_mnt_m']:.2f} m)" if r['sol']['decalage_mnt_m'] is not None else ''}</p>
<p>Recalage sur les houppiers : {f"rotation {cap['rotation_deg']}°, {cap['n_apparies']} apex appariés" if cap else 'non disponible'}</p>
<p>Oublis probables (houppiers dominants sans tronc) : {', '.join(map(str, r['oublis_probables'])) or 'aucun'}</p>
<p>Fermeture de boucle : {r['fermeture_boucle_m'] if r['fermeture_boucle_m'] is not None else 'non mesurée'} m</p>
<h2>Reconstructions</h2>
<table><tr><th>Méthode</th><th>Type</th><th>Points</th><th>Arbres</th><th>Durée (s)</th></tr>{recs}</table>
<table><tr><th>Comparaison</th><th>Appariés</th><th>Écart position (m)</th><th>Écart diamètre moyen (cm)</th><th>RMSE diamètre (cm)</th></tr>{comps}</table>
</body></html>
"""
    chemin.write_text(page, encoding="utf-8")
    return chemin
