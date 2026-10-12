"""Definitive output GeoPackage: one ``detections`` layer (schema of the
inventaire_video.gpkg ``detections`` layer, no field attributes)."""

from __future__ import annotations

from pathlib import Path

import geopandas as gpd
from shapely.geometry import Point

from groma.gpkg import noms_colonnes


def ecrire_gpkg_definitif(chemin: Path, detections: list[dict]) -> Path:
    cols = [c for c in noms_colonnes("detections")]
    lignes = [{k: d.get(k) for k in cols} for d in detections]
    gdf = gpd.GeoDataFrame(lignes, columns=cols, geometry=[Point(d["x"], d["y"]) for d in detections], crs=2154)
    if chemin.exists():
        chemin.unlink()
    gdf.rename_geometry("geom").to_file(chemin, layer="detections", driver="GPKG")
    return chemin
