# Serveur de calcul

## Pipeline (`server/src/groma_server/chaine.py`)

1. **Segmentation des troncs** (optionnelle, SAM 2), pour ne reconstruire
   que les troncs et le sol.
2. **Reconstructions en parallèle**, derrière la même interface
   (`reconstruction/`) :
   * `colmap` (méthode `classique`) : modèle épars connu écrit à partir des
     poses ARCore, `point_triangulator`, puis densification
     (`patch_match_stereo`, qui demande CUDA) ;
   * `vggt` (méthode `ia`) : morceaux de 32 images qui se recouvrent sur 8,
     recollés et mis à l'échelle par similitude (Umeyama) sur la trajectoire
     ARCore ;
   * `profondeur` (méthode `embarque`) : fusion des cartes de profondeur
     ARCore, sans GPU. Elle sert de repli et de référence.
3. **Cibles ArUco** : détection OpenCV (l'identifiant vient toujours du
   décodage ArUco), puis triangulation multivue avec rejet des rayons
   aberrants.
4. **Sol** : MNT LiDAR recalé en altitude sur les points bas reconstruits
   (le contrôle sol/MNT en découle), sinon sol reconstruit.
5. **Tranche 1,20-1,40 m**, DBSCAN, cercles RANSAC et ajustement
   géométrique, fusion des arcs d'un même gros bois.
6. **Géoréférencement** : C0 vers le centre RTK, rotation par moindres
   carrés sur C1-C3 (déclinaison et convergence), échelle seulement si les
   distances la justifient.
7. **Visibilité, numérotation, hauteurs** par houppiers.
8. **Rapport de qualité** JSON et HTML. Il contient les écarts de diamètre
   sur les arbres cibles, les résidus d'azimut, de distance et de position,
   le recalage sur les apex des houppiers (alerte au-delà de 5°), les
   houppiers dominants sans tronc, la cohérence diamètre/hauteur/houppier,
   la fermeture de boucle, le calage sol/MNT, la pente, l'exposition et le
   recouvrement, et la comparaison des reconstructions.

La reconstruction retenue est celle qui retrouve le mieux les trois arbres
cibles (écart au ruban), puis, à égalité, classique > IA > embarqué.

## Sortie

`<plot_id>_definitif.gpkg` (couche `detections`, `etat_detection =
definitif`) et `<plot_id>_rapport.json` / `.html`, téléchargeables par
nemetonshiny :

| Méthode | Chemin |
|---|---|
| POST | `/televersements` ({`projet_id`, `plot_id`, `taille`, `sha256`}) puis PUT `/televersements/{id}?offset=N`, POST `/televersements/{id}/terminer` |
| GET | `/placettes?projet_id=…` (état de chaque placette, pour le bloc « Suivre » ; sans `projet_id`, tous les projets) |
| GET | `/taches/{id}` |
| GET | `/projets/{projet_id}/placettes/{plot_id}/resultat.gpkg`, `/rapport.json`, `/rapport.html` |
| GET | `/placettes/{plot_id}/resultat.gpkg`, … : projet par défaut |

Une placette est identifiée par le couple (`projet_id`, `plot_id`) : deux
plans d'échantillonnage peuvent avoir chacun une `P001`. `projet_id` est
l'identifiant du projet nemetonshiny (table `projet` du GeoPackage). Un
téléphone d'avant la liste des projets n'envoie pas de `projet_id` : ses
placettes vont dans le projet par défaut (`""`), servi par les chemins sans
`/projets/`. Paquets et résultats sont rangés par projet
(`paquets/<projet_id>/`, `resultats/<projet_id>/<plot_id>/<tâche>/`, `_`
pour le projet par défaut) ; une base de file d'attente plus ancienne est
migrée au démarrage.

Authentification : jeton porteur (`GROMA_JETON`).

## Projets QField pour les téléphones

nemetonshiny publie le projet de l'inventaire vidéo, et Groma le télécharge
(brief `briefs/groma/2026-10-17-envoi-projet-telephone.md`,
`groma_server/projets_qfield.py`). Le projet est le `.qgz` écrit par
`creer_projet_inventaire_video()`, avec le GeoPackage et les rasters
dedans, plus le plugin `<nom>.qml`.

| Méthode | Chemin |
|---|---|
| PUT | `/projets/{projet_id}/qfield/fichiers/{nom}` : corps brut, `<nom>.qgz` ou `<nom>.qml` ; réponse `{nom, sha256, taille}` |
| POST | `/projets/{projet_id}/qfield/publier` : `{projet_nom, fichiers: [{nom, sha256, taille}], genere_le?, nemeton_version?}` ; réponse `{projet_id, version}` |
| GET | `/projets` : manifeste de la version courante de chaque projet publié |
| GET | `/projets/{projet_id}/qfield` : manifeste de la version courante |
| GET | `/projets/{projet_id}/qfield/{version}/{nom}` : téléchargement, reprise par en-tête `Range` (réponse 206) |

À la publication, le serveur :
* vérifie la taille et le SHA-256 de chaque fichier ;
* exige exactement un `.qgz`, le `.qml` étant facultatif ;
* contrôle que le `.qgz` est une archive zip qui contient un `.qgs` et
  `inventaire_video.gpkg` ;
* vérifie que le `projet_id` de la table `projet` de ce GeoPackage est celui
  de l'URL. Sinon, il répond 422 et ne publie rien.

La version porte l'horodatage UTC (`AAAAMMJJTHHMMSSZ`) et la publication est
atomique. Le stockage est rangé dans `projets_qfield/<projet_id>/<version>/`,
avec `manifeste.json`. Taille maximale : `GROMA_TAILLE_MAX_PROJET` (500 Mo
par défaut, 413 au-delà). Versions gardées : `GROMA_VERSIONS_PROJET` (3 par
défaut). Le jeton est le même que pour le reste de l'API.

## Workers distants

Pour un GPU loué à la demande (brief
`briefs/groma/2026-10-17-worker-gpu-scaleway.md`), le worker peut tourner
sur une autre machine que l'API, sans volume ni file SQLite partagés : il
passe par l'API en HTTPS.

```bash
# côté API : active les routes /worker/… (jeton distinct de celui des téléphones)
GROMA_JETON_WORKER=...
# côté machine GPU
GROMA_JETON_WORKER=... groma-worker --api https://groma.exemple.fr --arret-inactif 600
```

| Méthode | Chemin |
|---|---|
| POST | `/worker/prendre` : `{id, projet_id, plot_id, tentative, taille, bail_s}`, ou 204 si la file est vide |
| GET | `/worker/taches/{id}/paquet?tentative=N` : le paquet, reprise par `Range` |
| POST | `/worker/taches/{id}/bail` `{tentative}` : prolonge le bail |
| PUT | `/worker/taches/{id}/resultats/{nom}?tentative=N` : `definitif.gpkg`, `rapport.json`, `rapport.html` (corps brut) |
| POST | `/worker/taches/{id}/terminer` `{tentative, verdict}` ; `/worker/taches/{id}/echouer` `{tentative, erreur}` |

* Le jeton des téléphones est refusé sur `/worker/…`, celui des workers
  ailleurs. Sans `GROMA_JETON_WORKER`, ces routes répondent 404.
* Chaque appel nomme la tentative pour laquelle il travaille. Le worker
  renouvelle son bail au tiers de sa durée (`GROMA_BAIL_S`, 3600 s par
  défaut). Bail expiré et tâche reprise par un autre : 409, le résultat est
  écarté. La même garde s'applique au worker local.
* Les résultats sont rangés comme ceux du worker local
  (`resultats/<projet_id>/<plot_id>/<tâche>/`) et archivés en S3 par l'API
  si `GROMA_S3_URL` est défini.
* `--arret-inactif` (secondes) arrête le worker quand la file reste vide,
  en mode local comme distant : l'instance GPU peut alors être supprimée.

## Déploiement

```bash
GROMA_JETON=... docker compose -f server/docker-compose.yml up -d
```

Après une mise à jour du code, reconstruire l'image (`up -d --build`) avec
le même `GROMA_JETON` : le volume de données est gardé, et les téléphones
comme nemetonshiny gardent leur réglage.

* PC avec carte graphique, ou machine GPU louée à la demande (ADR-003 :
  Scaleway GPU L4). Le worker GPU se construit avec une base CUDA
  (`--build-arg BASE=nvidia/cuda:…`) et une compilation de COLMAP avec CUDA.
* File de tâches SQLite (aucun courtier à exploiter). Une tâche interrompue
  repart après expiration de son bail. Deux tentatives par défaut.
* Stockage S3 compatible en option (`GROMA_S3_URL`), pour l'archivage.

## Validé ici / à valider

* Validé sur données synthétiques (paquet rendu par lancer de rayons :
  288 images 160×120, bruit de 3 mm + 0,2 % de la distance) : 100 % des
  arbres retrouvés, RMSE du diamètre de 1,2 cm, erreur de position
  ≤ 4 cm, cap retrouvé à 0,05° près, environ 15 s. Une erreur de 10° sur
  les azimuts est détectée par les houppiers.
* **Non exécutés ici** : COLMAP (non installé), VGGT et SAM 2 (pas de GPU),
  image Docker (pas de démon Docker). Leur code est écrit et ses parties
  pures sont testées : conversion des poses, quaternions, PLY, Umeyama,
  découpage en morceaux.
* Le bruit réel de la profondeur ARCore (1 à 2 % de la distance) dégradera
  la méthode `profondeur` : voir `benchmark/`.
