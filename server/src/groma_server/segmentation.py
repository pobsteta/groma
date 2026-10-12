"""Trunk segmentation in the images (step 1), to reconstruct trunks and ground only.

The interface returns, per image id, a boolean mask at the depth-map
resolution. ``SansSegmentation`` keeps every pixel (default). The SAM 2
adapter (Apache-2.0) prompts the model with points sampled on the depth
discontinuities of each frame; it is optional and only loaded when installed.
"""

from __future__ import annotations

from typing import Protocol

import numpy as np

from .paquet import Paquet


class SegmenteurTroncs(Protocol):
    nom: str

    def disponible(self) -> bool: ...

    def masques(self, paquet: Paquet) -> dict[int, np.ndarray]: ...


class SansSegmentation:
    nom = "aucune"

    def disponible(self) -> bool:
        return True

    def masques(self, paquet: Paquet) -> dict[int, np.ndarray]:
        return {}


class SegmenteurSAM2:
    """SAM 2 adapter (to be validated on real captures, see PLAN.md)."""

    nom = "sam2"

    def __init__(self, configuration: str = "sam2.1_hiera_s.yaml", poids: str = "sam2.1_hiera_small.pt"):
        self.configuration = configuration
        self.poids = poids

    def disponible(self) -> bool:
        try:
            import sam2  # noqa: F401
            import torch
        except ImportError:
            return False
        return torch.cuda.is_available()

    def masques(self, paquet: Paquet) -> dict[int, np.ndarray]:
        import cv2
        from sam2.build_sam import build_sam2
        from sam2.sam2_image_predictor import SAM2ImagePredictor

        pred = SAM2ImagePredictor(build_sam2(self.configuration, self.poids))
        out = {}
        for img in paquet.manifeste.images:
            bgr = paquet.image_bgr(img)
            if bgr is None or img.profondeur is None:
                continue
            d = paquet.profondeur(img)
            # Prompts: pixels in the middle band of the frame whose depth is
            # well defined and lower than their row median (stems stand out
            # in front of the background).
            h, w = d.shape
            bande = d[h // 3 : 2 * h // 3]
            med = np.nanmedian(bande, axis=1, keepdims=True)
            v, u = np.nonzero(np.nan_to_num(bande, nan=np.inf) < 0.8 * med)
            if len(u) == 0:
                continue
            sel = np.random.default_rng(img.id).choice(len(u), min(16, len(u)), replace=False)
            sx, sy = bgr.shape[1] / w, bgr.shape[0] / h
            pts = np.column_stack([(u[sel] + 0.5) * sx, (v[sel] + h // 3 + 0.5) * sy])
            pred.set_image(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
            m, _, _ = pred.predict(point_coords=pts, point_labels=np.ones(len(pts)), multimask_output=False)
            masque = cv2.resize(m[0].astype(np.uint8), (w, h), interpolation=cv2.INTER_NEAREST).astype(bool)
            # Keep the ground: everything below the horizon line stays.
            masque[2 * h // 3 :] = True
            out[img.id] = masque
        return out
