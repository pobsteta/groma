# Groma

[![ci](https://github.com/pobsteta/groma/actions/workflows/ci.yml/badge.svg)](https://github.com/pobsteta/groma/actions/workflows/ci.yml)
[![licence](https://img.shields.io/badge/licence-GPL--3.0--or--later-blue)](LICENSE)
[![release](https://img.shields.io/github/v/release/pobsteta/groma?include_prereleases&sort=semver)](https://github.com/pobsteta/groma/releases)

Inventaire forestier sur placette circulaire **par vidéo au smartphone**.
Groma retrouve chaque arbre de la placette, mesure son diamètre à 1,30 m,
calcule sa position, le numérote selon une règle fixe et livre le résultat
dans un projet QField, où l'essence est saisie sur le terrain.

Le nom vient de la *groma* des arpenteurs romains, plantée en un point
central pour viser les directions autour, comme le récepteur RTK au centre
de la placette. Groma fait partie de la plateforme **Néméton** : le
GeoPackage de terrain est préparé et relu par
[`nemeton`](https://github.com/pobsteta/nemeton) et `nemetonshiny`, les
évolutions demandées à ces paquets sont livrées en briefs (`briefs/`).

> **État : prototype (0.x).** La chaîne est validée sur données
> synthétiques ; l'application Android compile mais n'a pas encore été
> testée sur appareil, et aucune placette réelle n'a été mesurée. Voir
> [`PLAN.md`](PLAN.md).

## Principe

```
nemetonshiny ── prépare le projet QField (inventaire_video.gpkg + rasters)
      │
Téléphone ── QField : centre RTK, cibles, essences
      └─ Groma : vidéo ARCore + profondeur + ArUco
            ├─ contrôle qualité en direct
            ├─ A. calcul embarqué ──► arbres provisoires (saisie des essences)
            └─ paquet <plot>.groma.zip ──► envoi reprenable
                                                │
Serveur Groma (API + worker GPU) ◄──────────────┘
      reconstruction 3D → tranche 1,30 m → cercles → géoréférencement
      → numérotation → hauteurs par houppiers
      └─ B. arbres définitifs + rapport de qualité ──► nemetonshiny
```

| | Où | Quand | Résultat |
|---|---|---|---|
| **A. Embarqué** | téléphone, hors ligne | fin de prise de vue (< 2 min) | contrôle de la prise de vue, arbres **provisoires** |
| **B. Serveur** | PC avec GPU ou machine louée | dès qu'un réseau permet l'envoi | arbres **définitifs** + rapport de qualité |

Le rapprochement provisoire → définitif est fait par nemeton
(`rapprocher_arbres()`), sans perte d'attribut saisi sur le terrain.
Détails : [`docs/architecture.md`](docs/architecture.md).

## Contenu du dépôt

| Dossier | Contenu | Langage | État |
|---|---|---|---|
| [`core/`](core/) | paquet `groma` : numérotation, azimuts, cercles, troncs, géoréférencement, houppiers, MNT, rapprochement, schéma et écriture du GeoPackage | Python | testé |
| [`server/`](server/) | `groma-server` : API FastAPI (envoi reprenable), file de tâches SQLite, pipeline 3D, rapport, image Docker | Python | testé sur données synthétiques |
| [`android/groma-core`](android/groma-core/) | portage de la logique commune (numérotation, cercles, appariement, GeoPackage, contrôle qualité…) | Kotlin/JVM | testé |
| [`android/app`](android/app/) | application Groma : capture ARCore, ArUco, calcul embarqué, envoi | Kotlin/Android | compile (CI), **non testée sur appareil** |
| [`fixtures/`](fixtures/) | cas de test communs aux trois langages (numérotation, rapprochement) | JSON | |
| [`briefs/`](briefs/) | briefs pour `nemeton` et `nemetonshiny`, code R de référence et tests | R, Markdown | |
| [`exemples/`](exemples/) | GeoPackage d'exemple (2 placettes, rasters) et résultat serveur | | passe la chaîne nemeton |
| [`benchmark/`](benchmark/) | comparaison des méthodes aux mesures manuelles | Python | en attente d'une placette réelle |
| [`docs/`](docs/) | [architecture](docs/architecture.md), [terrain](docs/terrain.md) (QField, NTRIP), [serveur](docs/serveur.md), [dépendances et licences](docs/dependances.md) | | |

## Installation et tests

Prérequis : Python ≥ 3.11, JDK ≥ 17 et Gradle 8.14 pour le Kotlin, R avec
`sf` et `testthat` pour les tests R, SDK Android pour l'application.

```bash
# Python
python -m venv .venv && . .venv/bin/activate
pip install -e "core[dev]" -e "server[dev]"
pytest core server benchmark

# Kotlin (logique commune, sans SDK Android)
cd android && gradle :groma-core:test

# Application Android (SDK Android requis : ANDROID_HOME ou local.properties)
cd android && gradle :app:assembleDebug

# R : numérotation sur les cas communs
GROMA_FIXTURES=$PWD/fixtures/numerotation Rscript -e 'source("briefs/vers-nemeton/R/numerotation.R"); testthat::test_file("briefs/vers-nemeton/tests/test-numerotation.R")'

# R : intégration avec nemeton sur le GeoPackage d'exemple
NEMETON_SRC=/chemin/vers/nemeton GROMA_RACINE=$PWD Rscript -e 'testthat::test_file("briefs/vers-nemeton/tests/test-integration-gpkg.R")'

# Regénérer l'exemple et les cas communs
python exemples/creer_exemple.py
python fixtures/numerotation/generer.py && python fixtures/rapprochement/generer.py
```

## Serveur

```bash
# Image publiée à chaque version
docker pull ghcr.io/pobsteta/groma-server:latest

# Ou construite localement, API + worker
GROMA_JETON=secret docker compose -f server/docker-compose.yml up -d
```

Configuration, GPU (COLMAP CUDA, VGGT, SAM 2) et stockage S3 :
[`docs/serveur.md`](docs/serveur.md).

## Intégration continue et versions

**Tests** (`.github/workflows/ci.yml`), à chaque PR et à chaque push sur
`main` :

| Job | Vérifie |
|---|---|
| `python` | `pytest core server benchmark` sous Python 3.11 et 3.12 |
| `kotlin` | `gradle :groma-core:test` |
| `android-app` | `gradle :app:assembleDebug`, APK de debug en artefact |
| `r` | numérotation R sur les cas communs et intégration avec `nemeton` |
| `docker` | construction de l'image serveur |

**Versions** : une seule version pour tout le dépôt (SemVer, tags `vX.Y.Z`),
calculée par [release-please](https://github.com/googleapis/release-please)
à partir des messages de commit
([Conventional Commits](https://www.conventionalcommits.org/fr/)) :

* `fix: …` → version corrective, `feat: …` → version mineure, `feat!: …`
  ou `BREAKING CHANGE:` → version majeure (mineure tant qu'on est en 0.x) ;
* `docs`, `refactor`, `test`, `build`, `ci`, `chore` ne déclenchent pas de
  version à eux seuls.

Les PR sont fusionnées en *squash* : **le titre de la PR devient le commit**
et doit donc suivre la convention (vérifié par `pr-title.yml`).

**Publication** (`.github/workflows/release.yml`) :

1. À chaque push sur `main`, release-please ouvre ou met à jour la PR
   « chore: publier la version X.Y.Z ». Elle monte la version dans
   `core/pyproject.toml`, `server/pyproject.toml`, les deux `__init__.py`
   et `android/app/build.gradle.kts` (`versionCode` en est déduit), et
   complète `CHANGELOG.md`.
2. Fusionner cette PR crée le tag `vX.Y.Z` et la release GitHub.
3. Les tests sont rejoués sur le tag, puis sont attachés à la release :
   wheels et sdist de `groma` et `groma-server`, image
   `ghcr.io/pobsteta/groma-server:X.Y.Z` (et `X.Y`, `latest`), APK Android
   (signée si le keystore est configuré, sinon APK de debug).

Secrets facultatifs du dépôt :

| Secret | Usage |
|---|---|
| `RELEASE_PLEASE_TOKEN` | PAT (`contents` + `pull-requests` en écriture) pour que la CI tourne aussi sur la PR de version |
| `GROMA_KEYSTORE_BASE64`, `GROMA_KEYSTORE_PASSWORD`, `GROMA_KEY_ALIAS`, `GROMA_KEY_PASSWORD` | signature de l'APK de release |

Dependabot propose chaque mois les mises à jour des actions, des
dépendances Python et Gradle et de l'image de base Docker.

## Contribuer

Règles du projet (une seule source pour le schéma, numérotation identique
en Python, Kotlin et R, aucune modification directe de nemeton…) :
[`CLAUDE.md`](CLAUDE.md). Code et commentaires en anglais, documentation et
noms de champs en français.

## Licence

[GPL-3.0-or-later](LICENSE), comme nemeton.
Les licences des dépendances sont recensées dans
[`docs/dependances.md`](docs/dependances.md).
