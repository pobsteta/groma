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
| 2026-10-15 | Déclinaison calculée par WMM2025 (NOAA, embarqué en Python et en Kotlin) quand elle n'est pas fournie à `groma-preparer`, par placette et à la date du jour ; saisie manuelle prioritaire (avertissement au-delà de 1° d'écart). Complète la décision « rayon et déclinaison obligatoires » du 2026-10-12 | Pascal (2026-10-16) |

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
| 7c | `groma-preparer` : projet Groma à partir du plan d'échantillonnage de nemetonshiny (`samples.gpkg` ou export QField), rayon en paramètre | fait (9 tests), sans rasters. Le projet complet (GeoPackage, `.qgz`, plugin, symbologie) vient désormais de `nemeton::creer_projet_inventaire_video()`, appelé par le bouton « Générer le projet QField » de nemetonshiny (Terrain → Inventaire vidéo) ; `groma-preparer` reste un dépannage |
| 7d | Liste des projets façon contextes Marculus (plusieurs plans sur le téléphone, table `projet`, `projet_id` dans les paquets) | brief écrit (`briefs/groma/2026-10-13-liste-des-projets.md`) ; PR 1 faite : table `projet` (schéma 1.1.0), `groma-preparer` la remplit depuis le projet nemetonshiny, lecture Kotlin. PR 2 faite : `projet_id` dans les paquets, les envois, les exports de détections et le serveur (rangement et routes par projet, compatible sans `projet_id`). PR 3 faite : écran Projets (dossier des projets, copies de travail, cartes, recherche, « Terminés », mise à jour protégée, retrait), essayé sur le S23 Ultra avec Couchey et Reconfort. PR 4 faite : docs (`terrain.md` : déposer, autoriser le dossier, mettre à jour). PR 5 : tri par distance (dernier ouvert, puis distance au centre de `emprise` depuis la dernière position connue du téléphone, sinon par nom ; distance sur la carte du projet), sans écoute GNSS sur cet écran |
| 7e | Carte des placettes, parcours et position RTK façon Marculus (brief `briefs/groma/2026-10-14-carte-rtk.md`) | PR 1 faite : couche `parcelles` (schéma 1.2.0) copiée par `groma-preparer`, lecture Kotlin des polygones, projections Lambert 93 / WGS 84 / Web Mercator en Kotlin pur. PR 2 faite : écran Carte (osmdroid ; Plan IGN, ortho IGN, OSM, cadastre IGN ; parcelles, parcours, placettes par état, centres mesurés ; feuille de placette, couches, cadrage), non essayée sur appareil. PR 3 faite : GNSS et RTK (NMEA, NTRIP et géodésie testés dans `groma-core` ; Bluetooth SPP et TCP, pont NTRIP, service de premier plan, repli sur le GNSS du téléphone ; position, badge du fix, « État GNSS », suivi sur la carte ; Paramètres → Récepteur RTK), non essayée avec le ZED-F9P. PR 3 bis : couche `ugf` (parcellaire des UG nemetonshiny, ténements fusionnés par UG) copiée par `groma-preparer` et tracée sur la carte. PR 4a : « Y aller » (feuille de placette, ou prochaine placette à faire du parcours) : bandeau avec distance, azimut magnétique dans l'unité de la placette et géographique, flèche relative au cap, ligne vers la placette, « Arrivé » à moins de 5 m, placette suivante ; calcul testé dans `groma-core`, non essayé sur appareil. PR 4b : « Mesurer le centre » à l'arrivée : moyenne des fix RTK fixes (durée réglable, 10 s), confirmation en flottant, refus sur le GNSS du téléphone, ligne ajoutée à `centres_mesures` (jamais réécrite, la plus récente compte, déclencheurs R*Tree mis de côté le temps de l'insertion), centre repris par la capture ; non essayé avec le ZED-F9P. PR 5a : carte hors ligne (menu « Hors ligne… » : Plan IGN ou ortho IGN, cadastre en option, emprise + 200 m, zooms 12 à 18, estimation, WorkManager avec reprise, tuiles dans le dossier du projet lues avant le réseau, gardées à la mise à jour, OSM exclu) ; calcul des tuiles testé, non essayé sur appareil. Tri des projets par distance fait (voir 7d). PR 6 : docs (`terrain.md` : carte du projet, couches, badge et État GNSS, « Y aller », « Mesurer le centre », ordre de la liste des projets). Reste : transport du centre vers la copie QField (même point ouvert que les détections)  |
| 7f | Déclinaison magnétique WMM2025 | `groma.declinaison` et `groma.core.Declinaison` (100 valeurs officielles NOAA + 2 points en France, `fixtures/declinaison/`) ; `--declinaison` facultatif dans `groma-preparer` ; Groma la calcule à la volée quand le GeoPackage n'en a pas (capture, « Y aller ») |
| 7g | Envoi du projet de nemetonshiny au téléphone par le serveur Groma (brief `briefs/groma/2026-10-17-envoi-projet-telephone.md`) | PR 1 faite : routes `/projets/…/qfield` du serveur (dépôt en flux, publication vérifiée et atomique, `projet_id` contrôlé dans le GeoPackage du `.qgz`, `Range`, 3 versions ; 13 tests). Reste : 2 manifeste et extraction sûre du `.qgz` (groma-core), 3 « Projets du serveur… » et téléchargement reprenable, 4 « Ouvrir dans QField », 5 docs. Briefs déposés le 2026-10-17 : nemeton (`groma_envoyer_projet()`), nemetonshiny (bouton « Envoyer au téléphone ») |
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
   importée, que Groma ne peut pas ouvrir. Prototype écrit : plugin de
   projet `qfield/inventaire_video.qml` (détections et centres mesurés lus
   sur `127.0.0.1:8765/inventaire/<projet_id>`, ajoutés par QField),
   projet de test `qfield/creer_projet_test.py` ; API vérifiées dans le
   source de QField. **Essayé le 2026-10-16 sur le S23 Ultra (QField 4.3.5)**
   avec le projet de Couchey : 3 détections de test ajoutées par le plugin.
   Reste à essayer : second appui (pas de doublon), lignes `arbres` créées
   par les déclencheurs, centres mesurés, détections d'une vraie capture.
   Service « partage avec QField » (premier plan, arrêt après 30 min sans
   demande) pour que Android ne coupe pas Groma pendant la saisie dans
   QField ; compilé, pas encore essayé sur appareil.
2. **NTRIP en forêt** : client NTRIP intégré à QField ≥ 4.2 ; sans 4G, il
   faut un repli PPK (UBX brut + RTKLIB). Les identifiants Centipède sont à
   vérifier.
3. Tester `android/app` sur un appareil ARCore Depth : installée sur un
   Galaxy S23 Ultra le 2026-10-12, l'écran de capture s'ouvre ; capture
   complète d'une placette pas encore faite.
4. Image Docker GPU : COLMAP avec CUDA, VGGT (licence à vérifier), SAM 2.
5. Première placette réelle : tourner, mesurer à la main, `benchmark/`.
6. **Chaîne nemeton / nemetonshiny livrée** (constaté le 2026-10-17) :
   `creer_projet_inventaire_video()` au schéma 1.2.0 avec plugin, symbologie
   de Marculus et fonds IGN (nemeton 2026.10.9, dépôt) ; sous-onglet
   « Inventaire vidéo » de nemetonshiny (2026.10.10). Nemeton installé ici :
   2026.10.8, sans la symbologie ; à mettre à jour. Reste à essayer le bouton
   sur Marsannay et à comparer avec le projet de test.
   Annexe au brief nemeton (tables `projet`, `parcelles`, `ugf`, plusieurs centres par placette) : `briefs/vers-nemeton/2026-10-15-inventaire-video-carte.md`, déposée le 2026-10-15.
   Seconde annexe (plugin QField à livrer à côté du projet, noms de couches,
   ajout d'entités autorisé, `declinaison_deg` vide accepté) :
   `briefs/vers-nemeton/2026-10-16-inventaire-video-plugin-qfield.md`,
   déposée le 2026-10-16.
7. ~~Déposer les briefs~~ : déposés le 2026-10-12 dans
   `/home/pascal/dev/briefs/vers-nemeton/` et `vers-nemetonshiny/`, en
   attente de traitement.

## Prochaine étape

Prototyper sur appareil la capture (`android/app`) et le transport vers
QField (point ouvert 1), puis tourner une placette test mesurée à la main
pour `benchmark/`.
