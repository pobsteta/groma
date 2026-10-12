# Brief vers nemeton : inventaire vidéo Groma

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemeton/2026-10-12-inventaire-video.md`).
* **Destinataire** : `nemeton` (cœur métier). Le pendant interface est
  `briefs/vers-nemetonshiny/2026-10-12-inventaire-video.md`.
* **Type de changement** : `feat:` (nouvelles fonctions exportées), plus une
  évolution **non cassante** de `validate_field_data()` et
  `get_arbre_schema()` (nouveaux arguments, valeurs par défaut inchangées).
  Version selon les consignes de release : `AAAA.M.N` du jour.

## 1. Contexte

Groma remplace la mesure manuelle d'une placette circulaire (compas,
boussole, décamètre) par une vidéo au smartphone. L'application Android
calcule un inventaire **provisoire** sur le terrain, le serveur un
inventaire **définitif**. nemeton porte toute la logique côté plateforme :
création du GeoPackage de terrain, hauteurs par houppiers, numérotation de
contrôle, rapprochement provisoire → définitif, validation, agrégats,
marquage de la source pour le NDP. nemetonshiny n'en fait que l'interface.

Décisions tranchées par Pascal le 2026-10-12 :

1. **Essences** : nouvelle colonne `essence_arbre` (essence d'arbre, saisie
   terrain, liste rattachée à la BD Forêt V2). `espece` (type de peuplement
   nemeton) en est **déduite** par `map_bdforet_essence()`. Le schéma stable
   `get_arbre_schema()` ne change pas de sens.
2. **Diamètres extrêmes** : **nemeton évolue**. Tout diamètre est accepté,
   avec signalement au-delà de 200 cm (voir § 5).
3. **Écriture concurrente sur le téléphone** : couche `detections` séparée,
   écrite par la seule application Groma, et reliée à `arbres` par `uuid` au
   moyen de déclencheurs SQLite (voir § 2.3).

## 2. GeoPackage `inventaire_video.gpkg`

Source unique du schéma : `core/src/groma/donnees/inventaire_video.schema.json`
du dépôt groma_nemeton. **À copier dans `inst/inventaire_video/`**, avec
`declencheurs.sql` et `essences_bdforet_v2.json` du même dossier. EPSG:2154.

### 2.1 Couches

| Couche | Géométrie | Index spatial | Contenu |
|---|---|---|---|
| `placettes` | POINT (point prévu GRTS) | **non** | schéma nemeton + colonnes de plan (`wgt`, `ip`, `stratum`, `strat_*`) en champs cachés, **jamais modifiées**, + `rayon_m` (20), `seuil_diam_cm` (7,5), `unite_azimut` (`deg`/`gr`), `declinaison_deg`, `convergence_deg` (caché, calculé), `etat_traitement` (`a_faire`/`provisoire`/`envoye`/`definitif`) |
| `centres_mesures` | POINT (centre RTK) | oui | `plot_id`, `qualite_gnss` (`fix`/`float`), `precision_h_m`, `ecart_prevu_m`, `date_mesure` |
| `cibles` | POINT | oui | `plot_id`, `id_cible` (C1-C3), `azimut_mesure`, `distance_mesuree_m`, `dbh_mesure_cm` |
| `arbres` | POINT | **non** | colonnes nemeton (`plot_id`, `tree_id`, `espece`, `dbh_cm`, `h_m`, `statut`, `qualite`, `photos`, `notes`) + `uuid`, `num_provisoire`, `azimut`, `distance_m`, `essence_arbre`, `h_source` (`mnh`/`mesuree`/`aucune`), `houppier_id`, `etat_detection` (`provisoire`/`definitif`/`a_completer`/`a_verifier`/`faux_positif`), `methode` (`embarque`/`classique`/`ia`) |
| `detections` | POINT | **non** | écrite par Groma seul, en lecture seule dans QField : `uuid`, `plot_id`, `num_provisoire`, `azimut`, `distance_m`, `dbh_cm`, `h_m`, `h_source`, `houppier_id`, `etat_detection`, `methode`, `score`, `date_detection` |
| `houppiers` | POLYGON | oui | sortie de `segment_houppiers()` : `plot_id`, `houppier_id`, `h_max`, `surface_m2` (lecture seule) |

Écarts au brief initial, à valider :

* `date` dans `centres_mesures` devient `date_mesure` (mot réservé SQL).
* `convergence_deg` est ajoutée à `placettes` : convergence des méridiens
  Lambert-93 au point prévu, `n × (lon − 3°)` avec n = 0,7256077650532670.
  La numérotation est faite par rapport au nord **géographique**, et l'écart
  avec le nord du quadrillage atteint ±3° en France, bien plus que la
  tolérance d'égalité de 1°. `az_geo = az_grille + convergence_deg`.
* `azimut` reste rendu dans l'unité de la placette. La tolérance d'égalité
  reste 1° quelle que soit l'unité.

### 2.2 Options d'écriture GDAL (impératives)

* `GEOMETRY_NAME=geom`, `FID=fid` pour toutes les couches.
* **`SPATIAL_INDEX=NO` pour `placettes`, `arbres` et `detections`.** Les
  déclencheurs R*Tree de GDAL (`rtree_<t>_geom_update4/5`) portent sur tout
  `UPDATE` et appellent `ST_IsEmpty`. SQLite résout les fonctions dès la
  préparation, et le SQLite d'Android ne les connaît pas : toute mise à jour
  par Groma échouerait (« no such function: ST_IsEmpty »). C'est vérifié
  par un test. Avec sf : `layer_options = c("SPATIAL_INDEX=NO", "GEOMETRY_NAME=geom", "FID=fid")`.
* Après écriture des couches, exécuter `declencheurs.sql` (par exemple
  `DBI::dbExecute()` sur une connexion RSQLite, une instruction à la fois).

### 2.3 Déclencheurs (décision 3)

`trg_detections_insert` : une détection dont l'`uuid` est inconnu de
`arbres` (et qui n'est pas un `faux_positif`) crée la ligne `arbres`
(`tree_id = num_provisoire`, `statut = 'vivant'`). `trg_detections_update` :
ne met à jour que les colonnes de mesure d'un arbre encore `provisoire` ou
`a_verifier`, garde `h_m` si `h_source = 'mesuree'`, et n'écrit jamais
`essence_arbre`, `statut`, `qualite`, `notes` ni `photos`. Aucune fonction
spatiale.

### 2.4 Rasters

MNT et MNH découpés par placette (rayon + 10 m de marge), à 0,5 m, **en
GeoTIFF à côté du GeoPackage** : `rasters/<plot_id>_mnt.tif` et
`rasters/<plot_id>_mnh.tif`. Float32, une bande, **en bandes (non tuilé),
DEFLATE sans prédicteur**, nodata −9999 :
`gdal = c("COMPRESS=DEFLATE", "PREDICTOR=1", "TILED=NO")` avec
`terra::writeRaster()`. Pourquoi pas un raster GeoPackage : les tuiles du
format sont en PNG ou JPEG, donc 8 bits sans l'extension « gridded
coverage ». Le GeoTIFF Float32 est lu nativement par QField, et
l'application lit cette variante sans GDAL (`GeoTiff.kt`, testé sur
l'exemple).

### 2.5 Projet QField

Construit comme `create_qgis_project()` (même XML de formulaires, même
écriture atomique, même contrôle du nom de projet), avec :

* les couches du § 2.1 et les rasters par placette ;
* `essence_arbre` : `ValueMap` code → libellé depuis `essences_bdforet_v2.json` ;
* champs cachés (`Hidden`) : colonnes de plan, `uuid`, `convergence_deg` ;
  champs en lecture seule : `espece`, `num_provisoire`, `azimut`,
  `distance_m`, `houppier_id`, `methode`, et toute la couche `detections` ;
* étiquette `"tree_id" || ' – ' || "dbh_cm"` sur `arbres` ;
* symbologie catégorisée de `arbres` selon `etat_detection` ;
* un **plugin de projet** `<projet>.qml` à côté du `.qgs`, réservé (voir
  `docs/terrain.md` de groma_nemeton, point ouvert sur Android 11+). Le
  brief en donnera le contenu une fois validé sur appareil.

GeoPackage d'exemple conforme : `exemples/inventaire_video/` du dépôt
groma_nemeton (deux placettes synthétiques ; P001 porte des détections
provisoires et des essences saisies).

## 3. Fonctions à créer (noms NMT, à confirmer)

| Fonction | Rôle |
|---|---|
| `schema_inventaire_video()` | lit le schéma JSON de `inst/inventaire_video/` ; liste de descripteurs au format de `.field()` |
| `creer_projet_inventaire_video(placettes, plot_ids = NULL, rayon_m = 20, seuil_diam_cm = 7.5, unite_azimut = c("deg", "gr"), declinaison_deg = NULL, avec_lidar = TRUE, dem = NULL, chm = NULL, marge_m = 10, output_dir, project_name = "inventaire_video", region = "BFC", lang = "fr", overwrite = TRUE)` | `.qgz` du § 2. MNT et MNH via `resolve_project_dem()` / `resolve_project_chm()` quand `dem`/`chm` sont `NULL` ; houppiers par `segment_houppiers(chm, aoi = st_buffer(placette, rayon_m + marge_m))` ; `pente_pct`, `exposition`, `recouvrement_pct` remplis s'ils sont vides (pente et exposition par plan ajusté sur le MNT dans le rayon, « flat » sous 5 % ; recouvrement = part des cellules MNH > 2 m) ; `declinaison_deg` à saisir (pas de modèle géomagnétique embarqué) |
| `numeroter_arbres(x, y, dbh_cm, id, rayon_m, seuil_diam_cm, unite_azimut, tolerance_deg = 1)` | contrôle de la numérotation. **Implémentation de référence fournie** : `briefs/vers-nemeton/R/numerotation.R`, qui passe les 10 cas communs |
| `affecter_hauteur_houppier(arbres, houppiers, h_min = 1, h_max = 70)` | point dans polygone sur la position du tronc ; houppier le plus haut en cas de superposition ; rien hors houppier (pas de repli sur le plus proche) ; hauteurs hors [1, 70] ignorées ; `h_source = "mesuree"` jamais écrasée ; renseigne `h_m`, `h_source`, `houppier_id` |
| `rapprocher_arbres(provisoires, definitifs, centre, convergence_deg = 0, tolerance_m = 0.5, ...)` | § 4 |
| `valider_inventaire_video(gpkg)` | `validate_field_data(..., dbh_max_cm = Inf, dbh_alerte_cm = 200)` + contrôles propres : `tree_id` unique et non vide, `uuid` unique, domaines `etat_detection` / `h_source` / `methode` / `essence_arbre`, colonnes de plan identiques au plan d'origine, `faux_positif` exclus |
| `importer_inventaire_video(gpkg, resultats = NULL, field_data_gpkg = NULL)` | bloc « Intégrer » : `import_qgis_gpkg()`, retrait des `faux_positif`, `espece` ← `map_bdforet_essence(bdforet(essence_arbre))`, rapprochement avec les GeoPackages définitifs du serveur, `valider_inventaire_video()`, `aggregate_plot_metrics(plot_radius = rayon_m)`, `tag_field_data_sources(..., source = "inventaire_video")`, puis fusion par `plot_id` (§ 6) |
| `groma_etat_placettes(url, jeton)` et `groma_telecharger_resultats(url, jeton, plot_ids, dossier)` | client de l'API du serveur Groma (`GET /placettes`, `GET /placettes/{plot_id}/resultat.gpkg | rapport.json | rapport.html`). `httr2` en `Suggests`. Bloc « Suivre » |

## 4. Rapprochement provisoire → définitif

Implémentation de référence : `core/src/groma/rapprochement.py`. Cas
communs : `fixtures/rapprochement/*.json` (à copier dans
`tests/testthat/fixtures/rapprochement/`).

* `uuid` stable, distinct de `tree_id`.
* Affectation optimale (méthode hongroise, `clue::solve_LSAP()` ou
  équivalent) sur la position, paires à plus de `tolerance_m` (0,5 m)
  interdites. Coût = distance + 0,01 m par cm d'écart de diamètre, pour
  départager deux candidats proches. Le cas 03 montre qu'un appariement
  glouton perd un arbre.
* Apparié : attributs terrain conservés (`essence_arbre`, `espece`,
  `statut`, `qualite`, `notes`, `photos`, `h_m` mesurée) ; position,
  `dbh_cm`, `methode` et hauteur MNH mis à jour ; `definitif` si
  `essence_arbre` est renseignée, sinon `a_completer`. Un `faux_positif`
  saisi par l'opérateur le reste (alerte s'il est retrouvé).
* Alerte `ecart_dbh` si l'écart dépasse 10 cm **et** 50 % du plus petit
  diamètre ; l'appariement est conservé.
* Nouveau (définitif sans provisoire) : nouvel `uuid`, `statut = "vivant"`,
  `a_completer`.
* Provisoire non retrouvé : `a_verifier`, attributs et position conservés.
* Numérotation recalculée sur les arbres hors `a_verifier` et
  `faux_positif`. L'ancien numéro va dans `num_provisoire`. Les `a_verifier`
  prennent `V1, V2…` et les `faux_positif` `F1, F2…` (par azimut), pour que
  `tree_id` reste unique et non vide sans décaler les numéros des arbres
  retrouvés. Les numéros modifiés sont rendus `(uuid, ancien, nouveau)`.
* Un arbre qui sort de l'inventaire une fois mesuré (hors rayon ou sous le
  seuil) est gardé `a_verifier` s'il porte des attributs terrain ou s'il
  était déjà provisoire, et abandonné sinon.
* Option : les arbres `a_completer` et `a_verifier` peuvent être renvoyés
  sur le terrain dans un nouveau `.qgz` filtré.

Critère d'acceptation : **aucun attribut saisi sur le terrain n'est perdu**,
sur tous les cas (test dédié dans `core/tests/test_rapprochement.py`, à
reproduire en testthat).

## 5. Diamètres extrêmes (décision 2)

* `validate_field_data()` : nouveaux arguments `dbh_max_cm = 300` et
  `dbh_alerte_cm = NULL` (défauts actuels, donc aucun changement pour les
  appelants). `dbh_cm > dbh_max_cm` reste une erreur ; `dbh_cm >
  dbh_alerte_cm` devient un avertissement « DBH above alert threshold ».
* `get_arbre_schema()` : argument `dbh_max_cm = 300` qui fixe le `max` du
  widget.
* L'inventaire vidéo appelle `dbh_max_cm = Inf, dbh_alerte_cm = 200`, et le
  formulaire QField utilise `max = 1000`. Le serveur Groma signale déjà les
  diamètres > 200 cm dans son rapport.

## 6. Agrégats, sources et NDP

* `aggregate_plot_metrics(placettes, arbres, plot_radius = 20)`. Le rayon
  vient de `placettes$rayon_m` ; s'il varie d'une placette à l'autre,
  agréger par groupe de rayon.
* `tag_field_data_sources()` : argument facultatif `source = "inventaire_terrain"`
  stocké dans `attr(data, "field_data_source")`. L'inventaire vidéo passe
  `"inventaire_video"`. `detect_ndp()` le traite comme un inventaire terrain
  (NDP 3 « Diagnostic ») et ajoute `"inventaire_video"` aux sources rendues.
* Fusion dans `data/field_data.gpkg` du projet **par `plot_id`, sans
  écraser** un inventaire classique : les placettes absentes sont ajoutées ;
  une placette déjà inventoriée classiquement est conservée, et la version
  vidéo est rangée à côté avec `source = "inventaire_video"`. Choisir
  laquelle des deux entre dans les agrégats est une option explicite
  (`priorite = c("classique", "video")`, défaut `"classique"`).

## 7. Référentiel d'essences

`map_bdforet_essence()` (correspondance partielle sur `bdforet_mapping` de
`inst/species/BFC.json`) envoie aujourd'hui vers `essence_mixte` plusieurs
libellés BD Forêt V2 du référentiel (vérifié en R) :

| Libellé BD Forêt V2 | Résultat actuel | Proposition |
|---|---|---|
| Sapin, épicéa | essence_mixte | essence_pessiere_sapiniere |
| Chênes sempervirents | essence_mixte | essence_chene_vert |
| Robinier | essence_mixte | essence_feuillus_pionniers |
| Pin maritime, Pin d'Alep, Pin à crochets, pin cembro, Autres pins | essence_mixte | essence_pinede |

Proposition : ajouter ces entrées à `bdforet_mapping`. Ce sont des données,
pas un changement d'API.

## 8. Tests (testthat, édition 3)

* `test-numerotation.R` : fourni (`briefs/vers-nemeton/tests/`). Il boucle
  sur `fixtures/numerotation/*.json` et passe les 10 cas avec
  `numerotation.R`.
* `test-rapprochement.R` : sur `fixtures/rapprochement/*.json`, mêmes
  assertions que `core/tests/test_rapprochement.py`, dont l'absence de perte
  d'attribut.
* `test-hauteur-houppier.R` : sur `fixtures/hauteurs_houppiers/cas.json`.
* `test-inventaire-video-gpkg.R` : **fourni**
  (`briefs/vers-nemeton/tests/test-integration-gpkg.R`). Il fait passer le
  GeoPackage d'exemple dans `import_qgis_gpkg()` → `validate_field_data()`
  → `aggregate_plot_metrics(plot_radius = 20)`, vérifie que les colonnes de
  plan sont intactes et déduit `espece` d'`essence_arbre`. **Exécuté le
  2026-10-12 avec les sources actuelles de nemeton : 14 attentes, 0 échec.**
* `creer_projet_inventaire_video()` : test sur un MNT/MNH synthétique
  (absence d'index spatial sur les trois couches, présence des
  déclencheurs, insertion dans `detections` → ligne `arbres` créée par une
  connexion RSQLite sans fonction spatiale).

Copie à déposer dans `/home/pascal/dev/briefs/vers-nemeton/2026-10-12-inventaire-video.md`.
