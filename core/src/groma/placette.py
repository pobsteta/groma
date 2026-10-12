"""From georeferenced trunks to rows of the ``detections`` layer.

Shared by the server (definitive pass) and mirrored by the phone (provisional
pass): numbering, azimuth in the plot unit, horizontal distance, height from
the crowns, detection state.
"""

from __future__ import annotations

import uuid as uuidlib
from dataclasses import dataclass
from typing import Sequence

from .houppiers import Houppier, affecter_hauteurs
from .numerotation import ArbreANumeroter, ParametresNumerotation, numeroter, offsets_geographiques


@dataclass(frozen=True)
class ParametresPlacette:
    plot_id: str
    centre: tuple[float, float]  # measured centre, Lambert-93
    rayon_m: float = 20.0
    seuil_diam_cm: float = 7.5
    unite_azimut: str = "deg"
    declinaison_deg: float = 0.0
    convergence_deg: float = 0.0

    @property
    def numerotation(self) -> ParametresNumerotation:
        return ParametresNumerotation(self.rayon_m, self.seuil_diam_cm, self.unite_azimut)  # type: ignore[arg-type]

    @classmethod
    def depuis_ligne(cls, ligne: dict, centre: Sequence[float]) -> "ParametresPlacette":
        return cls(
            plot_id=ligne["plot_id"],
            centre=(float(centre[0]), float(centre[1])),
            rayon_m=float(ligne.get("rayon_m") or 20.0),
            seuil_diam_cm=float(ligne.get("seuil_diam_cm") or 7.5),
            unite_azimut=ligne.get("unite_azimut") or "deg",
            declinaison_deg=float(ligne.get("declinaison_deg") or 0.0),
            convergence_deg=float(ligne.get("convergence_deg") or 0.0),
        )


def construire_detections(
    troncs: Sequence[dict],
    params: ParametresPlacette,
    houppiers: Sequence[Houppier] = (),
    methode: str = "classique",
    etat: str = "definitif",
    garder_hors_inventaire: bool = False,
) -> list[dict]:
    """``troncs``: dicts with x, y (Lambert-93), dbh_cm, optional uuid and score.

    Trees outside the radius or under the threshold are dropped unless
    ``garder_hors_inventaire`` (they are then numbered ``X1, X2...``).
    """
    lignes = []
    for t in troncs:
        dx, dy = offsets_geographiques(t["x"] - params.centre[0], t["y"] - params.centre[1], params.convergence_deg)
        lignes.append({**t, "_dx": dx, "_dy": dy, "uuid": t.get("uuid") or str(uuidlib.uuid4())})

    num = numeroter([ArbreANumeroter(l["uuid"], l["_dx"], l["_dy"], l["dbh_cm"]) for l in lignes], params.numerotation)
    hauteurs = affecter_hauteurs(lignes, houppiers)

    sortie = []
    hors = 0
    for l, h in zip(lignes, hauteurs):
        u = l["uuid"]
        if u in num.numeros:
            numero = num.numeros[u]
        elif garder_hors_inventaire:
            hors += 1
            numero = f"X{hors}"
        else:
            continue
        sortie.append(
            {
                "uuid": u,
                "plot_id": params.plot_id,
                "x": l["x"],
                "y": l["y"],
                "num_provisoire": numero,
                "azimut": round(num.azimuts[u], 4),
                "distance_m": round(num.distances[u], 3),
                "dbh_cm": round(float(l["dbh_cm"]), 1),
                "h_m": h.h_m,
                "h_source": h.h_source,
                "houppier_id": h.houppier_id,
                "etat_detection": etat,
                "methode": methode,
                "score": l.get("score"),
            }
        )
    sortie.sort(key=lambda d: (not d["num_provisoire"].isdigit(), int(d["num_provisoire"]) if d["num_provisoire"].isdigit() else 0))
    return sortie

