import pytest

from groma_server.synthese import generer_paquet


@pytest.fixture(scope="session")
def paquet_synthetique(tmp_path_factory):
    d = tmp_path_factory.mktemp("paquet")
    chemin, sc = generer_paquet(d / "P001.groma.zip", dossier_lidar=d / "lidar")
    return chemin, sc
