"""3D reconstruction backends behind one interface (step 2 of the pipeline).

* ``profondeur``: fusion of the ARCore depth maps with the ARCore poses
  (no GPU; also the reference for the on-phone provisional pass);
* ``colmap``: classical photogrammetry, COLMAP initialised with the ARCore
  poses (known-pose triangulation, then dense MVS);
* ``vggt``: direct AI reconstruction (VGGT or equivalent), by overlapping
  chunks glued back together and scaled on the ARCore trajectory.
"""

from .base import Nuage, Reconstructeur
from .colmap import ReconstructeurColmap
from .profondeur import ReconstructeurProfondeur
from .vggt import ReconstructeurVGGT

REGISTRE: dict[str, type] = {
    "profondeur": ReconstructeurProfondeur,
    "colmap": ReconstructeurColmap,
    "vggt": ReconstructeurVGGT,
}

__all__ = ["Nuage", "Reconstructeur", "REGISTRE", "ReconstructeurColmap", "ReconstructeurProfondeur", "ReconstructeurVGGT"]
