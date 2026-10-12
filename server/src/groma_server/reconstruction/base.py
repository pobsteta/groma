from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Protocol

import numpy as np

from ..paquet import Paquet


@dataclass
class Nuage:
    """Point cloud in the Groma local frame (metres, z up, ARCore heading)."""

    points: np.ndarray
    methode: str  # value of the `methode` column: embarque | classique | ia
    nom: str
    infos: dict = field(default_factory=dict)


class Reconstructeur(Protocol):
    nom: str
    methode: str

    def disponible(self) -> bool: ...

    def reconstruire(self, paquet: Paquet, dossier: Path, masques: dict[int, np.ndarray] | None = None) -> Nuage: ...


def sous_echantillonner(points: np.ndarray, voxel_m: float) -> np.ndarray:
    """One point per voxel (centroid), to bound memory on long captures."""
    if points.shape[0] == 0 or voxel_m <= 0:
        return points
    cles = np.floor(points / voxel_m).astype(np.int64)
    _, inverse, compte = np.unique(cles, axis=0, return_inverse=True, return_counts=True)
    inverse = inverse.ravel()
    somme = np.zeros((compte.size, 3))
    np.add.at(somme, inverse, points)
    return somme / compte[:, None]
