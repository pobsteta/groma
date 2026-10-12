# groma-server

Calcul définitif d'une placette : API d'envoi reprenable, file de tâches,
pipeline 3D (reconstructions classique et IA derrière une même interface,
cibles ArUco, sol LiDAR, tranche 1,20-1,40 m, cercles, géoréférencement,
numérotation, hauteurs par houppiers, rapport de qualité).

```bash
pip install -e "../core[dev]" -e ".[dev]"
pytest
GROMA_DONNEES=/tmp/groma GROMA_JETON=secret uvicorn groma_server.api:creer_app --factory
GROMA_DONNEES=/tmp/groma groma-worker
```

Voir `docs/serveur.md` à la racine du dépôt.
