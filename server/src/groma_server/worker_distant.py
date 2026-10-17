"""Remote worker: takes the tasks from the API over HTTP(S).

For a GPU machine rented on demand: it shares neither the data volume nor
the SQLite queue with the API. For each task it downloads the package
(resumed with ``Range`` after a cut), renews its lease while computing,
sends the three result files back, then closes the task. Once the lease has
expired and the task has been taken again, the API answers 409 and the
result is dropped.
"""

from __future__ import annotations

import json
import logging
import shutil
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from .chaine import ConfigChaine, traiter_paquet
from .config import Config

log = logging.getLogger("groma.worker")


@dataclass
class Reponse:
    statut: int
    corps: bytes

    def json(self):
        return json.loads(self.corps)


# (method, path, headers, body) -> Reponse; replaced in tests by the FastAPI test client.
Transport = Callable[[str, str, dict, bytes | None], Reponse]


class ErreurApi(RuntimeError):
    def __init__(self, statut: int, message: str):
        super().__init__(f"HTTP {statut} : {message}")
        self.statut = statut


class TacheReprise(ErreurApi):
    """409: the lease expired and the task was taken again, or closed."""


def transport_urllib(base: str, delai_s: float = 120) -> Transport:
    def envoyer(methode: str, chemin: str, entetes: dict, corps: bytes | None) -> Reponse:
        req = urllib.request.Request(base.rstrip("/") + chemin, data=corps, method=methode, headers=entetes)
        try:
            with urllib.request.urlopen(req, timeout=delai_s) as r:
                return Reponse(r.status, r.read())
        except urllib.error.HTTPError as e:
            return Reponse(e.code, e.read())

    return envoyer


class ClientApi:
    def __init__(self, base: str, jeton: str, transport: Transport | None = None):
        self.transport = transport or transport_urllib(base)
        self.entetes = {"Authorization": f"Bearer {jeton}"}

    def _appel(self, methode: str, chemin: str, json_corps: dict | None = None, corps: bytes | None = None,
               entetes: dict | None = None, attendus: tuple[int, ...] = (200,)) -> Reponse:
        h = dict(self.entetes, **(entetes or {}))
        if json_corps is not None:
            corps = json.dumps(json_corps).encode()
            h["Content-Type"] = "application/json"
        r = self.transport(methode, chemin, h, corps)
        if r.statut in attendus:
            return r
        try:
            detail = r.json().get("detail", "")
        except (ValueError, AttributeError):
            detail = r.corps[:200].decode(errors="replace")
        raise (TacheReprise if r.statut == 409 else ErreurApi)(r.statut, str(detail))

    def prendre(self) -> dict | None:
        r = self._appel("POST", "/worker/prendre", attendus=(200, 204))
        return None if r.statut == 204 else r.json()

    def telecharger(self, t: dict, cible: Path, morceau: int = 64 * 1024**2, essais: int = 5) -> Path:
        """Downloads the package by ranges, resuming from what is already on disk."""
        cible.parent.mkdir(parents=True, exist_ok=True)
        recu = cible.stat().st_size if cible.exists() else 0
        echecs = 0
        while recu < t["taille"]:
            fin = min(recu + morceau, t["taille"]) - 1
            try:
                r = self._appel("GET", f"/worker/taches/{t['id']}/paquet?tentative={t['tentative']}",
                                entetes={"Range": f"bytes={recu}-{fin}"}, attendus=(200, 206))
            except (TacheReprise, ErreurApi) as e:
                if isinstance(e, TacheReprise) or e.statut < 500:
                    raise
                r = None
            except OSError as e:
                log.warning("Téléchargement coupé à %d octets : %s", recu, e)
                r = None
            if r is None:
                echecs += 1
                if echecs >= essais:
                    raise ErreurApi(0, f"téléchargement interrompu {essais} fois")
                time.sleep(min(2**echecs, 30))
                continue
            donnees = r.corps if r.statut == 206 else r.corps[recu:fin + 1]
            with open(cible, "ab") as f:
                f.write(donnees)
            recu += len(donnees)
            echecs = 0
        if recu != t["taille"]:
            raise ErreurApi(0, f"paquet de {recu} octets au lieu de {t['taille']}")
        return cible

    def prolonger(self, t: dict) -> None:
        self._appel("POST", f"/worker/taches/{t['id']}/bail", {"tentative": t["tentative"]})

    def deposer(self, t: dict, nom: str, chemin: Path) -> None:
        self._appel("PUT", f"/worker/taches/{t['id']}/resultats/{nom}?tentative={t['tentative']}",
                    corps=chemin.read_bytes(), entetes={"Content-Type": "application/octet-stream"})

    def terminer(self, t: dict, verdict: str) -> dict:
        return self._appel("POST", f"/worker/taches/{t['id']}/terminer", {"tentative": t["tentative"], "verdict": verdict}).json()

    def echouer(self, t: dict, erreur: str) -> None:
        self._appel("POST", f"/worker/taches/{t['id']}/echouer", {"tentative": t["tentative"], "erreur": erreur[:4000]})


class Bail:
    """Renews the lease every third of its length, in the background."""

    def __init__(self, client: ClientApi, t: dict):
        self.client, self.t = client, t
        self.periode = max(1.0, t.get("bail_s", 3600) / 3)
        self.perdu = False
        self._fin = threading.Event()
        self._fil = threading.Thread(target=self._boucle, daemon=True)

    def _boucle(self) -> None:
        while not self._fin.wait(self.periode):
            try:
                self.client.prolonger(self.t)
            except TacheReprise:
                log.warning("Tâche %s reprise par un autre worker : calcul abandonné à la fin", self.t["id"])
                self.perdu = True
                return
            except (ErreurApi, OSError) as e:
                log.warning("Bail de la tâche %s non renouvelé : %s", self.t["id"], e)

    def __enter__(self) -> Bail:
        self._fil.start()
        return self

    def __exit__(self, *_exc) -> None:
        self._fin.set()
        self._fil.join(timeout=5)


def traiter_une_distante(client: ClientApi, cfg: Config, travail: Path) -> bool:
    """Takes one task from the API and processes it. False when the queue is empty."""
    t = client.prendre()
    if t is None:
        return False
    log.info("Tâche %s : projet %r, placette %s (tentative %d)", t["id"], t["projet_id"], t["plot_id"], t["tentative"])
    dossier = travail / t["id"]
    try:
        with Bail(client, t) as bail:
            paquet = client.telecharger(t, dossier / "paquet.groma.zip")
            r = traiter_paquet(paquet, dossier / "sortie", ConfigChaine(reconstructeurs=cfg.reconstructeurs))
        if bail.perdu:
            return True
        for nom, chemin in (("definitif.gpkg", r.chemin_gpkg), ("rapport.json", r.chemin_rapport_json),
                            ("rapport.html", r.chemin_rapport_html)):
            client.deposer(t, nom, chemin)
        client.terminer(t, r.rapport["verdict"])
    except TacheReprise:
        log.warning("Tâche %s reprise par un autre worker : résultat écarté", t["id"])
    except Exception as e:
        log.exception("Tâche %s en échec", t["id"])
        try:
            client.echouer(t, f"{type(e).__name__}: {e}")
        except (ErreurApi, OSError):
            log.exception("Échec de la tâche %s non signalé à l'API", t["id"])
    finally:
        shutil.rmtree(dossier, ignore_errors=True)
    return True
