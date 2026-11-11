"""Printable A4 sheets of the Groma ArUco targets (C0-C3, dictionary 4x4_50).

One target per page, vector PDF written by hand (no PDF dependency): the
marker is drawn as black rectangles in millimetres, so the print is exact at
100 % scale. A 10 cm bar lets the operator check that the printer did not
rescale the page.

Layout (portrait A4, 210 x 297 mm): 6-module marker (4 data + 1 black border
on each side) with a white quiet zone of at least one module. The largest
whole-millimetre module that fits the sheet width with that quiet zone is
26 mm; 25 mm is kept for round figures (marker 150 mm, quiet zone 30 mm).

Usage : python -m groma_server.planche_cibles [sortie.pdf]
"""

from __future__ import annotations

import argparse
import logging
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from .cibles import IDENTIFIANTS

log = logging.getLogger(__name__)

A4_L_MM, A4_H_MM = 210.0, 297.0
MODULE_MM = 25.0
MODULES = 6  # 4 x 4 data bits + one black border module on each side
COTE_MM = MODULE_MM * MODULES
MARGE_MM = (A4_L_MM - COTE_MM) / 2  # white quiet zone on the sides (30 mm)
HAUT_MM = MARGE_MM  # marker top, measured from the top edge of the sheet
ECHELLE_MM = 100.0

USAGES = {
    "C0": "Sur la canne du récepteur RTK, à 1,30 m du sol.",
    "C1": "Agrafer à 1,30 m sur un arbre, face au centre de la placette.",
    "C2": "Agrafer à 1,30 m sur un arbre, face au centre de la placette.",
    "C3": "Agrafer à 1,30 m sur un arbre, face au centre de la placette.",
}


@dataclass(frozen=True)
class Rectangle:
    """Filled black rectangle, millimetres, origin at the top-left of the sheet."""

    x: float
    y: float
    l: float
    h: float


@dataclass(frozen=True)
class Texte:
    x: float
    y: float  # baseline, from the top of the sheet
    taille_pt: float
    contenu: str
    gras: bool = False


def bits_marqueur(identifiant: int) -> np.ndarray:
    """6 x 6 cells of the marker (True = black), border included."""
    import cv2

    dico = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_50)
    img = cv2.aruco.generateImageMarker(dico, identifiant, MODULES, borderBits=1)
    return img < 128


def page(identifiant: int) -> tuple[list[Rectangle], list[Texte]]:
    nom = IDENTIFIANTS[identifiant]
    bits = bits_marqueur(identifiant)
    rects = [
        Rectangle(MARGE_MM + c * MODULE_MM, HAUT_MM + r * MODULE_MM, MODULE_MM, MODULE_MM)
        for r in range(MODULES)
        for c in range(MODULES)
        if bits[r, c]
    ]
    y0 = HAUT_MM + COTE_MM + MODULE_MM + 10  # below the bottom quiet zone
    x0 = MARGE_MM
    textes = [
        Texte(x0, y0, 28, f"Groma · cible {nom}", gras=True),
        Texte(x0, y0 + 9, 11, f"ArUco 4x4_50, identifiant {identifiant}. {USAGES[nom]}"),
        Texte(x0, y0 + 15, 9, f"Côté du motif : {COTE_MM / 10:.1f} cm (module de {MODULE_MM / 10:.1f} cm).".replace(".", ",", 2)),
        Texte(x0, y0 + 20, 9, "Imprimer à 100 % (taille réelle, sans ajustement), noir sur papier blanc mat."),
        Texte(x0, y0 + 25, 9, "Ne rien écrire ni agrafer dans la marge blanche autour du motif. Plastifier mat."),
        Texte(x0, y0 + 37, 9, "Contrôle d'échelle : ce segment doit mesurer 10,0 cm."),
    ]
    yb = y0 + 46
    rects.append(Rectangle(x0, yb, ECHELLE_MM, 1.0))
    for i in range(11):  # ticks every centimetre
        rects.append(Rectangle(x0 + i * 10 - 0.15 if i else x0, yb - (2.5 if i % 5 else 4.0), 0.3, 2.5 if i % 5 else 4.0))
    return rects, textes


def _pt(mm: float) -> float:
    return mm * 72.0 / 25.4


def _chaine_pdf(s: str) -> bytes:
    b = s.encode("cp1252")
    return b"(" + b.replace(b"\\", b"\\\\").replace(b"(", b"\\(").replace(b")", b"\\)") + b")"


def _contenu(rects: list[Rectangle], textes: list[Texte]) -> bytes:
    h = A4_H_MM
    lignes = [b"0 g"]
    for r in rects:
        lignes.append(f"{_pt(r.x):.3f} {_pt(h - r.y - r.h):.3f} {_pt(r.l):.3f} {_pt(r.h):.3f} re f".encode())
    for t in textes:
        police = b"/F2" if t.gras else b"/F1"
        lignes.append(
            b"BT " + police + f" {t.taille_pt} Tf {_pt(t.x):.3f} {_pt(h - t.y):.3f} Td ".encode() + _chaine_pdf(t.contenu) + b" Tj ET"
        )
    return b"\n".join(lignes) + b"\n"


def ecrire_pdf(chemin: Path, identifiants: list[int] | None = None) -> Path:
    """Write one A4 page per target (all Groma targets by default)."""
    identifiants = sorted(IDENTIFIANTS) if identifiants is None else identifiants
    n = len(identifiants)
    # Objects: 1 catalog, 2 pages, 3-4 fonts, then (page, content) per target.
    objets: dict[int, bytes] = {
        1: b"<< /Type /Catalog /Pages 2 0 R >>",
        3: b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
        4: b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>",
    }
    kids = []
    for k, ident in enumerate(identifiants):
        no_page, no_flux = 5 + 2 * k, 6 + 2 * k
        kids.append(f"{no_page} 0 R")
        flux = _contenu(*page(ident))
        objets[no_flux] = f"<< /Length {len(flux)} >>\nstream\n".encode() + flux + b"endstream"
        objets[no_page] = (
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 {_pt(A4_L_MM):.3f} {_pt(A4_H_MM):.3f}] "
            f"/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents {no_flux} 0 R >>"
        ).encode()
    objets[2] = f"<< /Type /Pages /Kids [{' '.join(kids)}] /Count {n} >>".encode()

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    decalages = {}
    for no in sorted(objets):
        decalages[no] = len(out)
        out += f"{no} 0 obj\n".encode() + objets[no] + b"\nendobj\n"
    xref = len(out)
    total = max(objets) + 1
    out += f"xref\n0 {total}\n0000000000 65535 f \n".encode()
    for no in range(1, total):
        out += f"{decalages[no]:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {total} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()

    chemin = Path(chemin)
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_bytes(bytes(out))
    log.info("Planche de %d cible(s) écrite : %s", n, chemin)
    return chemin


def main(argv: list[str] | None = None) -> None:
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    ap = argparse.ArgumentParser(description="Planche A4 des cibles ArUco Groma (C0-C3), une cible par page.")
    ap.add_argument("sortie", nargs="?", default="cibles_groma_a4.pdf", type=Path)
    ecrire_pdf(ap.parse_args(argv).sortie)


if __name__ == "__main__":
    main()
