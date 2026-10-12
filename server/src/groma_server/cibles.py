"""ArUco targets: detection in the key frames and multi-view triangulation.

The identifier is always confirmed by the classical ArUco decoding (OpenCV);
an AI detector may only propose candidate regions, never an identity.
Marker ids: C0 = 0 (RTK pole), C1 = 1, C2 = 2, C3 = 3, dictionary 4x4_50.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

import numpy as np

from .paquet import DetectionCible, Paquet, pose_locale

log = logging.getLogger(__name__)

IDENTIFIANTS = {0: "C0", 1: "C1", 2: "C2", 3: "C3"}


def detecter_aruco(image_bgr: np.ndarray, image_id: int = 0) -> list[DetectionCible]:
    import cv2

    dico = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_50)
    params = cv2.aruco.DetectorParameters()
    params.cornerRefinementMethod = cv2.aruco.CORNER_REFINE_SUBPIX
    coins, ids, _ = cv2.aruco.ArucoDetector(dico, params).detectMarkers(image_bgr)
    out = []
    if ids is None:
        return out
    for c, i in zip(coins, ids.ravel()):
        if int(i) in IDENTIFIANTS:
            u, v = c[0].mean(axis=0)
            out.append(DetectionCible(image=image_id, id_cible=IDENTIFIANTS[int(i)], u=float(u), v=float(v)))
    return out


@dataclass(frozen=True)
class CibleTriangulee:
    id_cible: str
    position: np.ndarray  # local frame (x, y, z)
    n_vues: int
    rmse_m: float


def _point_plus_proche(origines: np.ndarray, directions: np.ndarray) -> np.ndarray:
    a = np.zeros((3, 3))
    b = np.zeros(3)
    for o, d in zip(origines, directions):
        p = np.eye(3) - np.outer(d, d)
        a += p
        b += p @ o
    return np.linalg.solve(a, b)


def trianguler(paquet: Paquet, detections: list[DetectionCible], seuil_m: float = 0.05, vues_min: int = 3) -> dict[str, CibleTriangulee]:
    images = {i.id: i for i in paquet.manifeste.images}
    par_cible: dict[str, list[tuple[np.ndarray, np.ndarray]]] = {}
    for d in detections:
        img = images.get(d.image)
        if img is None or img.intrinseques is None:
            continue
        k = img.intrinseques
        pose = pose_locale(img)
        cam = np.array([(d.u - k.cx) / k.fx, -(d.v - k.cy) / k.fy, -1.0])
        direction = pose[:3, :3] @ cam
        par_cible.setdefault(d.id_cible, []).append((pose[:3, 3], direction / np.linalg.norm(direction)))

    out = {}
    for cible, rayons in par_cible.items():
        o = np.array([r[0] for r in rayons])
        dirs = np.array([r[1] for r in rayons])
        garde = np.ones(len(rayons), bool)
        for _ in range(5):
            if garde.sum() < vues_min:
                break
            x = _point_plus_proche(o[garde], dirs[garde])
            v = x - o
            dist = np.linalg.norm(v - (np.sum(v * dirs, axis=1)[:, None] * dirs), axis=1)
            nouveau = dist < max(seuil_m, 3 * np.median(dist[garde]))
            if (nouveau == garde).all():
                break
            garde = nouveau
        if garde.sum() < vues_min:
            log.warning("Cible %s : %d vues seulement, ignorée.", cible, int(garde.sum()))
            continue
        x = _point_plus_proche(o[garde], dirs[garde])
        v = x - o[garde]
        dist = np.linalg.norm(v - (np.sum(v * dirs[garde], axis=1)[:, None] * dirs[garde]), axis=1)
        out[cible] = CibleTriangulee(cible, x, int(garde.sum()), float(np.sqrt(np.mean(dist**2))))
    return out
