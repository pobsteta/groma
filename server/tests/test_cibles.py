import numpy as np
import pytest

cv2 = pytest.importorskip("cv2")

from groma_server.cibles import detecter_aruco, trianguler
from groma_server.paquet import Paquet


def test_detection_aruco_identifiant_confirme():
    dico = cv2.aruco.getPredefinedDictionary(cv2.aruco.DICT_4X4_50)
    img = np.full((480, 640), 255, np.uint8)
    img[100:260, 200:360] = cv2.aruco.generateImageMarker(dico, 2, 160)
    img[300:380, 450:530] = cv2.aruco.generateImageMarker(dico, 17, 80)  # not a Groma target
    det = detecter_aruco(cv2.cvtColor(img, cv2.COLOR_GRAY2BGR), image_id=5)
    assert [(d.id_cible, d.image) for d in det] == [("C2", 5)]
    assert det[0].u == pytest.approx(280, abs=1) and det[0].v == pytest.approx(180, abs=1)


def test_triangulation_cibles(paquet_synthetique):
    chemin, sc = paquet_synthetique
    p = Paquet.ouvrir(chemin)
    try:
        cibles = trianguler(p, p.manifeste.detections_cibles)
    finally:
        p.fermer()
    assert set(cibles) == {"C0", "C1", "C2", "C3"}
    c0 = cibles["C0"].position
    assert c0[:2] == pytest.approx(sc.c0_local, abs=0.02)
    assert all(c.rmse_m < 0.05 and c.n_vues >= 3 for c in cibles.values())
