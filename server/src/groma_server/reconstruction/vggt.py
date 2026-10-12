"""Direct AI reconstruction (VGGT or equivalent), by chunks.

The model predicts, for a chunk of frames, cameras and per-pixel 3D points
in an arbitrary similarity frame. Each chunk overlaps the previous one; it is
brought into the ARCore world frame by a similarity (Umeyama) fitted on the
camera centres, which both glues the chunks and sets the metric scale from
ARCore (the targets then check that scale during georeferencing).

Licence: check before deployment. The VGGT code and the original VGGT-1B
weights are distributed by Meta under a non-commercial licence; a
"VGGT-1B-Commercial" checkpoint exists under different terms. Do not ship
weights with this repository; see docs/dependances.md.
"""

from __future__ import annotations

import logging
from pathlib import Path

import numpy as np

from ..paquet import MONDE_VERS_LOCAL, Paquet
from .base import Nuage, sous_echantillonner

log = logging.getLogger(__name__)


def similitude_umeyama(src: np.ndarray, dst: np.ndarray) -> tuple[float, np.ndarray, np.ndarray]:
    """Scale s, rotation R, translation t minimising |s R src + t - dst|²."""
    src, dst = np.asarray(src, float), np.asarray(dst, float)
    ms, md = src.mean(0), dst.mean(0)
    a, b = src - ms, dst - md
    cov = b.T @ a / len(src)
    u, d, vt = np.linalg.svd(cov)
    s_mat = np.eye(3)
    if np.linalg.det(u) * np.linalg.det(vt) < 0:
        s_mat[2, 2] = -1
    r = u @ s_mat @ vt
    var = (a**2).sum() / len(src)
    s = float(np.trace(np.diag(d) @ s_mat) / var)
    return s, r, md - s * r @ ms


def morceaux(n: int, taille: int, recouvrement: int) -> list[range]:
    if n <= taille:
        return [range(n)]
    out, debut = [], 0
    while True:
        fin = min(n, debut + taille)
        out.append(range(debut, fin))
        if fin == n:
            return out
        debut = fin - recouvrement


class ReconstructeurVGGT:
    nom = "vggt"
    methode = "ia"

    def __init__(self, poids: str = "facebook/VGGT-1B", taille_morceau: int = 32, recouvrement: int = 8,
                 confiance_min: float = 3.0, voxel_m: float = 0.005):
        self.poids = poids
        self.taille_morceau = taille_morceau
        self.recouvrement = recouvrement
        self.confiance_min = confiance_min
        self.voxel_m = voxel_m

    def disponible(self) -> bool:
        try:
            import torch  # noqa: F401
            import vggt  # noqa: F401
        except ImportError:
            return False
        import torch

        return torch.cuda.is_available()

    def reconstruire(self, paquet: Paquet, dossier: Path, masques: dict[int, np.ndarray] | None = None) -> Nuage:
        import torch
        from vggt.models.vggt import VGGT
        from vggt.utils.load_fn import load_and_preprocess_images
        from vggt.utils.pose_enc import pose_encoding_to_extri_intri

        images = [i for i in paquet.manifeste.images if i.image]
        (dossier / "vggt").mkdir(parents=True, exist_ok=True)
        chemins = [paquet.extraire(i.image, dossier / "vggt") for i in images]
        centres_arcore = np.array([np.asarray(i.pose).reshape(4, 4)[:3, 3] for i in images])
        modele = VGGT.from_pretrained(self.poids).to("cuda").eval()
        blocs = []
        for m in morceaux(len(images), self.taille_morceau, self.recouvrement):
            lot = load_and_preprocess_images([str(chemins[i]) for i in m]).to("cuda")
            with torch.no_grad(), torch.autocast("cuda", dtype=torch.bfloat16):
                pred = modele(lot)
            extr, _ = pose_encoding_to_extri_intri(pred["pose_enc"], lot.shape[-2:])
            extr = extr[0].float().cpu().numpy()  # camera_from_world (OpenCV), 3x4
            centres = np.array([-e[:3, :3].T @ e[:3, 3] for e in extr])
            s, r, t = similitude_umeyama(centres, centres_arcore[list(m)])
            pts = pred["world_points"][0].float().cpu().numpy().reshape(-1, 3)
            conf = pred["world_points_conf"][0].float().cpu().numpy().reshape(-1)
            pts = pts[conf >= self.confiance_min]
            blocs.append((s * pts @ r.T) + t)
            log.info("VGGT : morceau %d-%d, échelle %.3f", m.start, m.stop - 1, s)
        monde = np.vstack(blocs)
        local = (np.column_stack([monde, np.ones(len(monde))]) @ MONDE_VERS_LOCAL.T)[:, :3]
        return Nuage(sous_echantillonner(local, self.voxel_m), self.methode, self.nom, {"n_morceaux": len(blocs)})
