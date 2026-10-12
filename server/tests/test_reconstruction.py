import math

import numpy as np
import pytest

from groma_server.paquet import Image, Intrinseques
from groma_server.reconstruction.base import sous_echantillonner
from groma_server.reconstruction.colmap import GL_VERS_CV, commandes, ecrire_ply, lire_ply, pose_colmap, quaternion
from groma_server.reconstruction.vggt import morceaux, similitude_umeyama


def _rot(axe, angle):
    a = np.asarray(axe, float) / np.linalg.norm(axe)
    k = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) + math.sin(angle) * k + (1 - math.cos(angle)) * k @ k


def _de_quaternion(q):
    w, x, y, z = q
    return np.array([
        [1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
        [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
        [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)],
    ])


@pytest.mark.parametrize("angle", [0.1, 1.0, 2.5, 3.1])
def test_quaternion(angle):
    r = _rot([0.3, -1, 0.5], angle)
    assert np.allclose(_de_quaternion(quaternion(r)), r, atol=1e-9)


def test_pose_colmap_projette_comme_arcore():
    pose = np.eye(4)
    pose[:3, :3] = _rot([0, 1, 0], 0.7)
    pose[:3, 3] = [1.0, 1.5, -2.0]
    q, t = pose_colmap(pose)
    r = _de_quaternion(q)
    p_monde = np.array([2.0, 1.0, -6.0])
    p_cv = r @ p_monde + t
    p_gl = np.linalg.inv(pose) @ np.append(p_monde, 1)
    assert np.allclose(p_cv, (GL_VERS_CV @ p_gl)[:3])
    assert p_cv[2] > 0  # in front of the camera for OpenCV / COLMAP


def test_ply_aller_retour(tmp_path):
    pts = np.random.default_rng(0).normal(size=(50, 3))
    ecrire_ply(tmp_path / "a.ply", pts)
    assert np.allclose(lire_ply(tmp_path / "a.ply"), pts, atol=1e-6)


def test_commandes_colmap(tmp_path):
    c = commandes("colmap", tmp_path)
    assert [x[1] for x in c] == ["feature_extractor", "sequential_matcher", "point_triangulator",
                                 "image_undistorter", "patch_match_stereo", "stereo_fusion"]
    assert len(commandes("colmap", tmp_path, dense=False)) == 3


def test_umeyama():
    rng = np.random.default_rng(1)
    src = rng.normal(size=(20, 3))
    r = _rot([1, 2, 3], 0.8)
    dst = 2.5 * src @ r.T + [1, -2, 3]
    s, r2, t = similitude_umeyama(src, dst)
    assert s == pytest.approx(2.5) and np.allclose(r2, r) and np.allclose(t, [1, -2, 3])


def test_morceaux():
    m = morceaux(70, 32, 8)
    assert m[0] == range(0, 32) and m[-1].stop == 70
    assert all(a.stop - b.start == 8 for a, b in zip(m, m[1:]))
    assert morceaux(10, 32, 8) == [range(10)]


def test_sous_echantillonnage():
    pts = np.array([[0.001, 0.001, 0.001], [0.002, 0.002, 0.002], [1, 1, 1]])
    assert len(sous_echantillonner(pts, 0.01)) == 2
