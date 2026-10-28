"""Trunk segmentation bench: SAM 2 (current adapter), SAM 3 and WeVisOne-VL.

Compares, on hand-annotated key frames of a real plot, how many trunks each
model finds, how well its masks cover them, and what it costs per image.
The 3D pipeline only uses the masks to keep trunk pixels, the diameters
come from the circle fit, so the instance recall matters more than the
precision of the edges.

Two sub-commands::

    # 1. pick evenly spaced key frames of a capture package, to annotate
    python benchmark/segmentation_troncs.py extraire --paquet P001.groma.zip \
        --sortie P001_annot --nombre 50

    # 2. evaluate the segmenters against the annotations
    python benchmark/segmentation_troncs.py evaluer --paquet P001.groma.zip \
        --masques P001_annot/masques --segmenteur sam2 --segmenteur sam3 \
        --segmenteur wevisone-4b --sortie P001_segmentation.json --apercus P001_apercus

Annotations: either one PNG per frame (``masques/000123.png``, same size as
the image, 0 = background, 1..N = one trunk each, 255 for 8-bit or 65535
for 16-bit = area to ignore), or a COCO file (``--coco``) whose category
``tronc`` gives the trunks and ``ignorer`` (or ``iscrowd = 1``) the areas
to ignore; images are matched on the frame id in ``file_name``.

The models are optional and only imported when requested; each runs on a
GPU machine (see benchmark/README.md for the environments).
"""

from __future__ import annotations

import argparse
import json
import logging
import platform
import re
import statistics
import sys
import time
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

import numpy as np

log = logging.getLogger("groma.benchmark.segmentation")

SEUIL_IOU = 0.5
REQUETE = "tree trunk"


# --------------------------------------------------------------------------
# Ground truth
# --------------------------------------------------------------------------


@dataclass
class Verite:
    """Annotated trunks of one frame."""

    image_id: int
    troncs: list[np.ndarray]  # one boolean mask per trunk
    ignorer: np.ndarray  # boolean, pixels excluded from every count

    @property
    def forme(self) -> tuple[int, int]:
        return self.ignorer.shape


def _id_image(nom: str) -> int:
    m = re.search(r"(\d+)", Path(nom).stem)
    if m is None:
        raise ValueError(f"Identifiant d'image introuvable dans « {nom} »")
    return int(m.group(1))


def verite_depuis_etiquettes(image_id: int, etiquettes: np.ndarray) -> Verite:
    """Instance label image (0 background, 1..N trunks, dtype max = ignore)."""
    if etiquettes.ndim != 2:
        raise ValueError("Le masque d'annotation doit être en niveaux de gris (une étiquette par pixel)")
    code_ignorer = np.iinfo(etiquettes.dtype).max
    ignorer = etiquettes == code_ignorer
    ids = [int(v) for v in np.unique(etiquettes) if v != 0 and v != code_ignorer]
    return Verite(image_id, [etiquettes == v for v in ids], ignorer)


def lire_masques(dossier: Path) -> dict[int, Verite]:
    import cv2

    out: dict[int, Verite] = {}
    for chemin in sorted(Path(dossier).glob("*.png")):
        etiquettes = cv2.imread(str(chemin), cv2.IMREAD_UNCHANGED)
        if etiquettes is None:
            raise ValueError(f"Masque illisible : {chemin}")
        i = _id_image(chemin.name)
        out[i] = verite_depuis_etiquettes(i, etiquettes)
    if not out:
        raise ValueError(f"Aucun masque PNG dans {dossier}")
    return out


def _rle_vers_masque(rle: dict, h: int, w: int) -> np.ndarray:
    counts = rle["counts"]
    if isinstance(counts, str):  # compressed RLE: needs pycocotools
        from pycocotools import mask as mask_utils

        return mask_utils.decode({"size": [h, w], "counts": counts.encode()}).astype(bool)
    plat = np.zeros(h * w, dtype=bool)
    pos, valeur = 0, False
    for n in counts:
        plat[pos : pos + n] = valeur
        pos += n
        valeur = not valeur
    return plat.reshape((w, h)).T  # COCO RLE is column major


def _segmentation_vers_masque(seg, h: int, w: int) -> np.ndarray:
    import cv2

    if isinstance(seg, dict):
        return _rle_vers_masque(seg, h, w)
    m = np.zeros((h, w), dtype=np.uint8)
    for poly in seg:
        pts = np.round(np.asarray(poly, dtype=float).reshape(-1, 2)).astype(np.int32)
        cv2.fillPoly(m, [pts], 1)
    return m.astype(bool)


def lire_coco(chemin: Path) -> dict[int, Verite]:
    """COCO instance file (CVAT, Label Studio…): ``tronc`` and ``ignorer`` categories."""
    data = json.loads(Path(chemin).read_text(encoding="utf-8"))
    cat = {c["id"]: c["name"].strip().lower() for c in data.get("categories", [])}
    images = {im["id"]: im for im in data["images"]}
    out = {
        _id_image(im["file_name"]): Verite(
            _id_image(im["file_name"]), [], np.zeros((im["height"], im["width"]), dtype=bool)
        )
        for im in images.values()
    }
    for a in data.get("annotations", []):
        im = images[a["image_id"]]
        v = out[_id_image(im["file_name"])]
        m = _segmentation_vers_masque(a["segmentation"], im["height"], im["width"])
        nom = cat.get(a["category_id"], "")
        if a.get("iscrowd", 0) or nom in {"ignorer", "ignore"}:
            v.ignorer |= m
        elif nom in {"tronc", "trunk", "tree trunk"}:
            v.troncs.append(m)
        else:
            log.warning("Catégorie COCO « %s » ignorée", nom)
    return out


# --------------------------------------------------------------------------
# Metrics
# --------------------------------------------------------------------------


@dataclass
class ResultatImage:
    image_id: int
    troncs_verite: int
    troncs_predits: int
    vrais_positifs: int
    faux_positifs: int
    iou_apparies: list[float]
    pixels_inter: int
    pixels_verite: int
    pixels_predits: int
    temps_s: float


def apparier(verite: Verite, predits: list[np.ndarray], seuil_iou: float = SEUIL_IOU) -> tuple[list[tuple[int, int, float]], int]:
    """One-to-one matching of predicted and annotated trunks.

    IoU computed outside the ignored area, Hungarian assignment, pairs
    below ``seuil_iou`` rejected. Returns the pairs (truth, prediction,
    IoU) and the number of false positives: unmatched predictions, except
    those lying mostly (> 50 %) in the ignored area.
    """
    from scipy.optimize import linear_sum_assignment

    garder = ~verite.ignorer
    v = [t & garder for t in verite.troncs]
    p = [m & garder for m in predits]
    paires: list[tuple[int, int, float]] = []
    if v and p:
        iou = np.zeros((len(v), len(p)))
        for i, a in enumerate(v):
            sa = a.sum()
            for j, b in enumerate(p):
                inter = np.logical_and(a, b).sum()
                if inter:
                    iou[i, j] = inter / (sa + b.sum() - inter)
        lignes, colonnes = linear_sum_assignment(-iou)
        paires = [(int(i), int(j), float(iou[i, j])) for i, j in zip(lignes, colonnes) if iou[i, j] >= seuil_iou]
    apparies = {j for _, j, _ in paires}
    fp = 0
    for j, m in enumerate(predits):
        if j in apparies:
            continue
        n = m.sum()
        if n == 0 or (m & verite.ignorer).sum() > 0.5 * n:
            continue
        fp += 1
    return paires, fp


def evaluer_image(verite: Verite, predits: list[np.ndarray], temps_s: float, seuil_iou: float = SEUIL_IOU) -> ResultatImage:
    predits = [np.asarray(m, dtype=bool) for m in predits]
    for m in predits:
        if m.shape != verite.forme:
            raise ValueError(f"Masque prédit {m.shape} ≠ annotation {verite.forme} (image {verite.image_id})")
    paires, fp = apparier(verite, predits, seuil_iou)
    garder = ~verite.ignorer
    union_v = np.zeros(verite.forme, dtype=bool)
    for t in verite.troncs:
        union_v |= t
    union_p = np.zeros(verite.forme, dtype=bool)
    for m in predits:
        union_p |= m
    union_v &= garder
    union_p &= garder
    return ResultatImage(
        image_id=verite.image_id,
        troncs_verite=len(verite.troncs),
        troncs_predits=len(predits),
        vrais_positifs=len(paires),
        faux_positifs=fp,
        iou_apparies=[iou for _, _, iou in paires],
        pixels_inter=int((union_v & union_p).sum()),
        pixels_verite=int(union_v.sum()),
        pixels_predits=int(union_p.sum()),
        temps_s=temps_s,
    )


def _ratio(a: float, b: float) -> float | None:
    return a / b if b else None


def synthese(resultats: list[ResultatImage], images_par_placette: int = 300) -> dict:
    """Aggregated scores of one segmenter over all frames."""
    tv = sum(r.troncs_verite for r in resultats)
    tp = sum(r.vrais_positifs for r in resultats)
    fp = sum(r.faux_positifs for r in resultats)
    fn = tv - tp
    rappel = _ratio(tp, tv)
    precision = _ratio(tp, tp + fp)
    f1 = 2 * rappel * precision / (rappel + precision) if rappel and precision else 0.0
    inter = sum(r.pixels_inter for r in resultats)
    pv = sum(r.pixels_verite for r in resultats)
    pp = sum(r.pixels_predits for r in resultats)
    ious = [i for r in resultats for i in r.iou_apparies]
    # The first frame carries the warm-up (CUDA kernels, caches): left out
    # of the median when there are enough frames.
    temps = [r.temps_s for r in resultats]
    temps_regime = temps[1:] if len(temps) > 2 else temps
    t_med = statistics.median(temps_regime) if temps_regime else None
    return {
        "images": len(resultats),
        "troncs_verite": tv,
        "vrais_positifs": tp,
        "faux_positifs": fp,
        "oublis": fn,
        "rappel": rappel,
        "precision": precision,
        "f1": f1,
        "iou_moyen_apparies": statistics.fmean(ious) if ious else None,
        "iou_pixels": _ratio(inter, pv + pp - inter),
        "rappel_pixels": _ratio(inter, pv),
        "precision_pixels": _ratio(inter, pp),
        "corrections_par_image": _ratio(fn + fp, len(resultats)),
        "temps_median_s": t_med,
        "temps_total_s": sum(temps),
        "temps_placette_min": t_med * images_par_placette / 60 if t_med is not None else None,
    }


# --------------------------------------------------------------------------
# Segmenters
# --------------------------------------------------------------------------


@dataclass
class Trame:
    """One frame given to a segmenter."""

    image_id: int
    rgb: np.ndarray  # H x W x 3, uint8
    profondeur: np.ndarray | None = None  # ARCore depth in metres, own resolution


class SegmenteurBanc(Protocol):
    nom: str

    def charger(self) -> None: ...

    def segmenter(self, trame: Trame) -> list[np.ndarray]:
        """One boolean mask per trunk, at the frame resolution."""
        ...

    def description(self) -> dict: ...


def composantes(masque: np.ndarray, aire_min: int = 50) -> list[np.ndarray]:
    """Split a binary mask into connected components (instances)."""
    import cv2

    n, lab = cv2.connectedComponents(masque.astype(np.uint8), connectivity=8)
    out = [lab == k for k in range(1, n)]
    return [m for m in out if m.sum() >= aire_min]


@dataclass
class SegmenteurSam2Profondeur:
    """Current production adapter (points on the ARCore depth discontinuities).

    It returns one binary mask per frame: trunks are its connected
    components, so two touching stems count as one.
    """

    configuration: str = "sam2.1_hiera_s.yaml"
    poids: str = "sam2.1_hiera_small.pt"
    nom: str = "sam2"
    _base: object = None
    _pred: object = None

    def charger(self) -> None:
        from groma_server.segmentation import SegmenteurSAM2

        self._base = SegmenteurSAM2(self.configuration, self.poids)
        self._pred = self._base.charger()

    def segmenter(self, trame: Trame) -> list[np.ndarray]:
        import cv2

        if trame.profondeur is None:
            raise ValueError("SAM 2 (adaptateur Groma) a besoin de la profondeur ARCore de l'image")
        bgr = cv2.cvtColor(trame.rgb, cv2.COLOR_RGB2BGR)
        m = self._base.masque_troncs(self._pred, bgr, trame.profondeur, trame.image_id)
        return [] if m is None else composantes(m)

    def description(self) -> dict:
        return {"modele": "SAM 2.1", "poids": self.poids, "invite": "points sur les ruptures de profondeur"}


@dataclass
class SegmenteurSam3:
    """SAM 3 through transformers, prompted with a text concept."""

    modele: str = "facebook/sam3"
    requete: str = REQUETE
    seuil: float = 0.3
    nom: str = "sam3"
    _model: object = None
    _proc: object = None
    _device: str = "cpu"

    def charger(self) -> None:
        import torch
        from transformers import Sam3Model, Sam3Processor

        self._device = "cuda" if torch.cuda.is_available() else "cpu"
        self._proc = Sam3Processor.from_pretrained(self.modele)
        self._model = Sam3Model.from_pretrained(self.modele).to(self._device).eval()

    def segmenter(self, trame: Trame) -> list[np.ndarray]:
        import torch
        from PIL import Image as PILImage

        image = PILImage.fromarray(trame.rgb)
        entrees = self._proc(images=image, text=self.requete, return_tensors="pt").to(self._device)
        with torch.inference_mode():
            sorties = self._model(**entrees)
        res = self._proc.post_process_instance_segmentation(
            sorties, threshold=self.seuil, mask_threshold=0.5, target_sizes=[trame.rgb.shape[:2]]
        )[0]
        return [m.cpu().numpy().astype(bool) for m in res["masks"]]

    def description(self) -> dict:
        return {"modele": self.modele, "requete": self.requete, "seuil_score": self.seuil}


@dataclass
class SegmenteurWeVisOne:
    """WeVisOne-VL (Tencent), task ``segmentation`` with a text query."""

    modele: str = "tencent/WeVisOne-VL-4B"
    requete: str = REQUETE
    backend: str = "transformers"
    nom: str = "wevisone-4b"
    _model: object = None

    def charger(self) -> None:
        from wevisone_vl import WeVisOneVL

        self._model = WeVisOneVL(self.modele, backend=self.backend)

    def segmenter(self, trame: Trame) -> list[np.ndarray]:
        from PIL import Image as PILImage

        res = self._model.predict(
            PILImage.fromarray(trame.rgb), task="segmentation", query=self.requete,
            max_new_tokens=4096, temperature=0.0,
        )
        # Masks are probabilities at the image resolution.
        return [np.asarray(m) > 0.5 for m in res.masks]

    def description(self) -> dict:
        return {"modele": self.modele, "requete": self.requete, "backend": self.backend}


def fabriquer_segmenteur(nom: str, requete: str = REQUETE) -> SegmenteurBanc:
    if nom == "sam2":
        return SegmenteurSam2Profondeur()
    if nom == "sam3":
        return SegmenteurSam3(requete=requete)
    if nom in {"wevisone-4b", "wevisone-2b"}:
        taille = nom.split("-")[1].upper()
        return SegmenteurWeVisOne(modele=f"tencent/WeVisOne-VL-{taille}", requete=requete, nom=nom)
    raise ValueError(f"Segmenteur inconnu : {nom} (sam2, sam3, wevisone-4b, wevisone-2b)")


# --------------------------------------------------------------------------
# Frames from a capture package
# --------------------------------------------------------------------------


def trames_du_paquet(chemin_paquet: Path, ids: Iterable[int]) -> Iterable[Trame]:
    import cv2
    from groma_server.paquet import Paquet

    voulus = set(ids)
    paquet = Paquet.ouvrir(chemin_paquet)
    try:
        trouves = set()
        for img in paquet.manifeste.images:
            if img.id not in voulus:
                continue
            bgr = paquet.image_bgr(img)
            if bgr is None:
                log.warning("Image %d sans JPEG dans le paquet, ignorée", img.id)
                continue
            d = paquet.profondeur(img) if paquet.existe(img.profondeur) else None
            trouves.add(img.id)
            yield Trame(img.id, cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB), d)
        manquants = voulus - trouves
        if manquants:
            log.warning("%d image(s) annotée(s) absente(s) du paquet : %s", len(manquants), sorted(manquants)[:10])
    finally:
        paquet.fermer()


def extraire(chemin_paquet: Path, sortie: Path, nombre: int) -> list[Path]:
    """Copy ``nombre`` evenly spaced key frames (with a JPEG) for annotation."""
    from groma_server.paquet import Paquet

    paquet = Paquet.ouvrir(chemin_paquet)
    try:
        images = [i for i in paquet.manifeste.images if paquet.existe(i.image) and i.etat_suivi == "TRACKING"]
        if not images:
            raise ValueError("Le paquet ne contient aucune image clé JPEG")
        rang = np.unique(np.linspace(0, len(images) - 1, min(nombre, len(images))).round().astype(int))
        dossier = Path(sortie) / "images"
        dossier.mkdir(parents=True, exist_ok=True)
        (Path(sortie) / "masques").mkdir(exist_ok=True)
        out = []
        for k in rang:
            img = images[k]
            p = dossier / f"{img.id:06d}.jpg"
            p.write_bytes(paquet.lire(img.image))
            out.append(p)
        return out
    finally:
        paquet.fermer()


# --------------------------------------------------------------------------
# Run
# --------------------------------------------------------------------------


def _vram():
    try:
        import torch
    except ImportError:
        return None
    return torch if torch.cuda.is_available() else None


def executer(
    segmenteur: SegmenteurBanc,
    verites: dict[int, Verite],
    trames: Callable[[], Iterable[Trame]],
    seuil_iou: float = SEUIL_IOU,
    apercus: Path | None = None,
) -> tuple[list[ResultatImage], dict]:
    """Run one segmenter on every annotated frame; times exclude model loading."""
    torch = _vram()
    t0 = time.perf_counter()
    segmenteur.charger()
    chargement = time.perf_counter() - t0
    if torch is not None:
        torch.cuda.reset_peak_memory_stats()
    resultats = []
    for trame in trames():
        v = verites[trame.image_id]
        if trame.rgb.shape[:2] != v.forme:
            raise ValueError(f"Image {trame.image_id} : {trame.rgb.shape[:2]} ≠ annotation {v.forme}")
        if torch is not None:
            torch.cuda.synchronize()
        t = time.perf_counter()
        predits = segmenteur.segmenter(trame)
        if torch is not None:
            torch.cuda.synchronize()
        dt = time.perf_counter() - t
        r = evaluer_image(v, predits, dt, seuil_iou)
        resultats.append(r)
        log.info("%s image %d : %d/%d troncs, %d faux positifs, %.2f s", segmenteur.nom, r.image_id,
                 r.vrais_positifs, r.troncs_verite, r.faux_positifs, dt)
        if apercus is not None:
            ecrire_apercu(Path(apercus) / segmenteur.nom / f"{trame.image_id:06d}.jpg", trame.rgb, v, predits)
    extra = {"chargement_s": chargement, "vram_max_go": torch.cuda.max_memory_allocated() / 1e9 if torch else None}
    return resultats, extra


def ecrire_apercu(chemin: Path, rgb: np.ndarray, verite: Verite, predits: list[np.ndarray]) -> None:
    """Overlay for a visual check: predictions filled, truth outlined in white, ignored area dimmed."""
    import cv2

    chemin.parent.mkdir(parents=True, exist_ok=True)
    img = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR).astype(float)
    img[verite.ignorer] *= 0.4
    rng = np.random.default_rng(0)
    for m in predits:
        couleur = rng.integers(60, 255, 3).astype(float)
        img[m] = 0.5 * img[m] + 0.5 * couleur
    img = img.astype(np.uint8)
    for t in verite.troncs:
        contours, _ = cv2.findContours(t.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        cv2.drawContours(img, contours, -1, (255, 255, 255), 2)
    cv2.imwrite(str(chemin), img)


def _versions() -> dict:
    out = {"python": platform.python_version()}
    for mod in ("torch", "transformers", "sam2", "wevisone_vl"):
        try:
            out[mod] = getattr(__import__(mod), "__version__", "installé")
        except ImportError:
            pass
    torch = _vram()
    if torch is not None:
        out["gpu"] = torch.cuda.get_device_name(0)
    return out


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Banc d'essai de la segmentation des troncs")
    sous = ap.add_subparsers(dest="commande", required=True)

    e = sous.add_parser("extraire", help="extraire des images clés à annoter")
    e.add_argument("--paquet", type=Path, required=True)
    e.add_argument("--sortie", type=Path, required=True)
    e.add_argument("--nombre", type=int, default=50)

    v = sous.add_parser("evaluer", help="comparer les segmenteurs aux annotations")
    v.add_argument("--paquet", type=Path, required=True)
    src = v.add_mutually_exclusive_group(required=True)
    src.add_argument("--masques", type=Path, help="dossier de PNG d'étiquettes (000123.png)")
    src.add_argument("--coco", type=Path, help="fichier COCO (catégories tronc / ignorer)")
    v.add_argument("--segmenteur", action="append", required=True,
                   help="sam2, sam3, wevisone-4b ou wevisone-2b (option répétable)")
    v.add_argument("--requete", default=REQUETE, help=f"invite texte de SAM 3 et WeVisOne (défaut : {REQUETE!r})")
    v.add_argument("--seuil-iou", type=float, default=SEUIL_IOU)
    v.add_argument("--images-par-placette", type=int, default=300,
                   help="pour estimer le temps de segmentation d'une placette")
    v.add_argument("--sortie", type=Path, required=True, help="rapport JSON")
    v.add_argument("--apercus", type=Path, help="dossier des superpositions à contrôler à l'œil")

    a = ap.parse_args(argv)
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")

    if a.commande == "extraire":
        images = extraire(a.paquet, a.sortie, a.nombre)
        print(f"{len(images)} images dans {a.sortie / 'images'} ; masques attendus dans {a.sortie / 'masques'}")
        return 0

    verites = lire_masques(a.masques) if a.masques else lire_coco(a.coco)
    rapport = {"paquet": str(a.paquet), "images_annotees": len(verites),
               "troncs_annotes": sum(len(v.troncs) for v in verites.values()),
               "seuil_iou": a.seuil_iou, "versions": _versions(), "segmenteurs": {}}
    for nom in a.segmenteur:
        seg = fabriquer_segmenteur(nom, a.requete)
        try:
            resultats, extra = executer(seg, verites, lambda: trames_du_paquet(a.paquet, verites), a.seuil_iou, a.apercus)
        except ImportError as err:
            log.error("%s non installé (%s) : ignoré", nom, err)
            rapport["segmenteurs"][nom] = {"erreur": f"non installé : {err}"}
            continue
        s = synthese(resultats, a.images_par_placette)
        rapport["segmenteurs"][nom] = {**seg.description(), **extra, **s,
                                       "par_image": [r.__dict__ for r in resultats]}
    a.sortie.write_text(json.dumps(rapport, indent=1, ensure_ascii=False), encoding="utf-8")
    imprimer_tableau(rapport)
    return 0


def _f(x, fmt: str) -> str:
    return "—" if x is None else format(x, fmt)


def imprimer_tableau(rapport: dict) -> None:
    print(f"\n{rapport['images_annotees']} images, {rapport['troncs_annotes']} troncs annotés, IoU ≥ {rapport['seuil_iou']}")
    print(f"{'segmenteur':<14}{'rappel':>8}{'précision':>11}{'IoU app.':>10}{'IoU pix.':>10}"
          f"{'corr./img':>11}{'s/image':>9}{'min/plac.':>11}{'VRAM Go':>9}")
    for nom, s in rapport["segmenteurs"].items():
        if "erreur" in s:
            print(f"{nom:<14}{s['erreur']}")
            continue
        print(f"{nom:<14}{_f(s['rappel'], '.1%'):>8}{_f(s['precision'], '.1%'):>11}"
              f"{_f(s['iou_moyen_apparies'], '.2f'):>10}{_f(s['iou_pixels'], '.2f'):>10}"
              f"{_f(s['corrections_par_image'], '.1f'):>11}{_f(s['temps_median_s'], '.2f'):>9}"
              f"{_f(s['temps_placette_min'], '.1f'):>11}{_f(s['vram_max_go'], '.1f'):>9}")


if __name__ == "__main__":
    sys.exit(main())
