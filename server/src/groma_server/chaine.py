"""Definitive processing chain of one plot package (server, part B)."""

from __future__ import annotations

import json
import logging
import math
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
from scipy.optimize import linear_sum_assignment

from groma.georeferencement import CibleMesuree, Georeferencement, georeferencer
from groma.houppiers import Houppier, controler_coherence, houppiers_sans_tronc, recaler_sur_apex
from groma.mnt import Raster, lire_geotiff, pente_exposition, recouvrement_pct
from groma.placette import ParametresPlacette, construire_detections
from groma.troncs import detecter_troncs, extraire_tranche

from .cibles import CibleTriangulee, detecter_aruco, trianguler
from .paquet import Paquet
from .reconstruction import REGISTRE, Nuage
from .segmentation import SansSegmentation, SegmenteurTroncs
from .sol import ModeleSol, points_sol, sol_depuis_mnt, sol_reconstruit

log = logging.getLogger(__name__)

PRIORITE = {"classique": 0, "ia": 1, "embarque": 2}


@dataclass
class ConfigChaine:
    reconstructeurs: tuple[str, ...] = ("colmap", "vggt", "profondeur")
    eps_dbscan_m: float = 0.05
    seuil_ransac_m: float = 0.015
    couverture_min_deg: float = 40.0
    tolerance_apex_m: float = 2.0
    seuil_alerte_cap_deg: float = 5.0
    tolerance_calage_m: float = 0.3
    redetecter_cibles: bool = True


@dataclass
class ResultatReconstruction:
    nom: str
    methode: str
    detections: list[dict]
    sol: ModeleSol
    n_points: int
    duree_s: float
    controle_cibles: list[dict] = field(default_factory=list)


@dataclass
class ResultatChaine:
    plot_id: str
    retenue: ResultatReconstruction
    resultats: list[ResultatReconstruction]
    rapport: dict
    chemin_gpkg: Path
    chemin_rapport_json: Path
    chemin_rapport_html: Path


def _charger_lidar(paquet: Paquet, dossier: Path):
    l = paquet.manifeste.lidar
    mnt = lire_geotiff(paquet.extraire(l.mnt, dossier)) if paquet.existe(l.mnt) else None
    mnh = lire_geotiff(paquet.extraire(l.mnh, dossier)) if paquet.existe(l.mnh) else None
    hs: list[Houppier] = []
    if paquet.existe(l.houppiers):
        from shapely.geometry import shape

        fc = json.loads(paquet.lire(l.houppiers))
        for f in fc["features"]:
            p = f["properties"]
            hs.append(Houppier(int(p["houppier_id"]), shape(f["geometry"]), p.get("h_max"), p.get("surface_m2")))
    return mnt, mnh, hs


def _georeferencer(paquet: Paquet, cibles: dict[str, CibleTriangulee]) -> Georeferencement:
    m = paquet.manifeste
    if "C0" not in cibles:
        raise ValueError("Repère central C0 non retrouvé dans la prise de vue : géoréférencement impossible.")
    mesurees = [CibleMesuree(c.id_cible, c.azimut_mesure, c.distance_mesuree_m, c.dbh_mesure_cm) for c in m.cibles_mesurees]
    return georeferencer(
        cibles["C0"].position[:2],
        {k: v.position[:2] for k, v in cibles.items() if k != "C0"},
        mesurees,
        (m.centre.x, m.centre.y),
        unite=m.placette.unite_azimut,  # type: ignore[arg-type]
        declinaison_deg=m.placette.declinaison_deg,
        convergence_deg=m.placette.convergence_deg,
    )


def _traiter_nuage(nuage: Nuage, paquet: Paquet, geo: Georeferencement, mnt: Raster | None,
                   houppiers: list[Houppier], params: ParametresPlacette, cfg: ConfigChaine,
                   cibles: dict[str, CibleTriangulee], duree: float) -> ResultatReconstruction:
    sol = sol_reconstruit(nuage.points)
    if mnt is not None:
        try:
            sol = sol_depuis_mnt(nuage.points, mnt, geo)
        except ValueError as e:
            log.warning("MNT inutilisable (%s) : sol reconstruit.", e)
    tranche = extraire_tranche(nuage.points, sol.hauteur)
    troncs = detecter_troncs(tranche, eps_m=cfg.eps_dbscan_m, seuil_ransac_m=cfg.seuil_ransac_m,
                             couverture_min_deg=cfg.couverture_min_deg)
    xy = geo.vers_grille(np.array([[t.x, t.y] for t in troncs])) if troncs else np.zeros((0, 2))
    lignes = [{"x": float(p[0]), "y": float(p[1]), "dbh_cm": t.dbh_cm, "score": t.score} for p, t in zip(xy, troncs)]
    det = construire_detections(lignes, params, houppiers, methode=nuage.methode, etat="definitif")

    # Target trees: the trunk whose circle passes through the target.
    controle = []
    mesures = {c.id_cible: c for c in paquet.manifeste.cibles_mesurees}
    for k, c in cibles.items():
        if k == "C0" or k not in mesures:
            continue
        g = geo.vers_grille(c.position[None, :2])[0]
        meilleur = None
        for t in det:
            ecart = abs(math.hypot(t["x"] - g[0], t["y"] - g[1]) - t["dbh_cm"] / 200.0)
            if ecart < 0.15 and (meilleur is None or ecart < meilleur[0]):
                meilleur = (ecart, t)
        m = mesures[k]
        controle.append({
            "id_cible": k,
            "tree_id": meilleur[1]["num_provisoire"] if meilleur else None,
            "dbh_mesure_cm": m.dbh_mesure_cm,
            "dbh_estime_cm": meilleur[1]["dbh_cm"] if meilleur else None,
            "ecart_dbh_cm": round(meilleur[1]["dbh_cm"] - m.dbh_mesure_cm, 1) if meilleur and m.dbh_mesure_cm else None,
        })
    return ResultatReconstruction(nuage.nom, nuage.methode, det, sol, int(len(nuage.points)), duree, controle)


def _comparer(a: list[dict], b: list[dict], tolerance_m: float = 0.5) -> dict:
    if not a or not b:
        return {"n_apparies": 0}
    cout = np.array([[math.hypot(p["x"] - q["x"], p["y"] - q["y"]) for q in b] for p in a])
    i, j = linear_sum_assignment(np.where(cout <= tolerance_m, cout, 1e6))
    ok = cout[i, j] <= tolerance_m
    i, j = i[ok], j[ok]
    ddbh = np.array([a[x]["dbh_cm"] - b[y]["dbh_cm"] for x, y in zip(i, j)])
    return {
        "n_apparies": int(ok.sum()),
        "n_seulement_a": len(a) - int(ok.sum()),
        "n_seulement_b": len(b) - int(ok.sum()),
        "ecart_position_moyen_m": round(float(cout[i, j].mean()), 3) if ok.any() else None,
        "ecart_dbh_moyen_cm": round(float(ddbh.mean()), 2) if ok.any() else None,
        "ecart_dbh_rmse_cm": round(float(np.sqrt((ddbh**2).mean())), 2) if ok.any() else None,
    }


def _choisir(resultats: list[ResultatReconstruction]) -> ResultatReconstruction:
    def cle(r: ResultatReconstruction):
        ecarts = [abs(c["ecart_dbh_cm"]) for c in r.controle_cibles if c["ecart_dbh_cm"] is not None]
        manquants = sum(1 for c in r.controle_cibles if c["ecart_dbh_cm"] is None)
        return (manquants, float(np.mean(ecarts)) if ecarts else math.inf, PRIORITE.get(r.methode, 9))

    return min(resultats, key=cle)


def traiter_paquet(chemin: str | Path, dossier_sortie: str | Path, cfg: ConfigChaine = ConfigChaine(),
                   segmenteur: SegmenteurTroncs | None = None) -> ResultatChaine:
    t0 = time.monotonic()
    dossier_sortie = Path(dossier_sortie)
    dossier_sortie.mkdir(parents=True, exist_ok=True)
    paquet = Paquet.ouvrir(chemin)
    m = paquet.manifeste
    log.info("Placette %s : %d images", m.plot_id, len(m.images))
    try:
        with tempfile.TemporaryDirectory(prefix=f"groma_{m.plot_id}_") as tmp:
            travail = Path(tmp)
            mnt, mnh, houppiers = _charger_lidar(paquet, travail)
            params = ParametresPlacette(m.plot_id, (m.centre.x, m.centre.y), m.placette.rayon_m, m.placette.seuil_diam_cm,
                                        m.placette.unite_azimut, m.placette.declinaison_deg, m.placette.convergence_deg)

            # 1. Segmentation, 3. targets (independent of the reconstruction).
            segmenteur = segmenteur or SansSegmentation()
            masques = segmenteur.masques(paquet) if segmenteur.disponible() else {}
            detections_cibles = list(m.detections_cibles)
            if cfg.redetecter_cibles:
                for img in m.images:
                    bgr = paquet.image_bgr(img)
                    if bgr is not None:
                        detections_cibles += detecter_aruco(bgr, img.id)
            cibles = trianguler(paquet, detections_cibles)
            geo = _georeferencer(paquet, cibles)

            # 2. Reconstructions, in parallel behind the same interface.
            recs = [REGISTRE[n]() for n in cfg.reconstructeurs if n in REGISTRE]
            recs = [r for r in recs if r.disponible()]
            if not recs:
                raise RuntimeError("Aucune méthode de reconstruction disponible.")

            def lancer(r):
                debut = time.monotonic()
                (travail / r.nom).mkdir()
                return r.reconstruire(paquet, travail / r.nom, masques), time.monotonic() - debut

            with ThreadPoolExecutor(max_workers=len(recs)) as ex:
                futurs = {r.nom: ex.submit(lancer, r) for r in recs}
            nuages, erreurs = [], {}
            for nom, f in futurs.items():
                try:
                    nuages.append(f.result())
                except Exception as e:  # one backend failing must not lose the others
                    log.exception("Reconstruction %s en échec", nom)
                    erreurs[nom] = str(e)
            if not nuages:
                raise RuntimeError(f"Toutes les reconstructions ont échoué : {erreurs}")

            # 4-7. Ground, slice, trunks, georeferencing, numbering, heights.
            resultats = [_traiter_nuage(n, paquet, geo, mnt, houppiers, params, cfg, cibles, d) for n, d in nuages]
            retenue = _choisir(resultats)

            # 8. Quality report.
            rapport = construire_rapport(paquet, geo, cibles, retenue, resultats, mnt, mnh, houppiers, params, cfg, erreurs,
                                         nuages, time.monotonic() - t0)
    finally:
        paquet.fermer()

    from .rapport import ecrire_html
    from .sortie import ecrire_gpkg_definitif

    gpkg = ecrire_gpkg_definitif(dossier_sortie / f"{m.plot_id}_definitif.gpkg", retenue.detections)
    rj = dossier_sortie / f"{m.plot_id}_rapport.json"
    rj.write_text(json.dumps(rapport, ensure_ascii=False, indent=1, default=_json), encoding="utf-8")
    rh = ecrire_html(dossier_sortie / f"{m.plot_id}_rapport.html", rapport)
    log.info("Placette %s traitée en %.0f s : %d arbres (%s)", m.plot_id, time.monotonic() - t0, len(retenue.detections), retenue.nom)
    return ResultatChaine(m.plot_id, retenue, resultats, rapport, gpkg, rj, rh)


def _json(o):
    if isinstance(o, (np.floating, np.integer)):
        return o.item()
    if isinstance(o, np.ndarray):
        return o.tolist()
    raise TypeError(type(o))


def construire_rapport(paquet, geo, cibles, retenue, resultats, mnt, mnh, houppiers, params, cfg, erreurs, nuages, duree):
    m = paquet.manifeste
    alertes = []
    troncs = np.array([[d["x"], d["y"]] for d in retenue.detections]) if retenue.detections else np.zeros((0, 2))

    cap = None
    if houppiers and len(troncs) >= 3:
        r = recaler_sur_apex(troncs, np.array([h.apex for h in houppiers]), params.centre,
                             tolerance_m=cfg.tolerance_apex_m, seuil_alerte_deg=cfg.seuil_alerte_cap_deg)
        if r:
            cap = {"rotation_deg": round(r.rotation_deg, 2), "n_apparies": r.n_apparies, "rmse_m": round(r.rmse_m, 2), "alerte": r.alerte}
            if r.alerte:
                alertes.append(f"Orientation : la carte des troncs est tournée de {r.rotation_deg:.1f}° par rapport aux houppiers LiDAR. Vérifier les azimuts des cibles et la déclinaison.")
    oublis = houppiers_sans_tronc(houppiers, troncs, params.centre, params.rayon_m) if houppiers else []
    if oublis:
        alertes.append(f"{len(oublis)} houppier(s) dominant(s) sans tronc détecté dessous (oublis possibles, ou LiDAR antérieur à une coupe).")

    incoh = controler_coherence([d["dbh_cm"] for d in retenue.detections], [d["h_m"] for d in retenue.detections],
                                [_surface(houppiers, d["houppier_id"]) for d in retenue.detections])
    for i in incoh:
        alertes.append(f"Arbre {retenue.detections[i.indice]['num_provisoire']} : {i.motif} = {i.valeur}")
    grands = [d for d in retenue.detections if d["dbh_cm"] > 200]
    for d in grands:
        alertes.append(f"Arbre {d['num_provisoire']} : diamètre {d['dbh_cm']} cm (> 200 cm), à confirmer.")

    for c in retenue.controle_cibles:
        if c["ecart_dbh_cm"] is None:
            alertes.append(f"Cible {c['id_cible']} : aucun tronc estimé sous la cible.")
        elif abs(c["ecart_dbh_cm"]) > 2.0:
            alertes.append(f"Cible {c['id_cible']} : écart de diamètre {c['ecart_dbh_cm']:+.1f} cm avec le ruban.")
    if m.centre.qualite_gnss and m.centre.qualite_gnss != "fix":
        alertes.append(f"Centre GNSS en {m.centre.qualite_gnss} (pas de fix RTK) : positions absolues moins sûres.")
    if geo.rmse_azimut_deg > 2.0:
        alertes.append(f"Résidu d'orientation élevé ({geo.rmse_azimut_deg:.1f}°) entre les cibles.")
    if retenue.sol.source == "mnt" and retenue.sol.dispersion_mnt_m is not None and retenue.sol.dispersion_mnt_m > cfg.tolerance_calage_m:
        alertes.append(f"Sol reconstruit et MNT divergent (dispersion {retenue.sol.dispersion_mnt_m:.2f} m).")

    images = m.images
    fermeture = None
    if m.suivi.boucle_fermee and len(images) > 1:
        p0, p1 = np.asarray(images[0].pose).reshape(4, 4)[:3, 3], np.asarray(images[-1].pose).reshape(4, 4)[:3, 3]
        fermeture = round(float(np.linalg.norm(p1 - p0)), 3)

    placette = {}
    if mnt is not None:
        try:
            placette["pente_pct"], placette["exposition"] = pente_exposition(mnt, *params.centre, params.rayon_m, params.convergence_deg)
        except ValueError:
            pass
    if mnh is not None:
        try:
            placette["recouvrement_pct"] = recouvrement_pct(mnh, *params.centre, params.rayon_m)
        except ValueError:
            pass

    comparaisons = {}
    for i, a in enumerate(resultats):
        for b in resultats[i + 1 :]:
            comparaisons[f"{a.nom}_vs_{b.nom}"] = _comparer(a.detections, b.detections)

    return {
        "plot_id": m.plot_id,
        "verdict": "a_verifier" if alertes else "conforme",
        "duree_traitement_s": round(duree, 1),
        "reconstruction_retenue": retenue.nom,
        "methode": retenue.methode,
        "nombre_arbres": len(retenue.detections),
        "centre": {"x": m.centre.x, "y": m.centre.y, "qualite_gnss": m.centre.qualite_gnss, "precision_h_m": m.centre.precision_h_m},
        "georeferencement": {
            "rotation_deg": round(geo.rotation_deg, 3),
            "echelle": round(geo.echelle, 4),
            "echelle_appliquee": geo.echelle_appliquee,
            "rmse_azimut_deg": round(geo.rmse_azimut_deg, 3),
            "residus_azimut_deg": {k: round(v, 3) for k, v in geo.residus_azimut_deg.items()},
            "residus_distance_m": {k: round(v, 3) for k, v in geo.residus_distance_m.items()},
            "residus_position_m": {k: round(v, 3) for k, v in geo.residus_position_m.items()},
            "cibles_triangulees": {k: {"n_vues": c.n_vues, "rmse_m": round(c.rmse_m, 3)} for k, c in cibles.items()},
        },
        "cibles": retenue.controle_cibles,
        "sol": {"source": retenue.sol.source, "decalage_mnt_m": retenue.sol.decalage_mnt_m, "dispersion_mnt_m": retenue.sol.dispersion_mnt_m},
        "controle_houppiers": cap,
        "oublis_probables": oublis,
        "fermeture_boucle_m": fermeture,
        "placette": placette,
        "reconstructions": [
            {"nom": r.nom, "methode": r.methode, "n_points": r.n_points, "n_arbres": len(r.detections), "duree_s": round(r.duree_s, 1), "cibles": r.controle_cibles}
            for r in resultats
        ],
        "reconstructions_en_echec": erreurs,
        "comparaison_reconstructions": comparaisons,
        "alertes": alertes,
    }


def _surface(houppiers: list[Houppier], hid):
    if hid is None:
        return None
    for h in houppiers:
        if h.houppier_id == hid:
            return h.surface_m2
    return None
