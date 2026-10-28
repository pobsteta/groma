# Dépendances et licences

Licence du dépôt : **GPL-3.0-or-later**, alignée sur nemeton et
nemetonshiny (à confirmer par Pascal). Les échanges avec nemeton se font
par GeoPackage et GeoTIFF : ce dépôt n'importe pas son code R.

## Utilisées

| Composant | Où | Licence | Remarque |
|---|---|---|---|
| NumPy, SciPy | core, serveur | BSD-3 | |
| Shapely, GEOS | core | BSD-3 / LGPL-2.1 | GEOS en bibliothèque dynamique |
| pyproj / PROJ | core (tests) | MIT | |
| rasterio, pyogrio, GDAL | core, serveur | BSD-3 / MIT | |
| GeoPandas | core, serveur | BSD-3 | |
| FastAPI, Starlette, Pydantic, Uvicorn | serveur | MIT / BSD-3 | |
| OpenCV (contrib, module ArUco) | serveur, Android | Apache-2.0 | |
| COLMAP | serveur | BSD-3 | binaire appelé en sous-processus |
| ARCore SDK (client) | Android | Apache-2.0 | requiert « Services Google Play pour la RA » (propriétaire, installé sur l'appareil) |
| AndroidX, WorkManager | Android | Apache-2.0 | |
| Jetpack Compose, Material 3, icônes Material (core) | Android (écran principal, style Marculus) | Apache-2.0 | |
| osmdroid | Android (écran Carte) | Apache-2.0 | |
| Code GNSS / NTRIP repris de Marculus | `groma-core` (NMEA, NTRIP), Android (transports, service RTK, position sur la carte) | même auteur (pobsteta) | Marculus n'a pas de fichier `LICENSE` ; repris par son auteur, adapté aux conventions de Groma, aucune dépendance ajoutée |
| Tuiles IGN Géoplateforme (Plan IGN, ortho, cadastre) | Android (écran Carte), service web | Licence Ouverte Etalab 2.0 | sans clé ; attribution affichée sur la carte ; User-Agent `Groma/<version>` ; téléchargement hors ligne limité à l'emprise du projet + 200 m, zooms 12 à 18, 30 000 tuiles au plus, une requête à la fois |
| Tuiles OpenStreetMap | Android (écran Carte), service web | ODbL (données) | attribution « © contributeurs OpenStreetMap » affichée ; usage modéré selon la politique des tuiles OSM ; **pas de téléchargement hors ligne** (interdit par cette politique) |
| Modèle géomagnétique WMM2025 (coefficients `WMM2025.COF`) | core (`groma.declinaison`), `groma-core` (`Declinaison`) | **domaine public** (NOAA/NCEI, gouvernement des États-Unis) | fichier officiel de `WMM2025COF.zip` (https://www.ncei.noaa.gov/products/world-magnetic-model), époque 2025.0, valable jusqu'à 2030.0 ; une seule copie dans `core/src/groma/donnees/`, copiée en ressource Kotlin par Gradle ; algorithme réécrit, aucune bibliothèque ajoutée |
| kotlinx-serialization, JUnit 5, sqlite-jdbc | tests Kotlin | Apache-2.0 / EPL-2.0 / Apache-2.0 | tests seulement |
| boto3 | serveur (option S3) | Apache-2.0 | |
| Police UnifrakturCook Bold | icône Android (rendue en PNG) | OFL-1.1 | téléchargée par `android/app/icone/generer_icone.py`, non versionnée ; seul le rendu est distribué |

## Optionnelles, à surveiller

| Composant | Licence | Statut |
|---|---|---|
| **VGGT** (reconstruction IA) | code : « VGGT License » de Meta (2025-07-29), usage commercial permis, **non OSI**, politique d'usage excluant notamment le militaire et les infrastructures critiques ; poids VGGT-1B d'origine : **non commerciaux** ; « VGGT-1B-Commercial » : usage commercial après formulaire de demande | Adaptateur fourni, chargé seulement s'il est installé. **Signalé** : installé dans l'image GPU, jamais copié dans le dépôt ; poids jamais dans l'image ; désactivé par défaut tant que les poids commerciaux ne sont pas obtenus (brief `briefs/groma/2026-10-17-worker-gpu-scaleway.md`). |
| SAM 2 (segmentation des troncs) | Apache-2.0 | adaptateur optionnel |
| SAM 3 (segmentation des troncs par invite texte, Meta) | « SAM License » (2025-11-19) de Meta : usage commercial permis, redistribution avec copie de la licence et mention de Meta, contrôle des exportations, **non OSI** ; poids `facebook/sam3` **à accès demandé** sur Hugging Face | **Signalé** : banc d'essai seulement (`benchmark/segmentation_troncs.py`), pas dans l'image du worker. À relire avant toute adoption en production. |
| WeVisOne-VL 2B / 4B (segmentation par invite texte, Tencent) | code Apache-2.0 (fichier `LICENSE` du dépôt, composants tiers sous leur propre licence) ; poids `tencent/WeVisOne-VL-*` étiquetés apache-2.0 sur Hugging Face | Banc d'essai seulement, dans un environnement à part (épingle torch 2.11 et transformers 5.12.1). Publié le 2026-10-22, aucun résultat reproduit par un tiers. |
| MapAnything (reconstruction IA, Meta) | code Apache-2.0 ; poids `facebook/map-anything-apache` Apache-2.0 ; poids `facebook/map-anything` **CC-BY-NC 4.0** | Prévu (brief worker GPU) : seul le point de contrôle Apache est retenu ; poids téléchargés à l'exécution, jamais dans l'image. |
| Prompt Depth Anything (profondeur métrique guidée) | code Apache-2.0 ; poids `prompt-depth-anything-vitl` apache-2.0 (étiquette Hugging Face) | Prévu : profondeur ARCore en indication. Licence des poids à relire à l'épinglage. |
| Video Depth Anything / Depth Anything 3 | `Small`, `Metric-…-Small`, `DA3METRIC-LARGE` : Apache-2.0 ; `Base`, `Large`, `GIANT`, `NESTED` : **CC-BY-NC 4.0** | Repli prévu, points de contrôle Apache seulement. |
| PyTorch | BSD-3 | requis par VGGT et SAM 2 |
| MinIO (stockage S3 auto-hébergé) | **AGPL-3.0** | **Signalé** : acceptable comme service séparé non modifié. Alternatives : SeaweedFS (Apache-2.0), Garage (AGPL-3.0). Par défaut : disque local. |
| RTKLIB (post-traitement PPK) | BSD-2 | outil externe |
| **API Pl@ntNet** (essence par photo d'écorce ou de feuilles, étape 8) | service web **propriétaire** ; gratuit jusqu'à 500 identifications par jour (un compte par personne ou structure), **payant** au-delà (1 000 € HT pour 200 000 requêtes par an, puis 5 € les 1 000) | **Signalé** : pas encore intégré. Proposé **seulement après validation de l'utilisateur** (désactivé par défaut, clé d'API saisie par l'utilisateur). Sans réseau en forêt, les photos sont gardées avec l'arbre et identifiées **a posteriori**, à l'envoi ou côté serveur. Résultat (score, nom scientifique, identifiant GBIF) seulement **proposé** contre la liste BD Forêt V2, jamais écrit sans confirmation. Photos non stockées par Pl@ntNet ; e-mail et historique des requêtes transmis. [Conditions](https://my.plantnet.org/terms_of_use) |

## Écartées

| Composant | Licence | Raison |
|---|---|---|
| MASt3R, DUSt3R | CC BY-NC-SA 4.0 | non commerciale |
| Ultralytics YOLO | AGPL-3.0 | copyleft réseau ; un détecteur IA ne ferait que proposer des zones candidates de cibles, OpenCV suffit |
