"""Build a Groma project from the sampling plan made in nemetonshiny.

Bridge until nemeton delivers ``creer_projet_inventaire_video()`` (brief
2026-10-12): reads the plan as saved by nemetonshiny's sampling tab
(``<projet>/data/samples.gpkg``, layer ``plots``) or as exported for QField
(layer ``placettes``), sets the Groma plot settings and writes
``inventaire_video.gpkg`` with :func:`groma.gpkg.creer_gpkg`. No raster:
DEM, CHM and crowns stay with nemeton.

Inside a nemetonshiny project, ``metadata.json`` and ``data/parcels.gpkg``
are read (never written) to fill the ``projet`` table shown by the phone's
project list (brief 2026-10-13), and the output folder defaults to
``./<projet_id>``, the name the folder must keep on the phone.

    groma-preparer ~/.local/share/nemeton/projects/<id>/data/samples.gpkg \\
        --rayon 15 --declinaison 2.1
"""

from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path
from typing import Sequence

from . import __version__
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


def lire_metadonnees(chemin_plan: str | Path) -> dict:
    """``projet`` row from the nemetonshiny project holding the plan.

    Empty when the plan is not ``<projet>/data/samples.gpkg`` next to a
    ``metadata.json`` (QField export, copied file). Areas and communes come
    from ``data/parcels.gpkg`` when it can be read; they are optional.
    """
    chemin = Path(chemin_plan).resolve()
    racine = chemin.parent.parent
    meta = racine / "metadata.json"
    if chemin.parent.name != "data" or not meta.is_file():
        return {}
    m = json.loads(meta.read_text("utf-8"))
    projet = {
        "projet_id": m.get("id") or racine.name,
        "projet_nom": m.get("name") or None,
        "description": m.get("description") or None,
        "proprietaire": m.get("owner") or None,
        "nb_parcelles": m.get("parcels_count"),
        "nb_ug": m.get("ug_count"),
        "plan_genere_le": m.get("samples_generated_at"),
    }
    try:
        projet.update(_parcelles(chemin.parent))
    except Exception as e:  # areas are a convenience, never a reason to fail
        log.warning("Surfaces et communes non lues (%s) : %s", chemin.parent / "parcels.gpkg", e)
    return projet


def _parcelles(data: Path) -> dict:
    import pyogrio

    if not (data / "parcels.gpkg").is_file():
        return {}
    p = pyogrio.read_dataframe(data / "parcels.gpkg")
    out = {"surface_zone_ha": round(float(p.to_crs(2154).area.sum()) / 1e4, 2)}
    if "contenance" in p.columns:
        out["surface_cadastre_ha"] = round(float(p["contenance"].sum()) / 1e4, 2)
    if "commune" in p.columns:
        codes = sorted(set(p["commune"].dropna().astype(str)))
        noms = {}
        if (data / "commune.gpkg").is_file():
            c = pyogrio.read_dataframe(data / "commune.gpkg", read_geometry=False)
            if {"code", "nom"} <= set(c.columns):
                noms = dict(zip(c["code"].astype(str), c["nom"]))
        out["communes"] = ", ".join(f"{noms[k]} ({k})" if k in noms else k for k in codes) or None
    return out


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
    projet: dict | None = None,
) -> Path:
    """Write ``<sortie>/inventaire_video.gpkg`` from a plan GeoDataFrame.

    Plan columns (``visit_order``, ``type``, ``ip``, ``wgt``, ``stratum``…)
    are kept as they are; the Groma settings apply to every plot. An
    existing GeoPackage is never replaced unless ``ecraser`` is set: it may
    already hold field data.

    ``projet``: row of the ``projet`` table (see :func:`lire_metadonnees`);
    ``projet_id`` and ``projet_nom`` default to the output folder name.
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
    ligne = {"projet_id": sortie.name, "projet_nom": sortie.name} | {k: v for k, v in (projet or {}).items() if v is not None}
    ligne["prepare_par"] = f"groma-preparer {__version__}"
    if ligne["projet_id"] != sortie.name:
        log.warning("Le dossier %s ne porte pas l'identifiant du projet (%s) : le renommer avant de le copier sur le téléphone.",
                    sortie.name, ligne["projet_id"])
    gpkg = sortie / "inventaire_video.gpkg"
    if gpkg.exists() and not ecraser:
        raise FileExistsError(f"{gpkg} existe déjà (données terrain possibles) : choisir un autre dossier ou --ecraser.")
    sortie.mkdir(parents=True, exist_ok=True)
    creer_gpkg(gpkg, p.reset_index(drop=True), projet=ligne)
    log.info("%d placettes, rayon %s m : %s", len(p), rayon_m, gpkg)
    return gpkg


def main(argv: Sequence[str] | None = None) -> None:
    a = argparse.ArgumentParser(
        prog="groma-preparer",
        description="Préparer un projet Groma (inventaire_video.gpkg) à partir du plan d'échantillonnage de nemetonshiny.",
    )
    a.add_argument("plan", type=Path, help="samples.gpkg du projet (couche plots) ou GeoPackage du projet QField (couche placettes)")
    a.add_argument("--sortie", type=Path,
                   help="dossier du projet Groma à copier sur le téléphone (défaut : ./<identifiant du projet nemetonshiny>)")
    a.add_argument("--nom", help="nom du projet affiché sur le téléphone (défaut : celui du projet nemetonshiny ; obligatoire sinon)")
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
    projet = lire_metadonnees(args.plan)
    if args.nom:
        projet["projet_nom"] = args.nom
    if not projet.get("projet_nom"):
        a.error("--nom est obligatoire quand le plan ne vient pas d'un projet nemetonshiny (<projet>/data/samples.gpkg).")
    sortie = args.sortie
    if sortie is None:
        if "projet_id" not in projet:
            a.error("--sortie est obligatoire quand le plan ne vient pas d'un projet nemetonshiny.")
        sortie = Path.cwd() / projet["projet_id"]
    try:
        gpkg = preparer_projet(
            lire_plan(args.plan, args.couche),
            sortie,
            rayon_m=args.rayon,
            declinaison_deg=args.declinaison,
            seuil_diam_cm=args.seuil,
            unite_azimut=args.unite,
            plot_ids=[s.strip() for s in args.placettes.split(",")] if args.placettes else None,
            base_seulement=args.base_seulement,
            ecraser=args.ecraser,
            projet=projet,
        )
    except (ValueError, FileExistsError) as e:
        a.error(str(e))
    print(gpkg)


if __name__ == "__main__":
    main()
