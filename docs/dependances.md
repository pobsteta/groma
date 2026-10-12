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
| kotlinx-serialization, JUnit 5, sqlite-jdbc | tests Kotlin | Apache-2.0 / EPL-2.0 / Apache-2.0 | tests seulement |
| boto3 | serveur (option S3) | Apache-2.0 | |

## Optionnelles, à surveiller

| Composant | Licence | Statut |
|---|---|---|
| **VGGT** (reconstruction IA) | code et poids VGGT-1B d'origine : **non commerciale** (Meta) ; un point de contrôle « VGGT-1B-Commercial » existe sous d'autres conditions | Adaptateur fourni, chargé seulement s'il est installé. **Signalé** : à vérifier avant tout usage commercial, poids non distribués ici. |
| SAM 2 (segmentation des troncs) | Apache-2.0 | adaptateur optionnel |
| PyTorch | BSD-3 | requis par VGGT et SAM 2 |
| MinIO (stockage S3 auto-hébergé) | **AGPL-3.0** | **Signalé** : acceptable comme service séparé non modifié. Alternatives : SeaweedFS (Apache-2.0), Garage (AGPL-3.0). Par défaut : disque local. |
| RTKLIB (post-traitement PPK) | BSD-2 | outil externe |

## Écartées

| Composant | Licence | Raison |
|---|---|---|
| MASt3R, DUSt3R | CC BY-NC-SA 4.0 | non commerciale |
| Ultralytics YOLO | AGPL-3.0 | copyleft réseau ; un détecteur IA ne ferait que proposer des zones candidates de cibles, OpenCV suffit |
