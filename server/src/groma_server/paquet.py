"""Capture package of one plot (``<plot_id>.groma.zip``), built by the phone.

Layout::

    manifest.json
    images/000000.jpg          key frames (optional for the depth backend)
    profondeurs/000000.u16     ARCore depth, uint16 little endian, millimetres
    confiances/000000.u8       ARCore depth confidence (0-255), optional
    lidar/mnt.tif, lidar/mnh.tif, lidar/houppiers.geojson   optional

Frames and poses follow ARCore conventions: ``pose`` is the 4x4
world_from_camera matrix (row major), world frame Y up (gravity), camera
looking along -Z with +Y up (OpenGL). Depth is the distance along the
camera axis. The local horizontal frame used by Groma is
``x = X_world, y = -Z_world, z = Y_world``.
"""

from __future__ import annotations

import io
import json
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np
from pydantic import BaseModel, Field

FORMAT = "groma-paquet"
VERSION = 1

# world (ARCore, Y up) -> Groma local (z up)
MONDE_VERS_LOCAL = np.array([[1, 0, 0, 0], [0, 0, -1, 0], [0, 1, 0, 0], [0, 0, 0, 1]], dtype=float)


class Intrinseques(BaseModel):
    fx: float
    fy: float
    cx: float
    cy: float
    largeur: int
    hauteur: int


class Image(BaseModel):
    id: int
    t: float
    pose: list[float] = Field(min_length=16, max_length=16)
    image: str | None = None
    intrinseques: Intrinseques | None = None
    profondeur: str | None = None
    confiance: str | None = None
    intrinseques_profondeur: Intrinseques | None = None
    etat_suivi: str = "TRACKING"


class CibleMesuree(BaseModel):
    id_cible: str
    azimut_mesure: float
    distance_mesuree_m: float
    dbh_mesure_cm: float | None = None


class DetectionCible(BaseModel):
    image: int
    id_cible: str  # C0..C3
    u: float
    v: float


class Centre(BaseModel):
    x: float
    y: float
    qualite_gnss: str | None = None
    precision_h_m: float | None = None


class ParametresPlacette(BaseModel):
    rayon_m: float = 20.0
    seuil_diam_cm: float = 7.5
    unite_azimut: str = "deg"
    declinaison_deg: float = 0.0
    convergence_deg: float = 0.0


class Suivi(BaseModel):
    perte_suivi_s: float = 0.0
    boucle_fermee: bool = False


class Lidar(BaseModel):
    mnt: str | None = None
    mnh: str | None = None
    houppiers: str | None = None


class Manifeste(BaseModel):
    format: str = FORMAT
    version: int = VERSION
    plot_id: str
    date: str | None = None
    appareil: dict[str, Any] = Field(default_factory=dict)
    placette: ParametresPlacette = Field(default_factory=ParametresPlacette)
    centre: Centre
    hauteur_c0_m: float = 1.30
    cibles_mesurees: list[CibleMesuree] = Field(default_factory=list)
    images: list[Image]
    detections_cibles: list[DetectionCible] = Field(default_factory=list)
    suivi: Suivi = Field(default_factory=Suivi)
    lidar: Lidar = Field(default_factory=Lidar)


@dataclass
class Paquet:
    manifeste: Manifeste
    _zip: zipfile.ZipFile

    @classmethod
    def ouvrir(cls, chemin: str | Path) -> "Paquet":
        z = zipfile.ZipFile(chemin)
        m = Manifeste.model_validate_json(z.read("manifest.json"))
        if m.format != FORMAT or m.version != VERSION:
            raise ValueError(f"Format de paquet non pris en charge : {m.format} v{m.version}")
        return cls(m, z)

    def fermer(self) -> None:
        self._zip.close()

    def lire(self, nom: str) -> bytes:
        return self._zip.read(nom)

    def existe(self, nom: str | None) -> bool:
        return nom is not None and nom in self._zip.namelist()

    def extraire(self, nom: str, dossier: Path) -> Path:
        return Path(self._zip.extract(nom, dossier))

    def profondeur(self, img: Image) -> np.ndarray:
        """Depth map in metres (NaN where invalid)."""
        k = img.intrinseques_profondeur
        brut = np.frombuffer(self.lire(img.profondeur), dtype="<u2").reshape(k.hauteur, k.largeur)
        d = brut.astype(float) / 1000.0
        d[brut == 0] = np.nan
        return d

    def confiance(self, img: Image) -> np.ndarray | None:
        if not self.existe(img.confiance):
            return None
        k = img.intrinseques_profondeur
        return np.frombuffer(self.lire(img.confiance), dtype=np.uint8).reshape(k.hauteur, k.largeur)

    def image_bgr(self, img: Image) -> np.ndarray | None:
        if not self.existe(img.image):
            return None
        import cv2

        return cv2.imdecode(np.frombuffer(self.lire(img.image), np.uint8), cv2.IMREAD_COLOR)


def pose_locale(img: Image) -> np.ndarray:
    """4x4 local_from_camera (local frame z up)."""
    return MONDE_VERS_LOCAL @ np.asarray(img.pose, dtype=float).reshape(4, 4)


def ecrire_paquet(chemin: str | Path, manifeste: Manifeste, fichiers: dict[str, bytes]) -> Path:
    chemin = Path(chemin)
    with zipfile.ZipFile(chemin, "w", compression=zipfile.ZIP_DEFLATED) as z:
        z.writestr("manifest.json", manifeste.model_dump_json(indent=1))
        for nom, contenu in fichiers.items():
            z.writestr(nom, contenu)
    return chemin
