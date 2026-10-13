"""Task queue on SQLite: no broker to run, safe across processes.

One API process enqueues; one or more workers take tasks atomically
(``BEGIN IMMEDIATE`` + conditional update). A task interrupted by a worker
crash is put back in the queue when it is older than ``bail_s``.

Plots are keyed by (``projet_id``, ``plot_id``): two sampling plans may
both have a ``P001``. ``projet_id`` is ``""`` for the default project
(phones and clients older than the project list).
"""

from __future__ import annotations

import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path

ETATS = ("en_attente", "en_cours", "terminee", "echec")

SCHEMA = """
CREATE TABLE IF NOT EXISTS taches (
  id TEXT PRIMARY KEY,
  projet_id TEXT NOT NULL DEFAULT '',
  plot_id TEXT NOT NULL,
  paquet TEXT NOT NULL,
  etat TEXT NOT NULL DEFAULT 'en_attente',
  tentatives INTEGER NOT NULL DEFAULT 0,
  cree TEXT NOT NULL,
  maj TEXT NOT NULL,
  erreur TEXT,
  gpkg TEXT,
  rapport_json TEXT,
  rapport_html TEXT,
  verdict TEXT
);
CREATE INDEX IF NOT EXISTS taches_etat ON taches(etat, cree);
"""

# Queues created before projet_id: the column is added, existing tasks go to
# the default project. The index is created after, on both kinds of base.
INDEX = "CREATE INDEX IF NOT EXISTS taches_projet_plot ON taches(projet_id, plot_id, cree);"


def _maintenant() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class FileAttente:
    def __init__(self, chemin: str | Path):
        self.chemin = Path(chemin)
        self.chemin.parent.mkdir(parents=True, exist_ok=True)
        with self._cx() as cx:
            cx.executescript(SCHEMA)
            if "projet_id" not in {r["name"] for r in cx.execute("PRAGMA table_info(taches)")}:
                cx.execute("ALTER TABLE taches ADD COLUMN projet_id TEXT NOT NULL DEFAULT ''")
            cx.execute("DROP INDEX IF EXISTS taches_plot")
            cx.execute(INDEX)

    def _cx(self) -> sqlite3.Connection:
        cx = sqlite3.connect(self.chemin, timeout=30, isolation_level=None)
        cx.row_factory = sqlite3.Row
        cx.execute("PRAGMA journal_mode=WAL")
        cx.execute("PRAGMA busy_timeout=30000")
        return cx

    def ajouter(self, plot_id: str, paquet: str, projet_id: str = "") -> str:
        tid = str(uuid.uuid4())
        t = _maintenant()
        with self._cx() as cx:
            cx.execute(
                "INSERT INTO taches (id, projet_id, plot_id, paquet, cree, maj) VALUES (?, ?, ?, ?, ?, ?)",
                (tid, projet_id, plot_id, paquet, t, t),
            )
        return tid

    def prendre(self, bail_s: int = 3600) -> dict | None:
        cx = self._cx()
        try:
            cx.execute("BEGIN IMMEDIATE")
            limite = datetime.fromtimestamp(datetime.now(timezone.utc).timestamp() - bail_s, timezone.utc).isoformat(timespec="seconds")
            r = cx.execute(
                "SELECT * FROM taches WHERE etat = 'en_attente' OR (etat = 'en_cours' AND maj < ?) ORDER BY cree LIMIT 1",
                (limite,),
            ).fetchone()
            if r is None:
                cx.execute("COMMIT")
                return None
            cx.execute("UPDATE taches SET etat='en_cours', tentatives=tentatives+1, maj=? WHERE id=?", (_maintenant(), r["id"]))
            cx.execute("COMMIT")
            return self.obtenir(r["id"])
        except Exception:
            cx.execute("ROLLBACK")
            raise
        finally:
            cx.close()

    def terminer(self, tid: str, gpkg: str, rapport_json: str, rapport_html: str, verdict: str) -> None:
        with self._cx() as cx:
            cx.execute(
                "UPDATE taches SET etat='terminee', gpkg=?, rapport_json=?, rapport_html=?, verdict=?, erreur=NULL, maj=? WHERE id=?",
                (gpkg, rapport_json, rapport_html, verdict, _maintenant(), tid),
            )

    def echouer(self, tid: str, erreur: str, tentatives_max: int) -> None:
        with self._cx() as cx:
            r = cx.execute("SELECT tentatives FROM taches WHERE id=?", (tid,)).fetchone()
            etat = "en_attente" if r and r["tentatives"] < tentatives_max else "echec"
            cx.execute("UPDATE taches SET etat=?, erreur=?, maj=? WHERE id=?", (etat, erreur[:2000], _maintenant(), tid))

    def obtenir(self, tid: str) -> dict | None:
        with self._cx() as cx:
            r = cx.execute("SELECT * FROM taches WHERE id=?", (tid,)).fetchone()
        return dict(r) if r else None

    def derniere(self, plot_id: str, etat: str | None = None, projet_id: str = "") -> dict | None:
        sql = "SELECT * FROM taches WHERE projet_id=? AND plot_id=?"
        args: list = [projet_id, plot_id]
        if etat:
            sql += " AND etat=?"
            args.append(etat)
        with self._cx() as cx:
            r = cx.execute(sql + " ORDER BY cree DESC, rowid DESC LIMIT 1", args).fetchone()
        return dict(r) if r else None

    def lister(self, projet_id: str | None = None) -> list[dict]:
        """Last task of every plot, of one project or of all (``None``)."""
        sql = (
            "SELECT t.* FROM taches t WHERE t.rowid = "
            "(SELECT MAX(rowid) FROM taches u WHERE u.projet_id = t.projet_id AND u.plot_id = t.plot_id)"
        )
        args: list = []
        if projet_id is not None:
            sql += " AND t.projet_id = ?"
            args.append(projet_id)
        with self._cx() as cx:
            lignes = cx.execute(sql + " ORDER BY projet_id, plot_id", args).fetchall()
        return [dict(r) for r in lignes]
