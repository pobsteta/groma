# PLAN — Groma (groma_nemeton)

Source unique de l'état d'avancement. Mis à jour à chaque étape.

## Décisions

| Date | Décision | Par |
|---|---|---|
| 2026-10-12 | **Essences** : colonne `essence_arbre` (liste rattachée à la BD Forêt V2), `espece` déduite par `map_bdforet_essence()` | Pascal |
| 2026-10-12 | **Diamètres > 300 cm** : nemeton évolue (`dbh_max_cm`, `dbh_alerte_cm` = 200), voir brief nemeton § 5 | Pascal |
| 2026-10-12 | **Écriture téléphone** : couche `detections` séparée, écrite par Groma seul, et déclencheurs SQLite vers `arbres` | Pascal |
| 2026-10-12 | `placettes`, `arbres` et `detections` sans index spatial (déclencheurs R*Tree de GDAL incompatibles avec le SQLite d'Android, vérifié par test) | Claude, à valider |
| 2026-10-12 | Colonne `convergence_deg` ajoutée à `placettes` (nord géographique contre nord Lambert-93, jusqu'à ±3°) ; `date` → `date_mesure` | Claude, à valider |
| 2026-10-12 | Rasters par placette en GeoTIFF Float32 en bandes, DEFLATE sans prédicteur, à côté du GeoPackage | Claude, à valider |
| 2026-10-12 | Les `a_verifier` sont numérotés `V1…` et les `faux_positif` `F1…`, pour ne pas décaler les numéros des arbres retrouvés | Claude, à valider |
| 2026-10-12 | File de tâches SQLite (pas de courtier) ; stockage local, S3 en option (MinIO signalé AGPL) | Claude, à valider |
| 2026-10-12 | Version unique pour tout le dépôt (tags `vX.Y.Z`), montée par release-please à partir des Conventional Commits ; release = wheels, image GHCR, APK | Claude, à valider |
| 2026-10-12 | Pont `groma-preparer` côté Groma (GeoPackage, sans toucher à nemeton) ; rayon et déclinaison obligatoires, rayon à reprendre du plan | Pascal |
| 2026-10-12 | Licence du dépôt : GPL-3.0-or-later (alignée sur nemeton), fichier `LICENSE` | Pascal |

## Avancement (ordre de travail du brief)

| # | Étape | État |
|---|---|---|
| 1 | Lecture de nemeton / nemetonshiny, trois décisions, arborescence, dépendances et licences | fait (`docs/dependances.md`) |
| 2 | `fixtures/` et `core/` : numérotation, géoréférencement, cercles, hauteurs par houppiers, placette synthétique | fait, 58 tests |
| 3 | Schéma `inventaire_video.gpkg`, exemple, briefs nemeton et nemetonshiny | fait ; exemple validé avec les vraies fonctions de nemeton (14 attentes R) |
| 4 | Pipeline serveur de bout en bout | fait sur données synthétiques (20 tests). COLMAP, VGGT et SAM 2 écrits mais **non exécutés** (pas de GPU ni de binaire ici) |
| 4b | `benchmark/` | script prêt, **en attente d'une vraie placette** |
| 5 | Application Android avec contrôle qualité | logique testée en Kotlin (29 tests) ; application **compilée en CI** (APK de debug), non testée sur appareil |
| 6 | Calcul embarqué provisoire | écrit (`CalculEmbarque.kt`), à tester sur appareil |
| 7 | Envoi, file d'attente, résultats pour nemetonshiny | fait côté serveur (envoi reprenable testé) ; `EnvoiWorker.kt` écrit |
| 7b | CI (Python 3.11/3.12, Kotlin, R, Docker, APK), versions, releases, Dependabot | fait |
| 7c | `groma-preparer` : projet Groma à partir du plan d'échantillonnage de nemetonshiny (`samples.gpkg` ou export QField), rayon en paramètre | fait (9 tests), en attendant `creer_projet_inventaire_video()` ; sans rasters |
| 8 | Essence proposée par photo d'écorce ou de feuilles | plus tard |

## Résultats sur données synthétiques (pas encore sur le terrain)

Placette de 25 arbres, dont un gros bois de 210 cm vu en arcs brisés ;
paquet rendu par lancer de rayons (288 images de profondeur 160×120, bruit
de 3 mm + 0,2 % de la distance) ; reconstruction par fusion des
profondeurs :

* 100 % des arbres retenus retrouvés, aucun faux positif ;
* RMSE du diamètre : 1,2 cm ; écart sur les 3 arbres cibles ≤ 0,3 cm ;
* erreur de position ≤ 4 cm ; cap retrouvé à 0,05° près ;
* environ 15 s de traitement (objectif < 15 min) ;
* une erreur de 10° sur les azimuts est détectée par le recalage sur les
  houppiers.

Ces chiffres valident la chaîne géométrique, **pas** la qualité d'une vraie
reconstruction en sous-bois.

## Points ouverts

1. **Android 11+ et QField** : QField travaille sur sa copie privée
   importée, que Groma ne peut pas ouvrir. Voie proposée : un plugin de
   projet QField lit les détections que Groma expose sur 127.0.0.1 et les
   écrit lui-même (voir `docs/terrain.md`). **À prototyper sur appareil**
   (API d'ajout d'entités et accès réseau d'un plugin non vérifiés).
2. **NTRIP en forêt** : client NTRIP intégré à QField ≥ 4.2 ; sans 4G, il
   faut un repli PPK (UBX brut + RTKLIB). Les identifiants Centipède sont à
   vérifier.
3. Tester `android/app` sur un appareil ARCore Depth : installée sur un
   Galaxy S23 Ultra le 2026-10-12, l'écran de capture s'ouvre ; capture
   complète d'une placette pas encore faite.
4. Image Docker GPU : COLMAP avec CUDA, VGGT (licence à vérifier), SAM 2.
5. Première placette réelle : tourner, mesurer à la main, `benchmark/`.
6. ~~Déposer les briefs~~ : déposés le 2026-10-12 dans
   `/home/pascal/dev/briefs/vers-nemeton/` et `vers-nemetonshiny/`, en
   attente de traitement.

## Prochaine étape

Prototyper sur appareil la capture (`android/app`) et le transport vers
QField (point ouvert 1), puis tourner une placette test mesurée à la main
pour `benchmark/`.
