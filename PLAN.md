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
| 7g | Envoi du projet de nemetonshiny au téléphone par le serveur Groma (brief `briefs/groma/2026-10-17-envoi-projet-telephone.md`) | PR 1 faite : routes `/projets/…/qfield` du serveur (dépôt en flux, publication vérifiée et atomique, `projet_id` contrôlé dans le GeoPackage du `.qgz`, `Range`, 3 versions ; 13 tests). PR 2 faite : manifeste, état sur le téléphone (absent, à jour, plus récent), extraction du `.qgz` limitée à `inventaire_video.gpkg` et `rasters/*.tif` avec noms reconstruits (pas de zip-slip), SHA-256 (groma-core, 6 tests). PR 3 faite : ⋮ → « Projets du serveur… » (liste, état, confirmation avant de remplacer un dépôt par câble ou une version plus récente), téléchargement WorkManager (Wi-Fi, 4G si autorisée, reprise par `Range`, SHA-256, notification de progression), installation dans `Download/groma/<projet_id>/` par dossier temporaire puis échange (`.qgz` et `.qml` dans `qfield/`), copie de travail laissée à « Mettre à jour » (sans `qfield/`) ; essayée sur le S23 Ultra avec un serveur local (installation, mise à jour, reprise après arrêt du serveur, reprise par `Range` en 206). PR 4 faite : ⋮ → « Ouvrir dans QField » (si `qfield/` contient un `.qgz`) envoie à QField un zip (`.qgz` et `.qml` à la racine, `inventaire_video.gpkg` et `rasters/` à côté, car QGIS résout `./…` depuis le dossier du `.qgz`) par FileProvider, `ACTION_VIEW` puis `ACTION_SEND` ; repli : import du dossier à la main ; projet déjà envoyé (empreinte du `.qgz`) : même version, « Ouvrir QField » (« Renvoyer » seulement si la copie de QField a été supprimée) ; nouvelle version, « Envoyer la nouvelle version », qui crée une copie séparée sans les saisies de l'ancienne ; essayée sur le S23 Ultra avec QField 4.3.6 (projet envoyé par nemetonshiny, import, couches, bouton du plugin, fenêtre « même version » ; fenêtre « nouvelle version » et repli manuel pas encore essayés). Fonds de carte absents et légende à plat : défauts du `.qgs` de nemeton (brief `2026-10-17-projet-qgs-crs-groupes.md`). PR 5 faite : `terrain.md` (recevoir un projet par le serveur, réglage de l'adresse et du jeton, « Ouvrir dans QField », copies séparées dans QField, défauts connus du projet), `serveur.md` (reconstruire l'image avec le même jeton). Reste : essayer la fenêtre « nouvelle version » et le repli manuel. Briefs déposés le 2026-10-17 : nemeton (`groma_envoyer_projet()` ; chemins du GeoPackage et des rasters dans le `.qgz`, `briefs/vers-nemeton/2026-10-17-inventaire-video-chemins-qgz.md`), nemetonshiny (bouton « Envoyer au téléphone ») |
| 7h | Worker GPU à la demande sur Scaleway (brief `briefs/groma/2026-10-17-worker-gpu-scaleway.md`, point ouvert 4) | PR 1 faite : worker par HTTP (`groma-worker --api`, routes `/worker/…` à jeton distinct `GROMA_JETON_WORKER`, bail renouvelé et garde par tentative, téléchargement repris par `Range`, `--arret-inactif` ; 9 tests). PR 2 faite : image `server/Dockerfile.gpu` (base COLMAP officiel avec CUDA, PyTorch 2.10 cu128, `transformers`, MapAnything et SAM 2 épinglés, sans poids ; imports vérifiés à la construction ; CI quand `server/` ou `core/` changent, publication `ghcr.io/pobsteta/groma-worker-gpu`), construite ici sans GPU, jamais lancée sur GPU. Reste : adaptateurs `mapanything` et `profondeur_ia`, session manuelle sur une L4, démarrage à la demande, docs |
| 7i | Thème de l'application : jetons `terrain-style` v0.6.0 (issue #57, mission 002), publiés en 0.10.0, puis **retour aux anciennes couleurs** (mission 004, 2026-10-24) | fait : `Theme.kt` redevient celui d'avant la PR #58 (`VertVif` #3E7A40, bandeau et boutons verts), car avec `terrain-style` v0.6.0 le bandeau (`primary` #202F2B clair, #E2F4ED sombre) avait perdu son vert sur le S23 Ultra, comme chez Marculus (PR marculus #12) ; dépendance `terrain-style` et dépôt JitPack retirés, `testImplementation(kotlin("test-junit"))` et la CI `:app:testDebugUnitTest` gardés ; **réadoption attendue** avec Marculus à la sortie de `terrain-style` v0.6.1 (reste verte) ; défaut connu : blanc sur `VertVif` = 5,17:1 |
| 7j | Choix du thème Système / Clair / Sombre dans Paramètres (missions 005-006, comme Marculus) | fait : `ChoixTheme` dans `groma-core` (13 `@Test`, 14 cas, + 3 tests de `nuitForcee`), liste déroulante dans Paramètres, `Reglages.theme` enregistré (clé `theme`) ; toute l'appli suit le choix (`AppCompatDelegate.setDefaultNightMode` au démarrage et à chaque changement : Compose, `CaptureActivity`, boîtes de dialogue ; l'écran se recharge une fois au changement) ; à vérifier sur le S23 Ultra |
| 7k | Plein écran réglable dans Paramètres (mission 007, comme Marculus) | fait : `Reglages.pleinEcran` (clé `plein_ecran`, défaut désactivé), `appliquerPleinEcran` (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) appliqué par `MainActivity` (au démarrage et au changement) et `CaptureActivity` (à l'ouverture) ; `BandeauCompact` garde l'encoche en haut quand la barre de statut est masquée ; à vérifier sur le S23 Ultra |
| 7m | Vue Kanban des projets (mission 008, colonnes de Marculus et Surculus) | fait : la vue colonnes montre tous les projets (recherche appliquée, puce « Terminés » masquée) ; `EtatKanban`, `statutMinimal(ResumeProjet)`, `deposer`, `repartir`, `ReglagesKanban` dans `groma-core` (14 tests) ; `VueKanban` et bouton liste/colonnes dans `EcranProjets` ; statut et choix de vue en SharedPreferences seulement (rien dans le GeoPackage) ; icône colonnes dessinée (pas de `material-icons-extended`) ; à vérifier sur le S23 Ultra |
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
4. Image Docker GPU : COLMAP avec CUDA, VGGT, SAM 2. Brief écrit le
   2026-10-17 (`briefs/groma/2026-10-17-worker-gpu-scaleway.md`) : API
   toujours allumée sans GPU, worker GPU L4 créé à la demande sur Scaleway
   (outillage repris de maestronemeton), worker par HTTP, image CUDA sans
   poids, garde-fous de coût. Licence VGGT vérifiée : code sous « VGGT
   License » (usage commercial permis, non OSI) ; poids d'origine non
   commerciaux, `VGGT-1B-Commercial` sur demande. Complété le même jour :
   **MapAnything** (code et poids `map-anything-apache` sous Apache-2.0)
   remplace VGGT par défaut ; nouvelle méthode `profondeur_ia` (profondeur
   ARCore affinée par Prompt Depth Anything, repli Video Depth Anything
   Small ou Depth Anything 3 Metric, points de contrôle Apache seulement) ;
   GLOMAP à essayer dans COLMAP. À trancher : hébergement de l'API, VGGT en
   essai ou écarté, plafond d'heures GPU.
5. Première placette réelle : tourner, mesurer à la main, `benchmark/`.
6. **Chaîne nemeton / nemetonshiny livrée** (constaté le 2026-10-17) :
   `creer_projet_inventaire_video()` au schéma 1.2.0 avec plugin, symbologie
   de Marculus et fonds IGN (nemeton 2026.10.9, dépôt) ; sous-onglet
   « Inventaire vidéo » de nemetonshiny (2026.10.10). Nemeton installé ici :
   2026.10.12 (branche de la PR nemeton #566, le 2026-10-17). Bouton
   « Envoyer au téléphone » essayé sur Marsannay le 2026-10-17 (projet
   `20261015_194200_yzoh`, avec nemeton 2026.10.11).
   Annexe au brief nemeton (tables `projet`, `parcelles`, `ugf`, plusieurs centres par placette) : `briefs/vers-nemeton/2026-10-15-inventaire-video-carte.md`, déposée le 2026-10-15.
   Seconde annexe (plugin QField à livrer à côté du projet, noms de couches,
   ajout d'entités autorisé, `declinaison_deg` vide accepté) :
   `briefs/vers-nemeton/2026-10-16-inventaire-video-plugin-qfield.md`,
   déposée le 2026-10-16.
   Troisième annexe (essai QField du 2026-10-17 : projection du projet
   invalide faute de `ProjectionsEnabled`, d'où fonds IGN absents et échelle
   fausse ; emprise d'ouverture ; légende en trois groupes au lieu de 225
   couches à plat) : `briefs/vers-nemeton/2026-10-17-projet-qgs-crs-groupes.md`,
   déposée le 2026-10-17.
   **Traités par nemeton 2026.10.12** (constaté le 2026-10-17, PR nemeton
   #566 pas encore fusionnée) : cette troisième annexe, et le brief
   `2026-10-17-inventaire-video-chemins-qgz.md` par l'option 2 (dossier
   `<projet>/` ouvrable à côté du `.qgz`, qui ne change pas). nemeton
   propose à nemetonshiny de livrer ce dossier dans `video_zip_livraison()`
   (brief `vers-nemetonshiny/2026-10-17-inventaire-video-dossier-projet.md`).
   Rien à changer côté Groma : serveur et application lisent le `.qgz`, et
   le zip de « Ouvrir dans QField » reste valable. Vérifié le 2026-10-17
   dans QField 4.3.6 avec un projet régénéré : échelle juste et légende en
   groupes ; mais `arbres`, `cibles`, `ugf`, `projet`, Plan IGN et Ortho IGN
   invalides. Cause isolée par essais sur l'appareil : les identifiants de
   couches fixes (`arbres_01`…), identiques d'un projet à l'autre ; avec des
   identifiants nouveaux, tout est valide. Quatrième annexe (identifiants
   uniques à chaque génération, noms inchangés) :
   `briefs/vers-nemeton/2026-10-17-projet-qgs-ids-couches.md`, déposée le
   2026-10-17. Reste : fenêtre « nouvelle version » à essayer (mémoire des
   envois perdue au passage à l'APK de publication 0.9.0) ; tuiles
   OpenStreetMap refusées dans QField (règles d'usage du serveur de tuiles,
   à examiner).
7. ~~Déposer les briefs~~ : déposés le 2026-10-12 dans
   `/home/pascal/dev/briefs/vers-nemeton/` et `vers-nemetonshiny/`, en
   attente de traitement.

## Prochaine étape

Prototyper sur appareil la capture (`android/app`) et le transport vers
QField (point ouvert 1), puis tourner une placette test mesurée à la main
pour `benchmark/`.
