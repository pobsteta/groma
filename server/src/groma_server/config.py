"""Server configuration from environment variables (12-factor, ADR-010)."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path


@dataclass(frozen=True)
class Config:
    dossier_donnees: Path = field(default_factory=lambda: Path(os.environ.get("GROMA_DONNEES", "/var/lib/groma")))
    jeton: str | None = field(default_factory=lambda: os.environ.get("GROMA_JETON") or None)
    # Token of the remote GPU workers (routes /worker/...); unset: those routes are off.
    jeton_worker: str | None = field(default_factory=lambda: os.environ.get("GROMA_JETON_WORKER") or None)
    # A task taken by a worker is offered again when its lease is older than this.
    bail_s: int = field(default_factory=lambda: int(os.environ.get("GROMA_BAIL_S", "3600")))
    taille_morceau: int = field(default_factory=lambda: int(os.environ.get("GROMA_TAILLE_MORCEAU", str(8 * 1024 * 1024))))
    taille_max: int = field(default_factory=lambda: int(os.environ.get("GROMA_TAILLE_MAX", str(4 * 1024**3))))
    reconstructeurs: tuple[str, ...] = field(
        default_factory=lambda: tuple(os.environ.get("GROMA_RECONSTRUCTEURS", "colmap,vggt,profondeur").split(","))
    )
    taille_max_projet: int = field(default_factory=lambda: int(os.environ.get("GROMA_TAILLE_MAX_PROJET", str(500 * 1024**2))))
    versions_projet: int = field(default_factory=lambda: int(os.environ.get("GROMA_VERSIONS_PROJET", "3")))
    tentatives_max: int = field(default_factory=lambda: int(os.environ.get("GROMA_TENTATIVES_MAX", "2")))
    s3_url: str | None = field(default_factory=lambda: os.environ.get("GROMA_S3_URL") or None)
    s3_bucket: str = field(default_factory=lambda: os.environ.get("GROMA_S3_BUCKET", "groma"))

    @property
    def base(self) -> Path:
        return self.dossier_donnees / "taches.sqlite"
