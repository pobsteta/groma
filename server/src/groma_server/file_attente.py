"""Task queue on SQLite: no broker to run, safe across processes.

One API process enqueues; one or more workers take tasks atomically
(``BEGIN IMMEDIATE`` + conditional update). A task interrupted by a worker
crash is put back in the queue when it is older than ``bail_s``.
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
CREATE INDEX IF NOT EXISTS taches_plot ON taches(plot_id, cree);
"""


def _maintenant() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class FileAttente:
    def __init__(self, chemin: str | Path):
        self.chemin = Path(chemin)
        self.chemin.parent.mkdir(parents=True, exist_ok=True)
        with self._cx() as cx:
            cx.executescript(SCHEMA)

    def _cx(self) -> sqlite3.Connection:
        cx = sqlite3.connect(self.chemin, timeout=30, isolation_level=None)
        cx.row_factory = sqlite3.Row
        cx.execute("PRAGMA journal_mode=WAL")
        cx.execute("PRAGMA busy_timeout=30000")
        return cx

    def ajouter(self, plot_id: str, paquet: str) -> str:
        tid = str(uuid.uuid4())
        t = _maintenant()
        with self._cx() as cx:
            cx.execute("INSERT INTO taches (id, plot_id, paquet, cree, maj) VALUES (?, ?, ?, ?, ?)", (tid, plot_id, paquet, t, t))
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

    def derniere(self, plot_id: str, etat: str | None = None) -> dict | None:
        sql = "SELECT * FROM taches WHERE plot_id=?"
        args: list = [plot_id]
        if etat:
            sql += " AND etat=?"
            args.append(etat)
        with self._cx() as cx:
            r = cx.execute(sql + " ORDER BY cree DESC, rowid DESC LIMIT 1", args).fetchone()
        return dict(r) if r else None

    def lister(self) -> list[dict]:
        with self._cx() as cx:
            lignes = cx.execute(
                "SELECT t.* FROM taches t WHERE t.rowid = (SELECT MAX(rowid) FROM taches u WHERE u.plot_id = t.plot_id) ORDER BY plot_id"
            ).fetchall()
        return [dict(r) for r in lignes]
