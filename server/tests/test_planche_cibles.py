import re

import numpy as np
import pytest

cv2 = pytest.importorskip("cv2")

from groma_server.cibles import detecter_aruco
from groma_server.planche_cibles import A4_H_MM, A4_L_MM, COTE_MM, MARGE_MM, MODULE_MM, ecrire_pdf, page


def _raster(identifiant: int, px_mm: float = 2.0) -> np.ndarray:
    rects, _ = page(identifiant)
    img = np.full((int(A4_H_MM * px_mm), int(A4_L_MM * px_mm)), 255, np.uint8)
    for r in rects:
        img[round(r.y * px_mm) : round((r.y + r.h) * px_mm), round(r.x * px_mm) : round((r.x + r.l) * px_mm)] = 0
    return img


@pytest.mark.parametrize("identifiant,nom", [(0, "C0"), (1, "C1"), (2, "C2"), (3, "C3")])
def test_page_decodee_avec_le_bon_identifiant(identifiant, nom):
    img = _raster(identifiant)
    det = detecter_aruco(cv2.cvtColor(img, cv2.COLOR_GRAY2BGR))
    assert [d.id_cible for d in det] == [nom]


def test_marge_blanche_d_au_moins_un_module():
    assert MARGE_MM >= MODULE_MM
    assert MARGE_MM * 2 + COTE_MM <= A4_L_MM
    for ident in range(4):
        rects, _ = page(ident)
        motif = [r for r in rects if r.l == MODULE_MM]
        bas = max(r.y + r.h for r in motif)
        autres = [r for r in rects if r.l != MODULE_MM]
        assert min(r.y for r in autres) >= bas + MODULE_MM


def test_pdf_quatre_pages_a4(tmp_path):
    pdf = ecrire_pdf(tmp_path / "cibles.pdf").read_bytes()
    assert pdf.startswith(b"%PDF-1.4") and pdf.rstrip().endswith(b"%%EOF")
    assert b"/Count 4" in pdf
    assert len(re.findall(rb"/MediaBox \[0 0 595\.276 841\.890\]", pdf)) == 4
    xref = int(re.search(rb"startxref\n(\d+)", pdf).group(1))
    assert pdf[xref : xref + 4] == b"xref"
