"""Fusion of the ARCore depth maps (no GPU)."""

from __future__ import annotations

import logging
from pathlib import Path

import numpy as np

from ..paquet import Paquet, pose_locale
from .base import Nuage, sous_echantillonner

log = logging.getLogger(__name__)


class ReconstructeurProfondeur:
    nom = "profondeur"
    methode = "embarque"

    def __init__(self, profondeur_max_m: float = 8.0, confiance_min: int = 128, voxel_m: float = 0.005):
        self.profondeur_max_m = profondeur_max_m
        self.confiance_min = confiance_min
        self.voxel_m = voxel_m

    def disponible(self) -> bool:
        return True

    def reconstruire(self, paquet: Paquet, dossier: Path, masques: dict[int, np.ndarray] | None = None) -> Nuage:
        blocs = []
        for img in paquet.manifeste.images:
            if img.profondeur is None or img.etat_suivi != "TRACKING":
                continue
            d = paquet.profondeur(img)
            k = img.intrinseques_profondeur
            garde = np.isfinite(d) & (d > 0.2) & (d <= self.profondeur_max_m)
            conf = paquet.confiance(img)
            if conf is not None:
                garde &= conf >= self.confiance_min
            if masques and img.id in masques:
                garde &= masques[img.id]
            v, u = np.nonzero(garde)
            z = d[v, u]
            cam = np.column_stack([(u + 0.5 - k.cx) / k.fx * z, -(v + 0.5 - k.cy) / k.fy * z, -z, np.ones_like(z)])
            blocs.append((cam @ pose_locale(img).T)[:, :3])
        pts = np.vstack(blocs) if blocs else np.zeros((0, 3))
        n_brut = pts.shape[0]
        pts = sous_echantillonner(pts, self.voxel_m)
        log.info("Fusion des profondeurs : %d points (%d avant sous-échantillonnage)", pts.shape[0], n_brut)
        return Nuage(pts, self.methode, self.nom, {"n_points_bruts": n_brut, "n_images": len(blocs)})
