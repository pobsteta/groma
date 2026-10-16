"""Build a minimal QField test project around an inventaire_video.gpkg.

Prototype only: the real QField project is built by nemeton
(``creer_projet_inventaire_video()``, brief § 2.5). This one exists to try the
project plugin ``inventaire_video.qml`` on a phone before that.

Usage (PyQGIS required, run with the system Python that ships QGIS)::

    python3 qfield/creer_projet_test.py <dossier du projet>

The folder must contain ``inventaire_video.gpkg``. The script writes
``inventaire_video.qgs`` and copies the plugin as ``inventaire_video.qml``
next to it, so QField loads it as a project plugin.
"""

from __future__ import annotations

import argparse
import logging
import os
import shutil
import sys
from pathlib import Path

os.environ.setdefault("QT_QPA_PLATFORM", "offscreen")

from qgis.core import (  # noqa: E402
    QgsApplication,
    QgsCoordinateReferenceSystem,
    QgsEditorWidgetSetup,
    QgsProject,
    QgsVectorLayer,
)

sys.path.insert(0, str(Path(__file__).parent))
import symbologie  # noqa: E402

log = logging.getLogger("groma.qfield")

PLUGIN = Path(__file__).with_name("inventaire_video.qml")

# Drawing order, top first. Missing layers (older GeoPackages) are skipped.
COUCHES = ["detections", "arbres", "cibles", "centres_mesures", "placettes", "houppiers", "parcelles", "ugf", "projet"]

# Fields QField must not edit in forms (layer edits by the plugin still work).
LECTURE_SEULE = {
    "detections": None,  # whole layer
    "arbres": ["uuid", "espece", "num_provisoire", "azimut", "distance_m", "houppier_id", "methode"],
    "centres_mesures": ["ecart_prevu_m"],
}


def couches_du_gpkg(gpkg: Path) -> list[str]:
    import sqlite3

    with sqlite3.connect(gpkg) as db:
        return [r[0] for r in db.execute("SELECT table_name FROM gpkg_contents")]


def creer(dossier: Path) -> Path:
    gpkg = dossier / "inventaire_video.gpkg"
    if not gpkg.is_file():
        raise SystemExit(f"{gpkg} introuvable.")
    presentes = set(couches_du_gpkg(gpkg))
    projet = QgsProject.instance()
    projet.clear()
    projet.setCrs(QgsCoordinateReferenceSystem("EPSG:2154"))
    projet.setTitle("Inventaire vidéo (test du plugin Groma)")
    # Relative paths: QField imports the folder elsewhere.
    projet.writeEntry("Paths", "/Absolute", False)
    for nom in reversed(COUCHES):
        if nom not in presentes:
            log.info("Couche absente, ignorée : %s", nom)
            continue
        couche = QgsVectorLayer(f"{gpkg}|layername={nom}", nom, "ogr")
        if not couche.isValid():
            raise SystemExit(f"Couche invalide : {nom}")
        champs = LECTURE_SEULE.get(nom, [])
        cfg = couche.editFormConfig()
        for i, f in enumerate(couche.fields()):
            if f.name() == "fid":
                couche.setEditorWidgetSetup(i, QgsEditorWidgetSetup("Hidden", {}))
            if champs is None or f.name() in champs:
                cfg.setReadOnly(i, True)
        couche.setEditFormConfig(cfg)
        symbologie.appliquer(couche)
        projet.addMapLayer(couche)
        if nom in symbologie.MASQUEES:
            projet.layerTreeRoot().findLayer(couche.id()).setItemVisibilityChecked(False)
    symbologie.ajouter_fonds(projet)
    qgs = dossier / "inventaire_video.qgs"
    if not projet.write(str(qgs)):
        raise SystemExit(f"Échec de l'écriture de {qgs}")
    shutil.copyfile(PLUGIN, dossier / "inventaire_video.qml")
    log.info("Projet QField écrit : %s (+ plugin inventaire_video.qml)", qgs)
    return qgs


def main(argv: list[str] | None = None) -> int:
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("dossier", type=Path, help="dossier contenant inventaire_video.gpkg")
    a = p.parse_args(argv)
    app = QgsApplication([], False)
    app.initQgis()
    try:
        creer(a.dossier)
    finally:
        app.exitQgis()
    return 0


if __name__ == "__main__":
    sys.exit(main())
