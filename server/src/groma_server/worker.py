"""Worker: takes the queued packages and runs the definitive chain."""

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


def traiter_une(file: FileAttente, cfg: Config) -> bool:
    t = file.prendre()
    if t is None:
        return False
    log.info("Tâche %s : projet %r, placette %s (tentative %d)", t["id"], t["projet_id"], t["plot_id"], t["tentatives"])
    prefixe = f"{t['projet_id'] or '_'}/{t['plot_id']}/{t['id']}"
    sortie = cfg.dossier_donnees / "resultats" / prefixe
    try:
        r = traiter_paquet(Path(t["paquet"]), sortie, ConfigChaine(reconstructeurs=cfg.reconstructeurs))
        if cfg.s3_url:
            # Archive on the S3-compatible store; the API keeps serving the local copy.
            s3 = StockageS3(cfg.s3_url, cfg.s3_bucket)
            for f in (r.chemin_gpkg, r.chemin_rapport_json, r.chemin_rapport_html, Path(t["paquet"])):
                s3.deposer(f, f"{prefixe}/{f.name}")
        file.terminer(t["id"], str(r.chemin_gpkg), str(r.chemin_rapport_json), str(r.chemin_rapport_html), r.rapport["verdict"])
    except Exception as e:
        log.exception("Tâche %s en échec", t["id"])
        file.echouer(t["id"], f"{type(e).__name__}: {e}", cfg.tentatives_max)
    return True


def main() -> None:
    p = argparse.ArgumentParser(description="Worker Groma")
    p.add_argument("--une-fois", action="store_true", help="traiter la file puis s'arrêter")
    p.add_argument("--pause", type=float, default=5.0)
    a = p.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    cfg = Config()
    file = FileAttente(cfg.base)
    while True:
        if not traiter_une(file, cfg):
            if a.une_fois:
                return
            time.sleep(a.pause)


if __name__ == "__main__":
    main()
