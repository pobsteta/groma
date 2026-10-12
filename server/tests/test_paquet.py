import numpy as np

from groma_server.paquet import Paquet, pose_locale


def test_lecture_paquet(paquet_synthetique):
    chemin, _ = paquet_synthetique
    p = Paquet.ouvrir(chemin)
    try:
        m = p.manifeste
        assert m.plot_id == "P001" and len(m.images) > 100
        d = p.profondeur(m.images[0])
        assert d.shape == (120, 160) and np.nanmax(d) <= 8.0
        pose = pose_locale(m.images[0])
        # Camera of the first frame at about 1.5 m above the ground, frame z up.
        assert abs(np.linalg.det(pose[:3, :3]) - 1) < 1e-9
        assert {c.id_cible for c in m.cibles_mesurees} == {"C1", "C2", "C3"}
        assert p.existe(m.lidar.mnt) and p.existe(m.lidar.houppiers)
    finally:
        p.fermer()
