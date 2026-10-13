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

## Déploiement

```bash
GROMA_JETON=... docker compose -f server/docker-compose.yml up -d
```

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
