"""Build a Groma project from the sampling plan made in nemetonshiny.

Bridge until nemeton delivers ``creer_projet_inventaire_video()`` (brief
2026-10-12): reads the plan as saved by nemetonshiny's sampling tab
(``<projet>/data/samples.gpkg``, layer ``plots``) or as exported for QField
(layer ``placettes``), sets the Groma plot settings and writes
``inventaire_video.gpkg`` with :func:`groma.gpkg.creer_gpkg`. No raster:
DEM, CHM and crowns stay with nemeton.

    groma-preparer <projet>/data/samples.gpkg --sortie ~/terrain/foret \\
        --rayon 15 --declinaison 2.1
"""

from __future__ import annotations

import argparse
import logging
from pathlib import Path
from typing import Sequence

from .gpkg import couche, creer_gpkg

log = logging.getLogger(__name__)

COUCHES_PLAN = ("placettes", "plots")


def _bornes(nom: str) -> tuple[float, float]:
    col = next(c for c in couche("placettes")["colonnes"] if c["nom"] == nom)
    return col["min"], col["max"]


def lire_plan(chemin: str | Path, nom_couche: str | None = None):
    """Read the plan points: ``placettes`` (QField export) or ``plots`` (samples.gpkg)."""
    import pyogrio

    chemin = Path(chemin)
    couches = [c[0] for c in pyogrio.list_layers(chemin)]
    if nom_couche is None:
        nom_couche = next((c for c in COUCHES_PLAN if c in couches), None)
        if nom_couche is None:
            raise ValueError(f"Aucune couche de plan ({' ou '.join(COUCHES_PLAN)}) dans {chemin.name} : couches {couches}.")
    elif nom_couche not in couches:
        raise ValueError(f"Couche {nom_couche!r} absente de {chemin.name} : couches {couches}.")
    plan = pyogrio.read_dataframe(chemin, layer=nom_couche)
    if "plot_id" not in plan.columns:
        raise ValueError(f"La couche {nom_couche!r} n'a pas de colonne plot_id.")
    if plan.crs is None:
        raise ValueError(f"La couche {nom_couche!r} n'a pas de système de coordonnées.")
    return plan


def preparer_projet(
    plan,
    sortie: str | Path,
    rayon_m: float,
    declinaison_deg: float,
    seuil_diam_cm: float = 7.5,
    unite_azimut: str = "deg",
    plot_ids: Sequence[str] | None = None,
    base_seulement: bool = False,
    ecraser: bool = False,
) -> Path:
    """Write ``<sortie>/inventaire_video.gpkg`` from a plan GeoDataFrame.

    Plan columns (``visit_order``, ``type``, ``ip``, ``wgt``, ``stratum``…)
    are kept as they are; the Groma settings apply to every plot. An
    existing GeoPackage is never replaced unless ``ecraser`` is set: it may
    already hold field data.
    """
    for nom, valeur in (("rayon_m", rayon_m), ("seuil_diam_cm", seuil_diam_cm), ("declinaison_deg", declinaison_deg)):
        lo, hi = _bornes(nom)
        if not lo <= valeur <= hi:
            raise ValueError(f"{nom} = {valeur} hors de [{lo}, {hi}].")
    if unite_azimut not in ("deg", "gr"):
        raise ValueError(f"unite_azimut = {unite_azimut!r} : 'deg' ou 'gr' attendu.")

    p = plan.copy()
    p["plot_id"] = p["plot_id"].astype(str)
    if p["plot_id"].duplicated().any():
        raise ValueError(f"plot_id en double : {sorted(p.loc[p['plot_id'].duplicated(), 'plot_id'])}.")
    if base_seulement:
        if "type" not in p.columns:
            raise ValueError("Pas de colonne type : impossible de ne garder que les placettes Base.")
        p = p[p["type"] == "Base"]
    if plot_ids is not None:
        absentes = sorted(set(plot_ids) - set(p["plot_id"]))
        if absentes:
            raise ValueError(f"Placettes absentes du plan (ou écartées) : {absentes}.")
        p = p[p["plot_id"].isin(plot_ids)]
    if p.empty:
        raise ValueError("Aucune placette retenue.")

    p["rayon_m"] = float(rayon_m)
    p["seuil_diam_cm"] = float(seuil_diam_cm)
    p["unite_azimut"] = unite_azimut
    p["declinaison_deg"] = float(declinaison_deg)
    p["etat_traitement"] = "a_faire"

    sortie = Path(sortie)
    sortie.mkdir(parents=True, exist_ok=True)
    gpkg = sortie / "inventaire_video.gpkg"
    if gpkg.exists() and not ecraser:
        raise FileExistsError(f"{gpkg} existe déjà (données terrain possibles) : choisir un autre dossier ou --ecraser.")
    creer_gpkg(gpkg, p.reset_index(drop=True))
    log.info("%d placettes, rayon %s m : %s", len(p), rayon_m, gpkg)
    return gpkg


def main(argv: Sequence[str] | None = None) -> None:
    a = argparse.ArgumentParser(
        prog="groma-preparer",
        description="Préparer un projet Groma (inventaire_video.gpkg) à partir du plan d'échantillonnage de nemetonshiny.",
    )
    a.add_argument("plan", type=Path, help="samples.gpkg du projet (couche plots) ou GeoPackage du projet QField (couche placettes)")
    a.add_argument("--sortie", type=Path, required=True, help="dossier du projet Groma à copier sur le téléphone")
    a.add_argument("--rayon", type=float, required=True,
                   help="rayon de la placette en mètres ; reprendre celui du plan (15 m par défaut dans nemetonshiny)")
    a.add_argument("--declinaison", type=float, required=True,
                   help="déclinaison magnétique en degrés, positive vers l'est (calculateur IGN ou NOAA)")
    a.add_argument("--seuil", type=float, default=7.5, help="diamètre de précomptage en cm (défaut 7,5)")
    a.add_argument("--unite", choices=("deg", "gr"), default="deg", help="unité des azimuts (défaut deg)")
    a.add_argument("--placettes", help="plot_id à garder, séparés par des virgules (défaut : toutes)")
    a.add_argument("--base-seulement", action="store_true", help="écarter les placettes de remplacement (Over)")
    a.add_argument("--couche", help="couche du plan à lire (défaut : placettes, sinon plots)")
    a.add_argument("--ecraser", action="store_true", help="remplacer un inventaire_video.gpkg existant")
    args = a.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    try:
        gpkg = preparer_projet(
            lire_plan(args.plan, args.couche),
            args.sortie,
            rayon_m=args.rayon,
            declinaison_deg=args.declinaison,
            seuil_diam_cm=args.seuil,
            unite_azimut=args.unite,
            plot_ids=[s.strip() for s in args.placettes.split(",")] if args.placettes else None,
            base_seulement=args.base_seulement,
            ecraser=args.ecraser,
        )
    except (ValueError, FileExistsError) as e:
        a.error(str(e))
    print(gpkg)


if __name__ == "__main__":
    main()
