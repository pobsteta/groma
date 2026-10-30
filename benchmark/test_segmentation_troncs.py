import json
import sys
from pathlib import Path

import cv2
import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).parent))
import segmentation_troncs as st

H, W = 60, 80


def rect(x0, x1, y0=5, y1=55):
    m = np.zeros((H, W), dtype=bool)
    m[y0:y1, x0:x1] = True
    return m


def verite(*troncs, ignorer=None):
    return st.Verite(1, list(troncs), np.zeros((H, W), bool) if ignorer is None else ignorer)


def test_prediction_parfaite():
    v = verite(rect(10, 20), rect(40, 55))
    r = st.evaluer_image(v, [rect(40, 55), rect(10, 20)], 0.1)
    assert (r.vrais_positifs, r.faux_positifs) == (2, 0)
    assert r.iou_apparies == [1.0, 1.0]
    s = st.synthese([r])
    assert s["rappel"] == 1.0 and s["precision"] == 1.0 and s["iou_pixels"] == 1.0 and s["oublis"] == 0


def test_tronc_oublie_et_faux_positif():
    v = verite(rect(10, 20), rect(40, 55))
    r = st.evaluer_image(v, [rect(10, 20), rect(65, 75)], 0.1)
    s = st.synthese([r])
    assert (r.vrais_positifs, r.faux_positifs) == (1, 1)
    assert s["rappel"] == 0.5 and s["precision"] == 0.5 and s["corrections_par_image"] == 2.0


def test_troncs_fusionnes_comptent_une_seule_fois():
    # Two close stems predicted as one blob (IoU 10/22 with each): both
    # missed and one false positive, although the pixels are well covered.
    v = verite(rect(10, 20), rect(22, 32))
    r = st.evaluer_image(v, [rect(10, 32)], 0.1)
    assert (r.vrais_positifs, r.faux_positifs) == (0, 1)
    s = st.synthese([r])
    assert s["rappel"] == 0.0 and s["iou_pixels"] > 0.9


def test_masque_trop_large_sous_le_seuil_iou():
    v = verite(rect(10, 20))
    r = st.evaluer_image(v, [rect(0, 40)], 0.1)  # IoU = 10/40
    assert r.vrais_positifs == 0 and r.faux_positifs == 1
    assert st.evaluer_image(v, [rect(0, 40)], 0.1, seuil_iou=0.2).vrais_positifs == 1


def test_zone_ignoree_ne_compte_pas():
    ignorer = rect(60, 80, 0, H)
    v = verite(rect(10, 20), ignorer=ignorer)
    # Background stem predicted inside the ignored area: neither TP nor FP.
    r = st.evaluer_image(v, [rect(10, 20), rect(65, 75)], 0.1)
    assert (r.vrais_positifs, r.faux_positifs) == (1, 0)
    assert r.pixels_predits == r.pixels_verite


def test_taille_differente_refusee():
    with pytest.raises(ValueError):
        st.evaluer_image(verite(rect(10, 20)), [np.zeros((10, 10), bool)], 0.1)


def test_temps_median_sans_echauffement():
    rs = [st.evaluer_image(verite(rect(10, 20)), [rect(10, 20)], t) for t in (5.0, 1.0, 2.0, 3.0)]
    s = st.synthese(rs, images_par_placette=60)
    assert s["temps_median_s"] == 2.0 and s["temps_placette_min"] == 2.0 and s["temps_total_s"] == 11.0


@pytest.mark.parametrize("dtype,code", [(np.uint8, 255), (np.uint16, 65535)])
def test_lire_masques_png(tmp_path, dtype, code):
    lab = np.zeros((H, W), dtype=dtype)
    lab[rect(10, 20)] = 1
    lab[rect(40, 55)] = 7
    lab[:, 70:] = code
    cv2.imwrite(str(tmp_path / "000042.png"), lab)
    v = st.lire_masques(tmp_path)[42]
    assert len(v.troncs) == 2 and v.ignorer[:, 70:].all() and not v.ignorer[:, :70].any()


def test_lire_coco_polygone_rle_ignorer(tmp_path):
    rle_plat = rect(40, 55).T.ravel()  # column major
    counts, val, n = [], False, 0
    for b in rle_plat:
        if b != val:
            counts.append(n)
            val, n = b, 0
        n += 1
    counts.append(n)
    coco = {
        "categories": [{"id": 1, "name": "tronc"}, {"id": 2, "name": "ignorer"}],
        "images": [{"id": 9, "file_name": "images/000123.jpg", "width": W, "height": H}],
        "annotations": [
            {"image_id": 9, "category_id": 1, "segmentation": [[10, 5, 20, 5, 20, 55, 10, 55]]},
            {"image_id": 9, "category_id": 1, "segmentation": {"size": [H, W], "counts": counts}},
            {"image_id": 9, "category_id": 2, "segmentation": [[70, 0, 79, 0, 79, 59, 70, 59]]},
        ],
    }
    p = tmp_path / "instances.json"
    p.write_text(json.dumps(coco))
    v = st.lire_coco(p)[123]
    assert len(v.troncs) == 2
    assert np.array_equal(v.troncs[1], rect(40, 55))
    assert v.troncs[0][30, 15] and not v.troncs[0][30, 30]
    assert v.ignorer[30, 75] and not v.ignorer[30, 50]


def test_composantes():
    m = rect(10, 20) | rect(40, 55) | rect(70, 71, 0, 3)  # last one too small
    assert len(st.composantes(m)) == 2


# -- end to end on a small capture package --------------------------------


def _paquet(tmp_path, n=6):
    from groma_server.paquet import (
        Centre,
        Image,
        Intrinseques,
        Manifeste,
        ecrire_paquet,
    )

    k = Intrinseques(fx=60, fy=60, cx=W / 2, cy=H / 2, largeur=W, hauteur=H)
    images, fichiers = [], {}
    for i in range(n):
        img = np.full((H, W, 3), 90, np.uint8)
        img[rect(10, 20)] = (40, 60, 80)
        fichiers[f"images/{i:06d}.jpg"] = cv2.imencode(".jpg", img)[1].tobytes()
        images.append(Image(id=i, t=float(i), pose=list(np.eye(4).ravel()), image=f"images/{i:06d}.jpg", intrinseques=k))
    m = Manifeste(plot_id="P001", centre=Centre(x=0, y=0), images=images)
    return ecrire_paquet(tmp_path / "P001.groma.zip", m, fichiers)


class Faux:
    nom = "faux"

    def __init__(self):
        self.charge = False

    def charger(self):
        self.charge = True

    def segmenter(self, trame):
        assert self.charge and trame.rgb.shape == (H, W, 3)
        return [rect(10, 20)] if trame.image_id % 2 == 0 else []

    def description(self):
        return {"modele": "faux"}


def test_extraire_puis_evaluer(tmp_path, monkeypatch, capsys):
    paquet = _paquet(tmp_path)
    annot = tmp_path / "annot"
    images = st.extraire(paquet, annot, nombre=3)
    assert [p.name for p in images] == ["000000.jpg", "000002.jpg", "000005.jpg"]
    for p in images:
        lab = np.zeros((H, W), np.uint8)
        lab[rect(10, 20)] = 1
        cv2.imwrite(str(annot / "masques" / p.with_suffix(".png").name), lab)

    monkeypatch.setattr(st, "fabriquer_segmenteur", lambda nom, requete: Faux())
    sortie = tmp_path / "rapport.json"
    st.main(["evaluer", "--paquet", str(paquet), "--masques", str(annot / "masques"), "--segmenteur", "faux",
             "--sortie", str(sortie), "--apercus", str(tmp_path / "apercus")])
    r = json.loads(sortie.read_text())
    s = r["segmenteurs"]["faux"]
    assert r["troncs_annotes"] == 3 and s["vrais_positifs"] == 2 and s["oublis"] == 1  # image 5 is odd
    assert len(list((tmp_path / "apercus" / "faux").glob("*.jpg"))) == 3
    assert "faux" in capsys.readouterr().out


def test_segmenteur_absent_signale(tmp_path, monkeypatch):
    paquet = _paquet(tmp_path, n=2)
    masques = tmp_path / "m"
    masques.mkdir()
    cv2.imwrite(str(masques / "000000.png"), np.zeros((H, W), np.uint8))

    class Absent(Faux):
        nom = "absent"

        def charger(self):
            raise ImportError("No module named 'wevisone_vl'")

    monkeypatch.setattr(st, "fabriquer_segmenteur", lambda nom, requete: Absent())
    sortie = tmp_path / "r.json"
    st.main(["evaluer", "--paquet", str(paquet), "--masques", str(masques), "--segmenteur", "wevisone-4b",
             "--sortie", str(sortie)])
    assert "non installé" in json.loads(sortie.read_text())["segmenteurs"]["wevisone-4b"]["erreur"]


def test_fabriquer_segmenteur():
    assert st.fabriquer_segmenteur("wevisone-2b").modele == "tencent/WeVisOne-VL-2B"
    assert st.fabriquer_segmenteur("sam3", "trunk").requete == "trunk"
    with pytest.raises(ValueError):
        st.fabriquer_segmenteur("yolo")
