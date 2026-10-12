# Benchmark

Comparaison des diamètres, positions et hauteurs de chaque méthode
(embarquée, classique, IA, houppiers) aux mesures manuelles d'une placette
test (ruban, boussole de visée, décamètre). Objectifs du brief : ≥ 95 %
des arbres détectés, RMSE du diamètre ≤ 2 cm, erreur de position ≤ 0,5 m.

**État** : script prêt, en attente d'une vraie placette. Les chiffres
obtenus jusqu'ici l'ont été sur données synthétiques uniquement (voir
`server/tests/test_chaine.py`) et ne préjugent pas du terrain.

```bash
python benchmark/comparer.py --manuel P001_manuel.csv --placette P001.json \
  --methode classique=P001_definitif.gpkg --methode ia=P001_ia.gpkg \
  --methode embarque=inventaire_video.gpkg --plot-id P001 --sortie P001_benchmark.json
```

Format de `P001_manuel.csv` : `tree_id,azimut,distance_m,dbh_cm,h_m`
(azimut magnétique dans l'unité de la placette, distance horizontale du
centre à l'écorce, `h_m` facultatif). `P001.json` :
`{"centre": [x, y], "unite_azimut": "gr", "declinaison_deg": 2.4, "convergence_deg": 2.1}`.
