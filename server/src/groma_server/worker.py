"""Worker: takes the queued packages and runs the definitive chain.

Local mode (default): same machine as the API, shared data volume and queue.
Remote mode (``--api``): see ``worker_distant``.
"""

from __future__ import annotations

import argparse
import logging
import time
from pathlib import Path

from .chaine import ConfigChaine, traiter_paquet
from .config import Config
from .file_attente import FileAttente
from .stockage import StockageS3

log = logging.getLogger("groma.worker")


def prefixe_resultats(t: dict) -> str:
    """Storage prefix of a task's results: ``<projet_id or _>/<plot_id>/<task id>``."""
    return f"{t['projet_id'] or '_'}/{t['plot_id']}/{t['id']}"


def archiver(cfg: Config, prefixe: str, fichiers: list[Path]) -> None:
    """Copies the files to the S3-compatible store, if any; the API keeps serving the local copy."""
    if cfg.s3_url:
        s3 = StockageS3(cfg.s3_url, cfg.s3_bucket)
        for f in fichiers:
            s3.deposer(f, f"{prefixe}/{f.name}")


def traiter_une(file: FileAttente, cfg: Config) -> bool:
    t = file.prendre(cfg.bail_s)
    if t is None:
        return False
    log.info("Tâche %s : projet %r, placette %s (tentative %d)", t["id"], t["projet_id"], t["plot_id"], t["tentatives"])
    prefixe = prefixe_resultats(t)
    sortie = cfg.dossier_donnees / "resultats" / prefixe
    try:
        r = traiter_paquet(Path(t["paquet"]), sortie, ConfigChaine(reconstructeurs=cfg.reconstructeurs))
        archiver(cfg, prefixe, [r.chemin_gpkg, r.chemin_rapport_json, r.chemin_rapport_html, Path(t["paquet"])])
        if not file.terminer(t["id"], str(r.chemin_gpkg), str(r.chemin_rapport_json), str(r.chemin_rapport_html),
                             r.rapport["verdict"], t["tentatives"]):
            log.warning("Tâche %s reprise par un autre worker pendant le calcul : résultat écarté", t["id"])
    except Exception as e:
        log.exception("Tâche %s en échec", t["id"])
        file.echouer(t["id"], f"{type(e).__name__}: {e}", cfg.tentatives_max, t["tentatives"])
    return True


def boucler(traiter, pause: float, une_fois: bool, arret_inactif: float | None) -> None:
    """Runs ``traiter()`` until the queue is empty (``une_fois``) or idle for ``arret_inactif`` seconds."""
    dernier = time.monotonic()
    while True:
        if traiter():
            dernier = time.monotonic()
            continue
        if une_fois:
            return
        if arret_inactif is not None and time.monotonic() - dernier >= arret_inactif:
            log.info("Aucune tâche depuis %.0f s : arrêt du worker", arret_inactif)
            return
        time.sleep(pause)


def main() -> None:
    p = argparse.ArgumentParser(description="Worker Groma")
    p.add_argument("--une-fois", action="store_true", help="traiter la file puis s'arrêter")
    p.add_argument("--pause", type=float, default=5.0)
    p.add_argument("--arret-inactif", type=float, default=None, metavar="SECONDES",
                   help="s'arrêter après tant de secondes sans tâche (instance GPU louée)")
    p.add_argument("--api", default=None, metavar="URL",
                   help="mode distant : prendre les tâches auprès de cette API (jeton : GROMA_JETON_WORKER)")
    a = p.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    cfg = Config()
    if a.api:
        from .worker_distant import ClientApi, traiter_une_distante

        if not cfg.jeton_worker:
            p.error("GROMA_JETON_WORKER est requis en mode distant.")
        client = ClientApi(a.api, cfg.jeton_worker)
        travail = cfg.dossier_donnees / "worker"
        boucler(lambda: traiter_une_distante(client, cfg, travail), a.pause, a.une_fois, a.arret_inactif)
    else:
        file = FileAttente(cfg.base)
        boucler(lambda: traiter_une(file, cfg), a.pause, a.une_fois, a.arret_inactif)


if __name__ == "__main__":
    main()
