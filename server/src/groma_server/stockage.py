"""Result and package storage: local directory, or S3-compatible bucket.

Any S3-compatible service works (``GROMA_S3_URL``). Licence note: MinIO
server is AGPL-3.0 (fine as an unmodified separate service, but flagged);
Garage (AGPL-3.0) and SeaweedFS (Apache-2.0) are alternatives.
"""

from __future__ import annotations

import shutil
from pathlib import Path
from typing import Protocol


class Stockage(Protocol):
    def deposer(self, local: Path, cle: str) -> str: ...

    def recuperer(self, cle: str, local: Path) -> Path: ...


class StockageLocal:
    def __init__(self, racine: Path):
        self.racine = Path(racine)
        self.racine.mkdir(parents=True, exist_ok=True)

    def _chemin(self, cle: str) -> Path:
        p = (self.racine / cle).resolve()
        if self.racine.resolve() not in p.parents:
            raise ValueError(f"Clé de stockage invalide : {cle!r}")
        return p

    def deposer(self, local: Path, cle: str) -> str:
        cible = self._chemin(cle)
        cible.parent.mkdir(parents=True, exist_ok=True)
        if Path(local).resolve() != cible:
            shutil.copyfile(local, cible)
        return cle

    def recuperer(self, cle: str, local: Path | None = None) -> Path:
        p = self._chemin(cle)
        if local is None:
            return p
        shutil.copyfile(p, local)
        return Path(local)


class StockageS3:
    def __init__(self, url: str, bucket: str):
        import boto3

        self.client = boto3.client("s3", endpoint_url=url)
        self.bucket = bucket

    def deposer(self, local: Path, cle: str) -> str:
        self.client.upload_file(str(local), self.bucket, cle)
        return cle

    def recuperer(self, cle: str, local: Path) -> Path:
        self.client.download_file(self.bucket, cle, str(local))
        return Path(local)
