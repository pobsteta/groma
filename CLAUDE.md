# CLAUDE.md — groma_nemeton

Inventaire forestier par vidéo (application Android Groma, paquet Python
`groma`, serveur). Fait partie de la plateforme Néméton.

## Règles

1. **Ne jamais modifier `nemeton` ni `nemetonshiny`** (lecture seule, aucune
   opération git dans leurs dossiers). Leurs évolutions sont livrées en
   **briefs** : copie de référence dans `briefs/vers-nemeton/` et
   `briefs/vers-nemetonshiny/`, copie déposée dans
   `/home/pascal/dev/briefs/vers-…/AAAA-MM-JJ-sujet.md`.
2. Ce dépôt n'importe pas le code R de nemeton : les échanges passent par
   GeoPackage et GeoTIFF.
3. Le schéma du GeoPackage n'a qu'une source :
   `core/src/groma/donnees/inventaire_video.schema.json` (+
   `declencheurs.sql`, `essences_bdforet_v2.json`).
4. La numérotation est implémentée en Python, Kotlin et R ; toute
   modification passe d'abord par un cas dans `fixtures/numerotation/`
   (résultats attendus écrits à la main), puis par les trois langages.
5. Le rapprochement ne perd jamais un attribut saisi sur le terrain (test
   dédié).
6. Signaler toute dépendance non commerciale ou copyleft avant de l'ajouter
   (`docs/dependances.md`).
7. Tenir `PLAN.md` à jour à chaque étape.

## Conventions

* Python 3.11+, typage, pytest, `logging`. Kotlin JVM 17 pour `groma-core`.
* Code et commentaires en anglais ; messages, documentation et noms de
  champs en français (convention NMT : snake_case sans accent, 30 caractères
  au plus).
* Unités : mètres, diamètres en cm, azimuts en degrés ou grades selon la
  placette (calculs internes en degrés, nord géographique).
* Conventional Commits (en français après le type : `feat(core): …`) ; ils
  pilotent les versions (release-please). PR fusionnées en squash, titre
  conforme. Ne jamais modifier les numéros de version à la main.

## Commandes

```bash
pytest core server benchmark
cd android && gradle :groma-core:test
cd android && gradle :app:assembleDebug   # SDK Android requis
GROMA_FIXTURES=$PWD/fixtures/numerotation Rscript -e 'source("briefs/vers-nemeton/R/numerotation.R"); testthat::test_file("briefs/vers-nemeton/tests/test-numerotation.R")'
```
