from pathlib import Path

import pytest

RACINE = Path(__file__).resolve().parents[2]


@pytest.fixture(scope="session")
def dossier_fixtures() -> Path:
    return RACINE / "fixtures"
