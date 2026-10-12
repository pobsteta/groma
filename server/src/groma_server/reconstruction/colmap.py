"""COLMAP reconstruction initialised with the ARCore poses.

The ARCore poses are written as a known sparse model (cameras.txt,
images.txt, empty points3D.txt); COLMAP then extracts and matches features,
triangulates with the poses fixed (``point_triangulator``), and densifies
(``patch_match_stereo`` needs CUDA, then ``stereo_fusion``). The result is in
the ARCore world frame, hence already metric and gravity-aligned.

COLMAP is BSD-3-Clause. It must be installed on the worker (Docker image
built with CUDA for the dense step).
"""

from __future__ import annotations

import logging
import shutil
import struct
import subprocess
from pathlib import Path

import numpy as np

from ..paquet import MONDE_VERS_LOCAL, Paquet
from .base import Nuage, sous_echantillonner

log = logging.getLogger(__name__)

# OpenGL camera (ARCore) -> OpenCV camera (COLMAP): flip y and z.
GL_VERS_CV = np.diag([1.0, -1.0, -1.0, 1.0])


def quaternion(r: np.ndarray) -> tuple[float, float, float, float]:
    """Rotation matrix -> (qw, qx, qy, qz), COLMAP convention."""
    t = np.trace(r)
    if t > 0:
        s = 0.5 / np.sqrt(t + 1.0)
        return (0.25 / s, (r[2, 1] - r[1, 2]) * s, (r[0, 2] - r[2, 0]) * s, (r[1, 0] - r[0, 1]) * s)
    i = int(np.argmax(np.diag(r)))
    j, k = (i + 1) % 3, (i + 2) % 3
    s = 2.0 * np.sqrt(1.0 + r[i, i] - r[j, j] - r[k, k])
    q = [0.0, 0.0, 0.0, 0.0]
    q[0] = (r[k, j] - r[j, k]) / s
    q[i + 1] = 0.25 * s
    q[j + 1] = (r[j, i] + r[i, j]) / s
    q[k + 1] = (r[k, i] + r[i, k]) / s
    return tuple(q)  # type: ignore[return-value]


def pose_colmap(pose_monde_camera_gl: np.ndarray) -> tuple[tuple[float, ...], np.ndarray]:
    """ARCore world_from_camera (OpenGL) -> COLMAP camera_from_world (q, t)."""
    cam_cv_monde = GL_VERS_CV @ np.linalg.inv(pose_monde_camera_gl)
    return quaternion(cam_cv_monde[:3, :3]), cam_cv_monde[:3, 3]


def ecrire_modele_connu(paquet: Paquet, dossier: Path) -> list[str]:
    """Write the known-pose sparse model; returns the image file names."""
    dossier.mkdir(parents=True, exist_ok=True)
    images = [i for i in paquet.manifeste.images if i.image and i.intrinseques]
    cam_lignes, img_lignes, noms = [], [], []
    for n, img in enumerate(images, start=1):
        k = img.intrinseques
        cam_lignes.append(f"{n} PINHOLE {k.largeur} {k.hauteur} {k.fx} {k.fy} {k.cx} {k.cy}")
        q, t = pose_colmap(np.asarray(img.pose).reshape(4, 4))
        nom = Path(img.image).name
        noms.append(nom)
        img_lignes.append(f"{n} {' '.join(f'{v:.10f}' for v in q)} {' '.join(f'{v:.10f}' for v in t)} {n} {nom}\n")
    (dossier / "cameras.txt").write_text("\n".join(cam_lignes) + "\n")
    (dossier / "images.txt").write_text("".join(img_lignes))
    (dossier / "points3D.txt").write_text("")
    return noms


def commandes(colmap: str, travail: Path, dense: bool = True) -> list[list[str]]:
    base = travail / "base.db"
    img = travail / "images"
    connu = travail / "modele_connu"
    epars = travail / "epars"
    dense_dir = travail / "dense"
    cmds = [
        [colmap, "feature_extractor", "--database_path", str(base), "--image_path", str(img),
         "--ImageReader.camera_model", "PINHOLE"],
        [colmap, "sequential_matcher", "--database_path", str(base), "--SequentialMatching.overlap", "15"],
        [colmap, "point_triangulator", "--database_path", str(base), "--image_path", str(img),
         "--input_path", str(connu), "--output_path", str(epars)],
    ]
    if dense:
        cmds += [
            [colmap, "image_undistorter", "--image_path", str(img), "--input_path", str(epars), "--output_path", str(dense_dir)],
            [colmap, "patch_match_stereo", "--workspace_path", str(dense_dir), "--PatchMatchStereo.geom_consistency", "true"],
            [colmap, "stereo_fusion", "--workspace_path", str(dense_dir), "--output_path", str(dense_dir / "fused.ply")],
        ]
    return cmds


def lire_ply(chemin: Path) -> np.ndarray:
    """x, y, z of a PLY file (ASCII or binary little endian), other properties skipped."""
    with open(chemin, "rb") as f:
        entete = []
        while True:
            ligne = f.readline().decode("ascii").strip()
            entete.append(ligne)
            if ligne == "end_header":
                break
        fmt = next(l.split()[1] for l in entete if l.startswith("format"))
        n = int(next(l.split()[2] for l in entete if l.startswith("element vertex")))
        props = []
        dans_vertex = False
        for l in entete:
            if l.startswith("element"):
                dans_vertex = l.split()[1] == "vertex"
            elif l.startswith("property") and dans_vertex:
                _, typ, nom = l.split()[:3]
                props.append((nom, typ))
        if fmt == "ascii":
            data = np.loadtxt(f, max_rows=n, ndmin=2)
            idx = [i for i, (nom, _) in enumerate(props) if nom in ("x", "y", "z")]
            return data[:, idx]
        tailles = {"float": "f4", "float32": "f4", "double": "f8", "uchar": "u1", "uint8": "u1", "char": "i1",
                   "int": "i4", "int32": "i4", "uint": "u4", "short": "i2", "ushort": "u2"}
        dt = np.dtype([(nom, "<" + tailles[typ]) for nom, typ in props])
        brut = np.frombuffer(f.read(n * dt.itemsize), dtype=dt, count=n)
        return np.column_stack([brut["x"], brut["y"], brut["z"]]).astype(float)


def ecrire_ply(chemin: Path, points: np.ndarray) -> None:
    with open(chemin, "wb") as f:
        f.write(f"ply\nformat binary_little_endian 1.0\nelement vertex {len(points)}\n"
                "property float x\nproperty float y\nproperty float z\nend_header\n".encode())
        f.write(np.asarray(points, dtype="<f4").tobytes())


class ReconstructeurColmap:
    nom = "colmap"
    methode = "classique"

    def __init__(self, executable: str = "colmap", dense: bool = True, voxel_m: float = 0.005):
        self.executable = executable
        self.dense = dense
        self.voxel_m = voxel_m

    def disponible(self) -> bool:
        return shutil.which(self.executable) is not None

    def reconstruire(self, paquet: Paquet, dossier: Path, masques: dict[int, np.ndarray] | None = None) -> Nuage:
        travail = dossier / "colmap"
        (travail / "images").mkdir(parents=True, exist_ok=True)
        for img in paquet.manifeste.images:
            if img.image:
                (travail / "images" / Path(img.image).name).write_bytes(paquet.lire(img.image))
        ecrire_modele_connu(paquet, travail / "modele_connu")
        (travail / "epars").mkdir(exist_ok=True)
        for cmd in commandes(self.executable, travail, self.dense):
            log.info("COLMAP : %s", " ".join(cmd[:2]))
            subprocess.run(cmd, check=True, capture_output=True)
        ply = travail / "dense" / "fused.ply"
        if not ply.exists():
            raise RuntimeError("COLMAP n'a pas produit de nuage dense.")
        monde = lire_ply(ply)
        local = (np.column_stack([monde, np.ones(len(monde))]) @ MONDE_VERS_LOCAL.T)[:, :3]
        return Nuage(sous_echantillonner(local, self.voxel_m), self.methode, self.nom, {"n_points_bruts": len(monde)})
