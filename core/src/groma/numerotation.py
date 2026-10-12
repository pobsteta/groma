"""Tree numbering rule of the Groma protocol.

The rule is implemented identically in Python (here), Kotlin
(``android/groma-core``) and R (nemeton, see the brief). The shared test
cases live in ``fixtures/numerotation/``; the algorithm below is the
normative description, also reproduced in ``fixtures/numerotation/README.md``.

Input positions are horizontal offsets from the plot centre, in metres,
with ``x`` towards east and ``y`` towards **geographic** north (use
:func:`offsets_geographiques` to rotate Lambert-93 grid offsets).

Algorithm
---------
1. *Eligible* trees ``E``: horizontal distance ``<= rayon_m`` and
   ``dbh_cm >= seuil_diam_cm`` (both inclusive, with a 1e-9 slack).
2. *Visible* trees ``V`` within ``E``: tree ``i`` is hidden when another
   eligible tree ``j`` closer to the centre has a trunk (disc of diameter
   ``dbh_j``) that the sight line from the centre to the centre of ``i``
   passes through (distance from the centre of ``j`` to the segment
   strictly lower than the radius of ``j``).
3. Tree no. 1: let ``az_min`` be the smallest azimuth in ``V`` and ``C``
   the visible trees with ``az - az_min < tolerance``. No. 1 is the
   member of ``C`` closest to the centre (ties: smaller azimuth, then id).
4. Every other eligible tree gets a relative azimuth
   ``rel = (az - az_1) mod 360``, except the other members of ``C`` which
   get ``rel = 0`` (they tie with no. 1). A hidden tree whose azimuth is
   lower than no. 1's therefore comes at the end of the turn.
5. Sort the other trees by ``(rel, distance, id)``, then sweep: a group
   starts at the first remaining tree and takes every following tree with
   ``rel - rel_first < tolerance``; within a group, trees are ordered by
   ``(distance, rel, id)``. Numbers follow that order, starting at 2.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Iterable, Sequence

from .azimut import Unite, azimut_deg, normaliser_deg, vers_unite

EPS = 1e-9


@dataclass(frozen=True)
class ArbreANumeroter:
    id: str
    x: float
    y: float
    dbh_cm: float


@dataclass(frozen=True)
class ParametresNumerotation:
    rayon_m: float = 20.0
    seuil_diam_cm: float = 7.5
    unite_azimut: Unite = "deg"
    tolerance_deg: float = 1.0


@dataclass
class ResultatNumerotation:
    # id -> tree_id ("1", "2", ...), eligible trees only, in numbering order.
    numeros: dict[str, str] = field(default_factory=dict)
    # id -> reason ("hors_rayon" or "sous_seuil") for non eligible trees.
    exclus: dict[str, str] = field(default_factory=dict)
    # ids of eligible trees visible from the centre.
    visibles: set[str] = field(default_factory=set)
    # id -> azimuth in the plot unit, and id -> horizontal distance (m).
    azimuts: dict[str, float] = field(default_factory=dict)
    distances: dict[str, float] = field(default_factory=dict)

    def ordre(self) -> list[str]:
        return sorted(self.numeros, key=lambda k: int(self.numeros[k]))


def offsets_geographiques(
    dx_grille: float, dy_grille: float, convergence_deg: float
) -> tuple[float, float]:
    """Rotate a Lambert-93 grid offset into the geographic-north frame.

    A vector of grid azimuth ``a`` has geographic azimuth ``a + convergence``:
    rotate it clockwise by ``convergence``.
    """
    g = math.radians(convergence_deg)
    c, s = math.cos(g), math.sin(g)
    return dx_grille * c + dy_grille * s, -dx_grille * s + dy_grille * c


def _distance_point_segment(px: float, py: float, bx: float, by: float) -> float:
    """Distance from P to the segment [O, B] where O is the origin."""
    l2 = bx * bx + by * by
    if l2 == 0.0:
        return math.hypot(px, py)
    t = max(0.0, min(1.0, (px * bx + py * by) / l2))
    return math.hypot(px - t * bx, py - t * by)


def est_masque(cible: ArbreANumeroter, autres: Iterable[ArbreANumeroter]) -> bool:
    """True when the sight line centre -> ``cible`` crosses a closer trunk."""
    d_cible = math.hypot(cible.x, cible.y)
    for a in autres:
        if a.id == cible.id:
            continue
        d_a = math.hypot(a.x, a.y)
        rayon_a = a.dbh_cm / 200.0
        if d_a >= d_cible or d_a <= rayon_a:
            # Not in front of the target, or the centre is inside that trunk.
            continue
        if _distance_point_segment(a.x, a.y, cible.x, cible.y) < rayon_a:
            return True
    return False


def numeroter(
    arbres: Sequence[ArbreANumeroter],
    params: ParametresNumerotation = ParametresNumerotation(),
) -> ResultatNumerotation:
    """Number the trees of one plot according to the Groma rule."""
    res = ResultatNumerotation()
    ids = [a.id for a in arbres]
    if len(set(ids)) != len(ids):
        raise ValueError("Identifiants d'arbres en double.")

    eligibles: list[ArbreANumeroter] = []
    az: dict[str, float] = {}
    dist: dict[str, float] = {}
    for a in arbres:
        d = math.hypot(a.x, a.y)
        dist[a.id] = d
        az[a.id] = azimut_deg(a.x, a.y)
        if d > params.rayon_m + EPS:
            res.exclus[a.id] = "hors_rayon"
        elif a.dbh_cm < params.seuil_diam_cm - EPS:
            res.exclus[a.id] = "sous_seuil"
        else:
            eligibles.append(a)

    for a in arbres:
        res.azimuts[a.id] = vers_unite(az[a.id], params.unite_azimut)
        res.distances[a.id] = dist[a.id]

    if not eligibles:
        return res

    visibles = [a for a in eligibles if not est_masque(a, eligibles)]
    res.visibles = {a.id for a in visibles}
    tol = params.tolerance_deg

    az_min = min(az[a.id] for a in visibles)
    candidats = [a for a in visibles if az[a.id] - az_min < tol]
    premier = min(candidats, key=lambda a: (dist[a.id], az[a.id], a.id))
    ids_candidats = {a.id for a in candidats}
    az1 = az[premier.id]

    def rel(a: ArbreANumeroter) -> float:
        if a.id in ids_candidats:
            return 0.0
        return normaliser_deg(az[a.id] - az1)

    autres = sorted(
        (a for a in eligibles if a.id != premier.id),
        key=lambda a: (rel(a), dist[a.id], a.id),
    )

    ordre = [premier]
    i = 0
    while i < len(autres):
        ancre = rel(autres[i])
        j = i
        while j < len(autres) and rel(autres[j]) - ancre < tol:
            j += 1
        groupe = sorted(autres[i:j], key=lambda a: (dist[a.id], rel(a), a.id))
        ordre.extend(groupe)
        i = j

    for rang, a in enumerate(ordre, start=1):
        res.numeros[a.id] = str(rang)
    return res
