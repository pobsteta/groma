"""Synthetic capture package rendered from a synthetic plot (tests, demo).

Depth maps are ray-cast against the vertical trunks and the sloping ground,
from a camera walked on three circles around the centre, in an ARCore-like
local frame with an arbitrary heading and origin. Target observations (C0 on
the RTK pole, C1-C3 on the bark) are projected into the frames, with
occlusion by the trunks.
"""

from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path

import numpy as np

from groma.azimut import convergence_lambert93_deg
from groma.synthetique import PlacetteSynthetique, houppiers_synthetiques, mnh_synthetique, mnt_synthetique, placette_synthetique

from .paquet import (
    MONDE_VERS_LOCAL,
    Centre,
    CibleMesuree,
    DetectionCible,
    Image,
    Intrinseques,
    Lidar,
    Manifeste,
    ParametresPlacette,
    Suivi,
    ecrire_paquet,
)

LOCAL_VERS_MONDE = np.linalg.inv(MONDE_VERS_LOCAL)


@dataclass(frozen=True)
class Scenario:
    placette: PlacetteSynthetique
    rotation_deg: float  # local azimuth -> grid azimuth (clockwise)
    c0_local: tuple[float, float]
    z_decalage: float  # local z = altitude - z_decalage
    declinaison_deg: float
    convergence_deg: float

    def grille_vers_local(self, x, y):
        dx, dy = np.asarray(x) - self.placette.centre[0], np.asarray(y) - self.placette.centre[1]
        t = math.radians(-self.rotation_deg)
        c, s = math.cos(t), math.sin(t)
        return self.c0_local[0] + dx * c + dy * s, self.c0_local[1] - dx * s + dy * c

    def local_vers_grille(self, x, y):
        dx, dy = np.asarray(x) - self.c0_local[0], np.asarray(y) - self.c0_local[1]
        t = math.radians(self.rotation_deg)
        c, s = math.cos(t), math.sin(t)
        return self.placette.centre[0] + dx * c + dy * s, self.placette.centre[1] - dx * s + dy * c

    def z_sol_local(self, x, y):
        gx, gy = self.local_vers_grille(x, y)
        return self.placette.z_sol(gx, gy) - self.z_decalage


def _plan_sol(sc: Scenario) -> tuple[float, float, float]:
    """Ground plane in local coordinates: z = a + b x + c y."""
    x0, y0 = sc.c0_local
    z00 = float(sc.z_sol_local(x0, y0))
    b = float(sc.z_sol_local(x0 + 1, y0)) - z00
    c = float(sc.z_sol_local(x0, y0 + 1)) - z00
    return z00 - b * x0 - c * y0, b, c


def _rotation_camera(az_deg: float, tangage_deg: float) -> np.ndarray:
    a, p = math.radians(az_deg), math.radians(tangage_deg)
    f = np.array([math.sin(a) * math.cos(p), math.cos(a) * math.cos(p), math.sin(p)])
    r = np.cross(f, [0.0, 0.0, 1.0])
    r /= np.linalg.norm(r)
    u = np.cross(r, f)
    return np.column_stack([r, u, -f])


def _lancer(o: np.ndarray, d: np.ndarray, troncs_xy: np.ndarray, rayons: np.ndarray, sol: tuple) -> np.ndarray:
    """Distance t (in units of d) of the first hit for rays o + t d."""
    n = d.shape[0]
    t_best = np.full(n, np.inf)
    a0, b, c = sol
    denom = d[:, 2] - b * d[:, 0] - c * d[:, 1]
    with np.errstate(divide="ignore", invalid="ignore"):
        t_sol = (a0 + b * o[0] + c * o[1] - o[2]) / denom
    t_best = np.where((t_sol > 0) & np.isfinite(t_sol), t_sol, t_best)
    dx, dy = d[:, 0], d[:, 1]
    aa = dx * dx + dy * dy
    for (cx, cy), r in zip(troncs_xy, rayons):
        ox, oy = o[0] - cx, o[1] - cy
        bb = 2 * (ox * dx + oy * dy)
        cc = ox * ox + oy * oy - r * r
        disc = bb * bb - 4 * aa * cc
        ok = disc >= 0
        t = np.full(n, np.inf)
        t[ok] = (-bb[ok] - np.sqrt(disc[ok])) / (2 * aa[ok])
        t = np.where(t > 1e-6, t, np.inf)
        t_best = np.minimum(t_best, t)
    return t_best


def scenario_par_defaut(graine: int = 42) -> Scenario:
    p = placette_synthetique(graine=graine)
    return Scenario(p, rotation_deg=63.0, c0_local=(2.0, -3.0), z_decalage=419.0, declinaison_deg=2.4, convergence_deg=convergence_lambert93_deg(6.1))


def generer_paquet(
    chemin: str | Path,
    sc: Scenario | None = None,
    bruit_fixe_m: float = 0.003,
    bruit_relatif: float = 0.002,
    profondeur_max_m: float = 8.0,
    largeur: int = 160,
    hauteur: int = 120,
    focale: float = 140.0,
    rayons_parcours: tuple[float, ...] = (3.0, 9.0, 15.0),
    pas_deg: float = 15.0,
    avec_lidar: bool = True,
    dossier_lidar: Path | None = None,
    graine: int = 0,
) -> tuple[Path, Scenario]:
    """Render a capture package. Returns its path and the ground truth."""
    sc = sc or scenario_par_defaut()
    rng = np.random.default_rng(graine)
    p = sc.placette
    gx = np.array([p.position(a)[0] for a in p.arbres])
    gy = np.array([p.position(a)[1] for a in p.arbres])
    lx, ly = sc.grille_vers_local(gx, gy)
    troncs_xy = np.column_stack([lx, ly])
    rayons = np.array([a.dbh_cm / 200.0 for a in p.arbres])
    sol = _plan_sol(sc)

    k = Intrinseques(fx=focale, fy=focale, cx=largeur / 2, cy=hauteur / 2, largeur=largeur, hauteur=hauteur)
    uu, vv = np.meshgrid(np.arange(largeur) + 0.5, np.arange(hauteur) + 0.5)
    dir_cam = np.column_stack([((uu - k.cx) / k.fx).ravel(), (-(vv - k.cy) / k.fy).ravel(), -np.ones(uu.size)])

    # Targets in the local frame.
    c0x, c0y = sc.c0_local
    cibles_loc = {"C0": np.array([c0x, c0y, float(sc.z_sol_local(c0x, c0y)) + 1.30])}
    cibles_mes = []
    for id_cible, id_arbre in p.cibles.items():
        a = next(t for t in p.arbres if t.id == id_arbre)
        d_tronc = math.hypot(a.x, a.y)
        d = d_tronc - a.dbh_cm / 200.0
        bx, by = p.centre[0] + a.x * d / d_tronc, p.centre[1] + a.y * d / d_tronc
        x, y = sc.grille_vers_local(bx, by)
        cibles_loc[id_cible] = np.array([float(x), float(y), float(sc.z_sol_local(x, y)) + 1.30])
        az_grille = math.degrees(math.atan2(a.x, a.y)) % 360
        az_mag = (az_grille + sc.convergence_deg - sc.declinaison_deg) % 360
        cibles_mes.append(CibleMesuree(id_cible=id_cible, azimut_mesure=round(az_mag, 1), distance_mesuree_m=round(d, 2), dbh_mesure_cm=a.dbh_cm))

    images, detections, fichiers = [], [], {}
    t = 0.0
    for r_parcours in rayons_parcours:
        for az_pos in np.arange(0, 360, pas_deg):
            px = c0x + r_parcours * math.sin(math.radians(az_pos))
            py = c0y + r_parcours * math.cos(math.radians(az_pos))
            pz = float(sc.z_sol_local(px, py)) + 1.5
            o = np.array([px, py, pz])
            for regard in (az_pos, az_pos + 180.0, az_pos + 90.0, az_pos - 90.0):
                rot = _rotation_camera(regard, -15.0)
                pose_loc = np.eye(4)
                pose_loc[:3, :3], pose_loc[:3, 3] = rot, o
                d_loc = dir_cam @ rot.T
                prof = _lancer(o, d_loc, troncs_xy, rayons, sol)
                prof = prof + rng.normal(0, 1, prof.size) * (bruit_fixe_m + bruit_relatif * np.where(np.isfinite(prof), prof, 0))
                prof[(~np.isfinite(prof)) | (prof > profondeur_max_m) | (prof <= 0)] = 0
                mm = np.clip(np.round(prof * 1000), 0, 65535).astype("<u2").reshape(hauteur, largeur)
                i = len(images)
                nom = f"profondeurs/{i:06d}.u16"
                fichiers[nom] = mm.tobytes()
                pose_monde = LOCAL_VERS_MONDE @ pose_loc
                images.append(Image(id=i, t=round(t, 3), pose=pose_monde.ravel().tolist(), intrinseques=k, profondeur=nom, intrinseques_profondeur=k))
                t += 0.25
                # Target observations.
                inv = np.linalg.inv(pose_loc)
                for id_cible, X in cibles_loc.items():
                    pc = inv @ np.append(X, 1.0)
                    if pc[2] >= -0.3 or -pc[2] > 10.0:
                        continue
                    u = k.cx + k.fx * pc[0] / -pc[2]
                    v = k.cy - k.fy * pc[1] / -pc[2]
                    if not (0 <= u < largeur and 0 <= v < hauteur):
                        continue
                    ray = X - o
                    dist = np.linalg.norm(ray)
                    t_hit = _lancer(o, (ray / dist)[None, :], troncs_xy, rayons, sol)[0]
                    if t_hit < dist - 0.05:
                        continue  # hidden by a trunk
                    detections.append(DetectionCible(image=i, id_cible=id_cible, u=float(u + rng.normal(0, 0.5)), v=float(v + rng.normal(0, 0.5))))

    lidar = Lidar()
    if avec_lidar:
        from groma.mnt import decouper, ecrire_geotiff

        tmp = Path(dossier_lidar or Path(chemin).parent)
        tmp.mkdir(parents=True, exist_ok=True)
        ecrire_geotiff(decouper(mnt_synthetique(p), *p.centre, 30), tmp / "mnt.tif")
        ecrire_geotiff(decouper(mnh_synthetique(p), *p.centre, 30), tmp / "mnh.tif")
        fichiers["lidar/mnt.tif"] = (tmp / "mnt.tif").read_bytes()
        fichiers["lidar/mnh.tif"] = (tmp / "mnh.tif").read_bytes()
        fc = {
            "type": "FeatureCollection",
            "features": [
                {"type": "Feature", "properties": {"houppier_id": h.houppier_id, "h_max": h.h_max, "surface_m2": h.surface_m2},
                 "geometry": json.loads(_geojson(h.polygone))}
                for h in houppiers_synthetiques(p)
            ],
        }
        fichiers["lidar/houppiers.geojson"] = json.dumps(fc).encode()
        lidar = Lidar(mnt="lidar/mnt.tif", mnh="lidar/mnh.tif", houppiers="lidar/houppiers.geojson")

    m = Manifeste(
        plot_id="P001",
        date="2026-10-12T10:00:00Z",
        appareil={"modele": "synthetique"},
        placette=ParametresPlacette(declinaison_deg=sc.declinaison_deg, convergence_deg=sc.convergence_deg),
        centre=Centre(x=p.centre[0], y=p.centre[1], qualite_gnss="fix", precision_h_m=0.02),
        cibles_mesurees=cibles_mes,
        images=images,
        detections_cibles=detections,
        suivi=Suivi(perte_suivi_s=0.0, boucle_fermee=True),
        lidar=lidar,
    )
    return ecrire_paquet(chemin, m, fichiers), sc


def _geojson(poly) -> str:
    from shapely.geometry import mapping

    return json.dumps(mapping(poly))
