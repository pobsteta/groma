"""QField projects published by nemetonshiny for the phones (brief envoi projet).

nemetonshiny uploads the ``.qgz`` written by ``creer_projet_inventaire_video()``
(GeoPackage and rasters inside) and its project plugin ``.qml``, then
publishes them as a new version; Groma lists the projects and downloads the
current version.

Layout under ``<data>/projets_qfield/<projet_id>/``::

    attente/<nom>              files uploaded, not yet published
    <version>/<nom>            published files (version = UTC AAAAMMJJTHHMMSSZ)
    <version>/manifeste.json
    courante                   name of the current version

A version is published atomically (temporary folder renamed); only the
last [VERSIONS_GARDEES] are kept.
"""

from __future__ import annotations

import hashlib
import json
import logging
import re
import shutil
import sqlite3
import tempfile
import time
import zipfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

log = logging.getLogger(__name__)

PROJET_ID = re.compile(r"^[A-Za-z0-9_.-]{1,64}$")
NOM_FICHIER = re.compile(r"^[A-Za-z0-9_-]{1,64}\.(qgz|qml)$")
VERSION = re.compile(r"^\d{8}T\d{6}Z$")
GPKG = "inventaire_video.gpkg"


class ErreurProjet(Exception):
    """Invalid request; ``code`` is the HTTP status to return."""

    def __init__(self, code: int, message: str) -> None:
        super().__init__(message)
        self.code = code


@dataclass(frozen=True)
class Fichier:
    nom: str
    sha256: str
    taille: int


def projet_valide(projet_id: str) -> bool:
    return bool(PROJET_ID.match(projet_id)) and projet_id not in (".", "..")


def _sha256(chemin: Path) -> str:
    h = hashlib.sha256()
    with open(chemin, "rb") as f:
        for bloc in iter(lambda: f.read(1 << 20), b""):
            h.update(bloc)
    return h.hexdigest()


class ProjetsQfield:
    def __init__(self, dossier: Path, taille_max: int, versions_gardees: int = 3) -> None:
        self.dossier = dossier
        self.taille_max = taille_max
        self.versions_gardees = versions_gardees
        dossier.mkdir(parents=True, exist_ok=True)

    def _projet(self, projet_id: str) -> Path:
        if not projet_valide(projet_id):
            raise ErreurProjet(400, "projet_id invalide.")
        return self.dossier / projet_id

    # Upload ---------------------------------------------------------------

    def chemin_attente(self, projet_id: str, nom: str) -> Path:
        if not NOM_FICHIER.match(nom):
            raise ErreurProjet(400, "Nom de fichier invalide (attendu : <nom>.qgz ou <nom>.qml).")
        d = self._projet(projet_id) / "attente"
        d.mkdir(parents=True, exist_ok=True)
        return d / nom

    def fin_depot(self, chemin: Path) -> Fichier:
        return Fichier(chemin.name, _sha256(chemin), chemin.stat().st_size)

    # Publication ----------------------------------------------------------

    def publier(self, projet_id: str, projet_nom: str, fichiers: list[Fichier],
                genere_le: str | None = None, nemeton_version: str | None = None) -> dict:
        racine = self._projet(projet_id)
        attente = racine / "attente"
        noms = [f.nom for f in fichiers]
        if len(set(noms)) != len(noms):
            raise ErreurProjet(400, "Fichier en double.")
        qgz = [f for f in fichiers if f.nom.endswith(".qgz")]
        if len(qgz) != 1:
            raise ErreurProjet(400, "Exactement un .qgz attendu.")
        for f in fichiers:
            if not NOM_FICHIER.match(f.nom):
                raise ErreurProjet(400, f"Nom de fichier invalide : {f.nom}")
            p = attente / f.nom
            if not p.is_file():
                raise ErreurProjet(409, f"{f.nom} n'a pas été déposé.")
            if p.stat().st_size != f.taille or _sha256(p) != f.sha256:
                raise ErreurProjet(422, f"{f.nom} : taille ou somme de contrôle SHA-256 incorrecte.")
        lu = self._verifier_qgz(attente / qgz[0].nom)
        if lu != projet_id:
            raise ErreurProjet(422, f"Le GeoPackage du .qgz appartient au projet « {lu} », pas à « {projet_id} ».")

        version = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        while (racine / version).exists():  # two publications within one second
            time.sleep(0.2)
            version = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        tmp = Path(tempfile.mkdtemp(prefix=".publication-", dir=racine))
        for f in fichiers:
            (attente / f.nom).replace(tmp / f.nom)
        manifeste = {
            "projet_id": projet_id, "projet_nom": projet_nom, "version": version,
            "publie_le": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "genere_le": genere_le, "nemeton_version": nemeton_version,
            "fichiers": [f.__dict__ for f in fichiers],
            "taille_totale": sum(f.taille for f in fichiers),
        }
        (tmp / "manifeste.json").write_text(json.dumps(manifeste, ensure_ascii=False, indent=2), encoding="utf-8")
        tmp.replace(racine / version)
        courante_tmp = racine / ".courante"
        courante_tmp.write_text(version)
        courante_tmp.replace(racine / "courante")
        self._purger(racine)
        log.info("Projet %s publié, version %s (%d octets).", projet_id, version, manifeste["taille_totale"])
        return manifeste

    def _verifier_qgz(self, qgz: Path) -> str:
        """Checks the archive and returns the projet_id of its GeoPackage."""
        try:
            with zipfile.ZipFile(qgz) as z:
                noms = z.namelist()
                if not any(n.endswith(".qgs") for n in noms):
                    raise ErreurProjet(422, "Le .qgz ne contient pas de projet .qgs.")
                gpkg = next((n for n in noms if Path(n).name == GPKG), None)
                if gpkg is None:
                    raise ErreurProjet(422, f"Le .qgz ne contient pas {GPKG}.")
                with tempfile.TemporaryDirectory() as d:
                    cible = Path(d) / GPKG
                    with z.open(gpkg) as src, open(cible, "wb") as dst:
                        shutil.copyfileobj(src, dst)
                    return self._projet_id_gpkg(cible)
        except zipfile.BadZipFile as e:
            raise ErreurProjet(422, "Le .qgz n'est pas une archive zip lisible.") from e

    @staticmethod
    def _projet_id_gpkg(gpkg: Path) -> str:
        try:
            with sqlite3.connect(f"file:{gpkg}?mode=ro", uri=True) as db:
                ligne = db.execute("SELECT projet_id FROM projet ORDER BY fid LIMIT 1").fetchone()
        except sqlite3.Error as e:
            raise ErreurProjet(422, f"Table projet illisible dans {GPKG} : {e}") from e
        if not ligne or not ligne[0] or not projet_valide(str(ligne[0])):
            raise ErreurProjet(422, f"{GPKG} n'a pas de projet_id valide (table projet).")
        return str(ligne[0])

    def _purger(self, racine: Path) -> None:
        versions = sorted(p for p in racine.iterdir() if p.is_dir() and VERSION.match(p.name))
        for p in versions[: max(0, len(versions) - self.versions_gardees)]:
            shutil.rmtree(p)

    # Reading --------------------------------------------------------------

    def manifeste(self, projet_id: str) -> dict | None:
        racine = self._projet(projet_id)
        courante = racine / "courante"
        if not courante.is_file():
            return None
        m = racine / courante.read_text().strip() / "manifeste.json"
        return json.loads(m.read_text(encoding="utf-8")) if m.is_file() else None

    def lister(self) -> list[dict]:
        out = []
        for d in sorted(self.dossier.iterdir()):
            if d.is_dir() and projet_valide(d.name):
                m = self.manifeste(d.name)
                if m:
                    out.append(m)
        return out

    def fichier(self, projet_id: str, version: str, nom: str) -> Path:
        racine = self._projet(projet_id)
        if not VERSION.match(version) or not NOM_FICHIER.match(nom):
            raise ErreurProjet(404, "Fichier inconnu.")
        p = racine / version / nom
        if not p.is_file():
            raise ErreurProjet(404, "Fichier inconnu.")
        return p
