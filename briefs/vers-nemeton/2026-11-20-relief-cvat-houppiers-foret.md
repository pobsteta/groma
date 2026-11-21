# Brief vers nemeton : relief CVAT, MNT et houppiers sur toute la forêt

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemeton/2026-11-20-relief-cvat-houppiers-foret.md`).
* **Destinataire** : `nemeton` (`R/inventaire_video.R`,
  `creer_projet_inventaire_video()`, et `R/houppiers.R`). Une ligne pour
  `nemetonshiny` au § 4.
* **Type de changement** : `feat:` (schéma 1.7.0 ; les projets déjà préparés
  restent lisibles, Groma se replie sur les rasters par placette).
* **Révision** : version corrigée le 2026-11-20, le jour même, qui remplace la
  première : dalles posées à plat dans `rasters/`, pas de dalles MNH, et
  présence exigée dans le `.qgz`. Complétée le 2026-11-21 (§ 2.3, § 3 et
  § 6) : conversion du CVAT produit par foretaccess, constatée sur
  l'exemple réel de Marsannay (mission 044).

## 1. Contexte

Essai du 2026-11-20 à Marsannay (projet `20261015_194200_yzoh`) : Pascal a
filmé une placette **près du chemin**, à 152 m du centre prévu de P067
(choix volontaire). Les données LiDAR du projet ne couvrent que le centre
prévu plus 10 m de marge (carré de 60 m) : pour cette placette, le serveur
n'a ni MNT (« MNT ne couvre pas la reconstruction ») ni houppiers. Il perd
le calage du sol et le contrôle d'orientation sur les sommets des
houppiers, alors que la placette avait 4,2° de résidu d'orientation et
7,7 m de fermeture de boucle.

Décisions de Pascal (2026-11-20) :

* une placette peut être déplacée sur le terrain : les données doivent
  couvrir **toute la forêt**, pas seulement les placettes prévues ;
* sur la carte de Groma : **relief CVAT** (celui de nemetonshiny),
  **contours des houppiers** et **sommet de chaque houppier** ; pas de
  raster MNT ni MNH affiché ;
* sur toute la forêt, **MNT et CVAT, pas de MNH** : les hauteurs et le
  contrôle d'orientation viennent des houppiers (`h_max`, sommets). Seul
  le taux de couvert du rapport utilise le MNH ; il reste calculé pour
  les placettes prévues, à partir des rasters par placette.

## 2. Changement du schéma (source : `core/src/groma/donnees/inventaire_video.schema.json`, version 1.7.0)

### 2.1 Emprise

L'emprise de la forêt est l'union des `ugf` du projet, augmentée de
**50 m**. Faute d'`ugf`, l'union des `parcelles` ; faute de parcelles,
l'emprise actuelle (placettes plus `marge_m`).

### 2.2 Dalles MNT

* Chemin : `rasters/mnt_{x}_{y}.tif`, **directement dans `rasters/`**, sans
  sous-dossier. `x` et `y` sont, en kilomètres Lambert-93, le coin
  **sud-ouest** de la dalle (par exemple `rasters/mnt_0849_6689.tif` couvre
  849 000-850 000 m en X et 6 689 000-6 690 000 m en Y). Quatre chiffres,
  complétés par des zéros. Ces noms ne peuvent pas se confondre avec ceux
  des rasters par placette (`{plot_id}_mnt.tif`).
* Dalles de **1 km × 1 km**, alignées sur le kilomètre, 0,5 m (2 000 × 2 000
  pixels). Seules les dalles qui touchent l'emprise sont écrites. Les pixels
  hors de l'emprise valent nodata.
* Format inchangé (celui de `rasters.format`) : GeoTIFF Float32, une bande,
  bandes non tuilées, DEFLATE sans prédicteur, nodata -9999, EPSG:2154.
  Groma ne lit que ce format.
* **Pas de dalles MNH.** Les rasters par placette `rasters/{plot_id}_mnt.tif`
  et `rasters/{plot_id}_mnh.tif` restent écrits comme aujourd'hui.

### 2.3 Relief CVAT

* Chemin : `rasters/cvat.tif`, un seul fichier sur l'emprise.
* GeoTIFF **Byte**, une bande, 2 m (`cvat_res_m` de nemetonshiny), EPSG:2154,
  DEFLATE. Valeurs 1 à 255 = relief `[0, 1]` de `vat_combined()` mis à
  l'échelle (`1 + round(v * 254)`), **0 = nodata** (hors de l'emprise
  LiDAR).
* **Conversion du CVAT de foretaccess.** Le CVAT que nemetonshiny calcule
  aujourd'hui (`build_cvat_precomputed()`, fichier
  `*_CVAT_8bit_foretaccess.tif`) est bien en Byte à 2 m, mais avec un autre
  codage : relief **0 à 254** (`round(v * 254)`) et **255 = nodata**. Il
  n'est pas lisible tel quel par Groma (0 y serait pris pour « sans
  donnée », 255 pour du relief). nemeton doit donc le **convertir lui-même**
  au format ci-dessus en écrivant `cvat.tif` : valeur + 1 pour 0 à 254
  (soit `1 + round(v * 254)`), 255 → 0, puis 0 hors de l'emprise. Un CVAT
  déjà au format ci-dessus (nodata 0) est recopié sans changement ; tout
  autre codage (autre nodata, autre type, autre résolution) est refusé avec
  une erreur, jamais converti en silence. Groma fait la même conversion
  dans `exemples/creer_marsannay_p067.py` (`decouper_cvat()`), qui sert de
  référence.
* Pas de symbologie : Groma l'affiche en niveaux de gris semi-transparents
  sur le fond de carte.

### 2.4 Houppiers

* Table `houppiers` : **toute l'emprise**, pas seulement les placettes.
  `plot_id` reste renseigné quand le houppier touche une placette prévue
  (même règle qu'aujourd'hui), sinon NULL.
* Deux colonnes nouvelles, requises : `x_sommet` et `y_sommet` (REAL,
  Lambert-93, m), position du sommet localisé par `segment_houppiers()`
  (celui qui a servi à faire croître le houppier ; pour `"watershed"` et
  `"lsms"`, le pixel du MNH où se trouve `h_max`). C'est le « sommet du
  houppier » affiché sur la carte. Un index B-tree sur (`x_sommet`,
  `y_sommet`) : le téléphone n'a pas le module R*Tree et filtre par
  `BETWEEN` sur ces colonnes.

### 2.5 Dans le `.qgz` et dans le dossier du projet

Le `.qgz` produit par `creer_projet_inventaire_video()` est ce que reçoit
le téléphone (« Projets du serveur… », après `groma_envoyer_projet()`).
Groma n'en extrait que `inventaire_video.gpkg` et les fichiers
`rasters/<nom>.tif` posés **directement** sous `rasters/` (un sous-dossier
serait ignoré). Les dalles MNT et `cvat.tif` doivent donc être **dans le
`.qgz`**, sous `rasters/`, comme les rasters par placette aujourd'hui, et
dans le dossier du projet quand il est copié à la main.

## 3. À faire dans nemeton

1. `segment_houppiers()` : renvoyer `x_sommet`, `y_sommet` pour chaque
   houppier (les sommets sont déjà calculés : les garder au lieu de les
   jeter).
2. `creer_projet_inventaire_video()` :
   * calculer l'emprise (§ 2.1) ;
   * segmenter les houppiers sur toute l'emprise et les écrire avec les deux
     colonnes nouvelles ;
   * écrire les dalles MNT (§ 2.2) ;
   * nouvel argument `cvat_path = NULL` : si fourni, découper le CVAT sur
     l'emprise et l'écrire au format du § 2.3, en convertissant le codage de
     foretaccess (0–254, 255 nodata → 1–255, 0 nodata) ; sinon pas de
     `cvat.tif` ;
   * mettre dalles et `cvat.tif` dans le `.qgz` (§ 2.5) ;
   * recopier le schéma 1.7.0 dans `inst/inventaire_video/`.
3. Projet QGIS/QField : les dalles et `cvat.tif` repliés et décochés, comme
   les rasters par placette aujourd'hui.

## 4. À faire dans nemetonshiny

Passer à `creer_projet_inventaire_video()` le CVAT déjà calculé du projet
(`cvat_path`), quand il existe ; ne pas le calculer pour l'occasion.

## 5. Ordres de grandeur (Marsannay)

Emprise des `ugf` : 4,3 × 3,2 km, 506 ha de forêt. Au plus 20 dalles MNT de
1 km, environ 16 Mo chacune avant compression ; `cvat.tif` : environ
2 100 × 1 600 pixels, quelques Mo.

## 6. Critères d'acceptation

* Sur un projet de test, chaque point de l'emprise est couvert par une dalle
  MNT ; les dalles portent le nom de leur coin sud-ouest et sont directement
  sous `rasters/`, dans le dossier et dans le `.qgz`.
* Aucune dalle MNH n'est écrite.
* Un houppier hors de toute placette est écrit avec `plot_id` NULL ; chaque
  houppier a `x_sommet`, `y_sommet` à l'intérieur de son polygone.
* `cvat.tif` est en Byte, avec 0 hors de l'emprise LiDAR, et présent dans le
  `.qgz` quand `cvat_path` est fourni.
* Avec le `*_CVAT_8bit_foretaccess.tif` de nemetonshiny en `cvat_path`,
  `cvat.tif` a nodata 0, ses valeurs valent celles de la source + 1, et les
  pixels à 255 dans la source valent 0 ; un CVAT d'un autre codage est
  refusé avec une erreur.
* Sans `cvat_path`, le projet est écrit sans `cvat.tif` et sans erreur.
* Les rasters par placette sont inchangés.
