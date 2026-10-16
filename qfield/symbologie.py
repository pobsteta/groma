"""Marculus-like symbology for the inventaire_video layers in QField.

Same visual language as Marculus' map (``ui/carte/CarteScreen.kt``) and as
Groma's own map (``carte/Couches.kt``), so the operator sees the same thing in
the three applications:

* ``arbres``: filled dot in the colour of the species group (Marculus'
  ``COULEURS_ESSENCES_DEFAUT``), white outline, size growing with the
  diameter; grey when the species is not entered yet;
* ``ugf``: Marculus' forest parcels, thick outline and 20 % fill from
  ``PALETTE_FONCIER`` (one colour per forest), parcel number at the centre;
* ``placettes``, ``centres_mesures``, ``cibles``, ``detections``,
  ``parcelles``: Groma's map styles.

Sizes are in millimetres (QField scales them to the screen density).
"""

from __future__ import annotations

import json
from pathlib import Path

from qgis.core import (
    QgsCategorizedSymbolRenderer,
    QgsFillSymbol,
    QgsMarkerSymbol,
    QgsPalLayerSettings,
    QgsProperty,
    QgsRendererCategory,
    QgsSingleSymbolRenderer,
    QgsSymbolLayer,
    QgsTextBufferSettings,
    QgsTextFormat,
    QgsVectorLayer,
    QgsVectorLayerSimpleLabeling,
)
from qgis.PyQt.QtGui import QColor, QFont

ESSENCES = Path(__file__).resolve().parents[1] / "core/src/groma/donnees/essences_bdforet_v2.json"

# Marculus, core/Referentiels.kt, COULEURS_ESSENCES_DEFAUT (groups of nemeton's stand types).
CHENE, HETRE, FEUILLU, SAPIN, EPICEA, RESINEUX = "#2a7fa6", "#5061b8", "#7e8e9c", "#94304d", "#c2502f", "#ae9381"
INCONNUE = "#888888"  # Marculus: species without colour

# Marculus, ui/carte/CarteScreen.kt, PALETTE_FONCIER.
PALETTE_FONCIER = ["#1e88e5", "#43a047", "#f4511e", "#8e24aa", "#fdd835", "#00acc1", "#6d4c41", "#ec407a"]
SANS_FORET = "#374742"

# Groma, carte/Couches.kt.
ETATS_PLACETTE = {"a_faire": ("#ffffff", "À faire"), "provisoire": ("#f57c00", "Provisoire"),
                  "envoye": ("#1976d2", "Envoyée"), "definitif": ("#2e7d32", "Définitive")}
PARCELLE = "#ffd54f"
CENTRE = "#d500f9"


def groupe_essence(code: str, bdforet: str) -> str:
    """Marculus colour of a species of essences_bdforet_v2.json."""
    if bdforet.startswith("Chênes"):
        return CHENE
    if bdforet == "Hêtre":
        return HETRE
    if code == "sapin_pectine":
        return SAPIN
    if code == "epicea_commun":
        return EPICEA
    if bdforet in ("NC", ""):
        return INCONNUE
    feuillus = {"Châtaignier", "Robinier", "Peuplier", "Feuillus"}
    return FEUILLU if bdforet in feuillus else RESINEUX


def _pastille(couleur: str, taille_mm: float = 3.0, contour: str = "#ffffff", forme: str = "circle") -> QgsMarkerSymbol:
    return QgsMarkerSymbol.createSimple({
        "name": forme, "color": couleur, "outline_color": contour,
        "outline_width": "0.5", "size": str(taille_mm), "size_unit": "MM",
    })


def _taille(sym: QgsMarkerSymbol, expression: str) -> QgsMarkerSymbol:
    """Data-defined size (mm), set before the symbol goes into a category (categories are copies)."""
    sym.symbolLayer(0).setDataDefinedProperty(QgsSymbolLayer.Property.Size, QgsProperty.fromExpression(expression))
    return sym


def _etiquette(layer: QgsVectorLayer, expression: str, taille: float = 9, zoom_max_echelle: float = 0) -> None:
    fmt = QgsTextFormat()
    fmt.setFont(QFont("Sans"))
    fmt.setSize(taille)
    fmt.setColor(QColor("#212121"))
    tampon = QgsTextBufferSettings()
    tampon.setEnabled(True)
    tampon.setSize(1)
    tampon.setColor(QColor("#ffffff"))
    fmt.setBuffer(tampon)
    s = QgsPalLayerSettings()
    s.fieldName = expression
    s.isExpression = True
    s.setFormat(fmt)
    if zoom_max_echelle:
        # Labels only when zoomed in (as Groma's map, from zoom 15).
        s.scaleVisibility = True
        s.minimumScale = zoom_max_echelle
        s.maximumScale = 0
    layer.setLabeling(QgsVectorLayerSimpleLabeling(s))
    layer.setLabelsEnabled(True)


def arbres(layer: QgsVectorLayer) -> None:
    essences = json.loads(ESSENCES.read_text(encoding="utf-8"))["essences"]
    # Marculus: dot size proportional to the diameter class (24 to 64 px).
    taille = 'scale_linear(coalesce("dbh_cm", 7.5), 7.5, 80, 2.4, 6.4)'
    cats = []
    for e in essences:
        sym = _taille(_pastille(groupe_essence(e["code"], e["bdforet"])), taille)
        cats.append(QgsRendererCategory(e["code"], sym, e["libelle"]))
    cats.append(QgsRendererCategory(None, _taille(_pastille(INCONNUE), taille), "Essence à saisir"))
    layer.setRenderer(QgsCategorizedSymbolRenderer("essence_arbre", cats))
    _etiquette(layer, "\"tree_id\" || ' – ' || format_number(\"dbh_cm\", 0)", 8, 1000)


def ugf(layer: QgsVectorLayer) -> None:
    forets = sorted({f["foret"] for f in layer.getFeatures() if f["foret"]})
    cats = []
    for i, nom in enumerate(forets):
        cats.append(QgsRendererCategory(nom, _surface(PALETTE_FONCIER[i % len(PALETTE_FONCIER)]), nom))
    cats.append(QgsRendererCategory(None, _surface(SANS_FORET), "Sans forêt"))
    layer.setRenderer(QgsCategorizedSymbolRenderer("foret", cats))
    _etiquette(layer, "coalesce(\"parcelle_onf\", \"libelle\", \"ug_id\")", 10)


def _surface(couleur: str, epaisseur_mm: float = 0.8, opacite_fond: int = 0x33) -> QgsFillSymbol:
    c = QColor(couleur)
    c.setAlpha(opacite_fond)
    return QgsFillSymbol.createSimple({
        "color": f"{c.red()},{c.green()},{c.blue()},{c.alpha()}",
        "outline_color": couleur, "outline_width": str(epaisseur_mm), "outline_width_unit": "MM",
    })


def parcelles(layer: QgsVectorLayer) -> None:
    layer.setRenderer(QgsSingleSymbolRenderer(_surface(PARCELLE, 0.4, 0x22)))
    _etiquette(layer, "\"section\" || ' ' || \"numero\"", 7, 5000)


def placettes(layer: QgsVectorLayer) -> None:
    # Replacement plots (type Over) smaller, as on Groma's map.
    taille = "if(\"type\" = 'Over', 2.4, 3.5)"
    cats = [QgsRendererCategory(k, _taille(_pastille(c, 3.5, "#212121"), taille), lib) for k, (c, lib) in ETATS_PLACETTE.items()]
    cats.append(QgsRendererCategory(None, _taille(_pastille("#ffffff", 3.5, "#212121"), taille), "À faire"))
    layer.setRenderer(QgsCategorizedSymbolRenderer("etat_traitement", cats))
    _etiquette(layer, "\"plot_id\"", 9, 25000)


def centres_mesures(layer: QgsVectorLayer) -> None:
    sym = QgsMarkerSymbol.createSimple({"name": "cross2", "color": CENTRE, "outline_color": CENTRE,
                                        "outline_width": "0.6", "size": "3.5", "size_unit": "MM"})
    layer.setRenderer(QgsSingleSymbolRenderer(sym))


def cibles(layer: QgsVectorLayer) -> None:
    layer.setRenderer(QgsSingleSymbolRenderer(_pastille("#ff6f00", 2.6, "#212121", "square")))
    _etiquette(layer, "\"id_cible\"", 8, 1000)


def detections(layer: QgsVectorLayer) -> None:
    # Hollow ring: the provisional detection under the tree entered in `arbres`.
    sym = QgsMarkerSymbol.createSimple({"name": "circle", "color": "0,0,0,0", "outline_color": "#f57c00",
                                        "outline_width": "0.4", "size": "4", "size_unit": "MM"})
    layer.setRenderer(QgsSingleSymbolRenderer(sym))


STYLES = {"arbres": arbres, "ugf": ugf, "parcelles": parcelles, "placettes": placettes,
          "centres_mesures": centres_mesures, "cibles": cibles, "detections": detections}


def appliquer(layer: QgsVectorLayer) -> None:
    """Applies the style of the layer named like ``layer`` (no-op for others)."""
    f = STYLES.get(layer.name())
    if f is not None:
        f(layer)
