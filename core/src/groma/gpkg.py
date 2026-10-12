"""The ``inventaire_video.gpkg`` GeoPackage: schema, creation, detection writes.

* :func:`creer_gpkg` is the Python reference of what nemeton builds from the
  "Inventaire vidéo" tab (used for the example GeoPackage and the tests).
* :func:`ecrire_detections` is the write protocol of the Groma phone app
  (decision 3), mirrored line for line in Kotlin: plain SQLite, no spatial
  function, one short ``BEGIN IMMEDIATE`` transaction, ``busy_timeout`` so
  that a write never fails because QField holds the lock for a moment.
  Only the ``detections`` table is written; the triggers create or update
  the ``arbres`` rows without ever touching the field attributes.
"""

from __future__ import annotations

import json
import logging
import sqlite3
import struct
from datetime import datetime, timezone
from functools import lru_cache
from importlib import resources
from pathlib import Path
from typing import Iterable, Sequence

log = logging.getLogger(__name__)

SRS_ID = 2154
GEOMETRIE = "geom"


@lru_cache(maxsize=1)
def charger_schema() -> dict:
    texte = resources.files("groma").joinpath("donnees/inventaire_video.schema.json").read_text("utf-8")
    return json.loads(texte)


def sql_declencheurs() -> str:
    return resources.files("groma").joinpath("donnees/declencheurs.sql").read_text("utf-8")


@lru_cache(maxsize=1)
def essences() -> list[dict]:
    texte = resources.files("groma").joinpath("donnees/essences_bdforet_v2.json").read_text("utf-8")
    return json.loads(texte)["essences"]


def couche(nom: str) -> dict:
    for c in charger_schema()["couches"]:
        if c["nom"] == nom:
            return c
    raise KeyError(nom)


def noms_colonnes(nom_couche: str) -> list[str]:
    return [c["nom"] for c in couche(nom_couche)["colonnes"]]


# ---- GeoPackage binary geometry (point only) ---------------------------


def encoder_point(x: float, y: float, srs_id: int = SRS_ID) -> bytes:
    """GeoPackage binary header (no envelope, little endian) + WKB point."""
    return b"GP" + bytes([0, 0x01]) + struct.pack("<i", srs_id) + struct.pack("<BIdd", 1, 1, x, y)


def decoder_point(blob: bytes) -> tuple[float, float]:
    if blob[:2] != b"GP":
        raise ValueError("Géométrie GeoPackage invalide.")
    flags = blob[3]
    taille_env = {0: 0, 1: 32, 2: 48, 3: 48, 4: 64}[(flags >> 1) & 0x07]
    wkb = blob[8 + taille_env :]
    ordre = "<" if wkb[0] == 1 else ">"
    (type_geom,) = struct.unpack(ordre + "I", wkb[1:5])
    if type_geom % 1000 != 1:
        raise ValueError("Seuls les points sont pris en charge.")
    return struct.unpack(ordre + "dd", wkb[5:21])


# ---- Creation -----------------------------------------------------------


def _dtype(type_sql: str) -> str:
    return {"TEXT": "object", "INTEGER": "Int64", "REAL": "float64", "DATETIME": "object"}[type_sql]


def _gdf_vide(nom_couche: str):
    import geopandas as gpd
    import pandas as pd

    spec = couche(nom_couche)
    df = pd.DataFrame({c["nom"]: pd.Series(dtype=_dtype(c["type"])) for c in spec["colonnes"]})
    return gpd.GeoDataFrame(df, geometry=gpd.GeoSeries([], crs=SRS_ID), crs=SRS_ID)


def _completer(gdf, nom_couche: str):
    """Add the schema columns missing from ``gdf`` (typed), keep the order."""
    import geopandas as gpd
    import pandas as pd

    spec = couche(nom_couche)
    for c in spec["colonnes"]:
        if c["nom"] not in gdf.columns:
            defaut = c.get("defaut")
            gdf[c["nom"]] = pd.Series([defaut] * len(gdf), index=gdf.index, dtype=_dtype(c["type"]))
    cols = [c["nom"] for c in spec["colonnes"]]
    return gpd.GeoDataFrame(gdf[cols], geometry=gdf.geometry.values, crs=SRS_ID)


def _ecrire(gdf, chemin: Path, nom_couche: str) -> None:
    import pyogrio

    spec = couche(nom_couche)
    pyogrio.write_dataframe(
        gdf.rename_geometry(GEOMETRIE) if gdf.geometry.name != GEOMETRIE else gdf,
        chemin,
        layer=nom_couche,
        driver="GPKG",
        geometry_type="Point" if spec["geometrie"] == "POINT" else "Polygon",
        layer_options={
            "SPATIAL_INDEX": "YES" if spec.get("index_spatial", True) else "NO",
            "GEOMETRY_NAME": GEOMETRIE,
            "FID": "fid",
        },
    )


def creer_gpkg(
    chemin: str | Path,
    placettes,
    houppiers=None,
    cibles=None,
    centres_mesures=None,
) -> Path:
    """Create ``inventaire_video.gpkg``.

    ``placettes``: GeoDataFrame of planned points (EPSG:2154) carrying at
    least ``plot_id``; plan columns (``wgt``, ``ip``, ``stratum``,
    ``strat_*``) are kept as they are. Missing Groma columns are added with
    their defaults.
    """
    chemin = Path(chemin)
    if chemin.exists():
        chemin.unlink()
    p = placettes.to_crs(SRS_ID)
    if "convergence_deg" not in p.columns or p["convergence_deg"].isna().any():
        from .azimut import convergence_lambert93_deg

        lon = p.to_crs(4326).geometry.x
        p["convergence_deg"] = [round(convergence_lambert93_deg(v), 6) for v in lon]
    _ecrire(_completer(p, "placettes"), chemin, "placettes")
    _ecrire(_completer(centres_mesures, "centres_mesures") if centres_mesures is not None else _gdf_vide("centres_mesures"), chemin, "centres_mesures")
    _ecrire(_completer(cibles, "cibles") if cibles is not None else _gdf_vide("cibles"), chemin, "cibles")
    _ecrire(_gdf_vide("arbres"), chemin, "arbres")
    _ecrire(_gdf_vide("detections"), chemin, "detections")
    if houppiers is not None and len(houppiers):
        _ecrire(_completer(houppiers, "houppiers"), chemin, "houppiers")
    else:
        _ecrire(_gdf_vide("houppiers"), chemin, "houppiers")
    with sqlite3.connect(chemin) as cx:
        cx.executescript(sql_declencheurs())
    log.info("GeoPackage créé : %s", chemin)
    return chemin


# ---- Phone write protocol ----------------------------------------------


def _maintenant() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")


def ecrire_detections(
    chemin: str | Path, detections: Iterable[dict], delai_verrou_ms: int = 5000
) -> tuple[int, int]:
    """Insert or update detections (keyed by ``uuid``). Returns (inserted, updated).

    Each detection: uuid, plot_id, x, y (Lambert-93) and any of the
    ``detections`` columns. Columns absent from the dict are left unchanged
    on update.
    """
    autorisees = set(noms_colonnes("detections")) - {"uuid"}
    cx = sqlite3.connect(chemin, timeout=delai_verrou_ms / 1000, isolation_level=None)
    inseres = maj = 0
    try:
        cx.execute(f"PRAGMA busy_timeout = {int(delai_verrou_ms)}")
        cx.execute("BEGIN IMMEDIATE")
        for d in detections:
            valeurs = {k: v for k, v in d.items() if k in autorisees}
            valeurs.setdefault("date_detection", _maintenant())
            valeurs[GEOMETRIE] = encoder_point(d["x"], d["y"])
            existe = cx.execute("SELECT 1 FROM detections WHERE uuid = ?", (d["uuid"],)).fetchone()
            if existe:
                assignations = ", ".join(f"{k} = ?" for k in valeurs)
                cx.execute(f"UPDATE detections SET {assignations} WHERE uuid = ?", [*valeurs.values(), d["uuid"]])
                maj += 1
            else:
                cols = ["uuid", *valeurs]
                cx.execute(
                    f"INSERT INTO detections ({', '.join(cols)}) VALUES ({', '.join('?' * len(cols))})",
                    [d["uuid"], *valeurs.values()],
                )
                inseres += 1
        cx.execute("COMMIT")
    except Exception:
        cx.execute("ROLLBACK")
        raise
    finally:
        cx.close()
    return inseres, maj


def lire_points(chemin: str | Path, nom_couche: str, plot_id: str | None = None) -> list[dict]:
    """Read a point layer as dicts with x, y (plain SQLite, no GDAL)."""
    cx = sqlite3.connect(chemin)
    cx.row_factory = sqlite3.Row
    try:
        sql = f"SELECT * FROM {nom_couche}"
        args: Sequence = ()
        if plot_id is not None:
            sql += " WHERE plot_id = ?"
            args = (plot_id,)
        out = []
        for r in cx.execute(sql, args):
            d = dict(r)
            g = d.pop(GEOMETRIE, None)
            if g is not None:
                d["x"], d["y"] = decoder_point(g)
            out.append(d)
        return out
    finally:
        cx.close()


def mettre_a_jour_etat(chemin: str | Path, plot_id: str, etat: str) -> None:
    if etat not in charger_schema()["domaines"]["etat_traitement"]:
        raise ValueError(etat)
    with sqlite3.connect(chemin, timeout=5) as cx:
        cx.execute("UPDATE placettes SET etat_traitement = ? WHERE plot_id = ?", (etat, plot_id))
