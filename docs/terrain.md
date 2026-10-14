# Protocole de terrain et chaîne téléphone

## Matériel

* Smartphone Samsung compatible ARCore avec Depth API, sans LiDAR.
* Récepteur GNSS RTK SparkFun (u-blox ZED-F9P) sur canne au centre de la
  placette, relié à QField en Bluetooth.
* Cibles ArUco du dictionnaire `4x4_50` : **C0 = identifiant 0** sur la canne
  du RTK, à 1,30 m ; **C1, C2, C3 = identifiants 1, 2, 3** agrafées à 1,30 m
  sur trois arbres, face au centre. Imprimer avec une marge blanche d'au moins
  un module et plastifier (mat).

## Préparer le projet à partir du plan d'échantillonnage

En attendant l'onglet « Inventaire vidéo » de nemetonshiny (brief du
2026-10-12), le projet Groma se prépare à partir du plan tiré dans l'onglet
Échantillonnage, enregistré dans `<projet>/data/samples.gpkg` (couche
`plots`), ou du GeoPackage du projet QField exporté (couche `placettes`) :

```bash
pip install -e "core[geo]"
cd ~/terrain
groma-preparer ~/.local/share/nemeton/projects/<id>/data/samples.gpkg \
    --rayon 15 --declinaison 2.1
adb push <id> /sdcard/Download/groma/
```

* `--rayon` (obligatoire) : rayon de la placette en mètres. Reprendre celui
  du plan (15 m par défaut dans nemetonshiny), pour filmer la surface qui a
  servi au tirage.
* `--declinaison` (obligatoire) : déclinaison magnétique en degrés, positive
  vers l'est (calculateurs IGN ou NOAA).
* `--sortie` : dossier produit. Par défaut `./<id>`, l'identifiant du projet
  nemetonshiny : **le dossier garde ce nom sur le téléphone**, Groma s'en
  sert pour reconnaître le projet.
* `--nom` : nom affiché dans Groma. Par défaut celui du projet
  nemetonshiny ; obligatoire, comme `--sortie`, pour un plan qui ne vient
  pas d'un dossier de projet nemetonshiny (export QField, fichier copié).
* Options : `--seuil` (7,5 cm), `--unite deg|gr`, `--placettes P001,P004`,
  `--base-seulement` (sans les placettes de remplacement), `--ecraser`.

Les colonnes du plan (`type`, `visit_order`, `stratum`, `ip`, `wgt`…) sont
gardées telles quelles. Un `inventaire_video.gpkg` existant n'est jamais
remplacé sans `--ecraser`. Il n'a pas de rasters : MNT, MNH et houppiers
restent à la charge de nemeton.

La table `projet` du GeoPackage décrit le projet pour la liste des projets
de Groma : nom et description, communes, surface de la zone (surface SIG
des parcelles) et cadastrale, nombre de parcelles, d'UG et de placettes
(base et remplacement), date du plan et de la préparation. Ces valeurs sont
lues, sans rien y écrire, dans `metadata.json` et `data/parcels.gpkg` du
projet nemetonshiny ; sans elles, la table ne garde que le nom et les
nombres de placettes.

La couche `parcelles` (schéma 1.2.0) reprend les parcelles cadastrales de
`data/parcels.gpkg` (identifiant, commune, section, numéro, contenance), en
Lambert 93, pour la carte de Groma. Elle reste vide quand le plan ne vient
pas d'un projet nemetonshiny ; les GeoPackages préparés avant ne l'ont pas.

## Projets sur le téléphone

Groma liste les projets du **dossier des projets**, choisi une fois par
⋮ → « Dossier des projets… » (par défaut `Téléchargements/groma/`). Chaque
sous-dossier qui contient `inventaire_video.gpkg` est un projet ; il garde
le nom que lui donne `groma-preparer`, l'identifiant du projet
nemetonshiny :

```
Téléchargements/groma/
├── 20261008_212542_uywc/inventaire_video.gpkg
└── 20261007_084614_qapz/inventaire_video.gpkg
```

* Un nouveau dossier est copié dans le stockage de Groma (copie de
  travail) à l'ouverture de l'application ou par ⋮ → « Actualiser ». La
  saisie se fait dans cette copie.
* Chaque carte montre le nom de la forêt, les communes, la surface de la
  zone et le nombre d'UG, les placettes (base + remplacement) et le rayon,
  l'avancement (placettes Base faites, envoyées) et la surface inventoriée
  (Σ π·r² des placettes faites). La puce « Terminés » bascule vers les
  projets dont toutes les placettes Base sont faites.
* **Mettre à jour** (menu de la carte) : quand le dossier contient une
  préparation plus récente que la copie de travail. Refusé dès que la copie
  contient des données de terrain (centre, cibles, détections, placette
  traitée) : rien de ce qui a été saisi n'est écrasé.
* **Retirer du téléphone** : supprime la copie de travail et le dossier
  source ; refusé tant qu'une placette faite n'est pas envoyée.
* ⋮ → « Importer un dossier… » copie un dossier situé ailleurs.
* Un GeoPackage préparé avant la table `projet` (Groma < 0.3) s'affiche
  avec le nom de son dossier et ses seuls nombres de placettes.

### Déposer les dossiers

Par câble USB, avec `adb` :

```bash
adb push 20261008_212542_uywc 20261007_084614_qapz /sdcard/Download/groma/
```

ou en glissant les dossiers dans `Download/groma` depuis l'explorateur de
fichiers de l'ordinateur (téléphone en mode « Transfert de fichiers »).

Au premier lancement, ⋮ → « Dossier des projets… » ouvre le sélecteur
d'Android sur `Download/groma` : toucher **Utiliser ce dossier**, puis
**Autoriser**. Sur Samsung, cette seconde confirmation est indispensable :
sans elle, Groma n'a pas accès au dossier et la liste reste vide.
L'autorisation est gardée, même après un redémarrage.

### Mettre à jour un projet

Quand le plan change dans nemetonshiny (placettes ajoutées, rayon corrigé)
avant le début du terrain :

```bash
groma-preparer ~/.local/share/nemeton/projects/<id>/data/samples.gpkg \
    --rayon 15 --declinaison 2.1 --ecraser
adb push <id> /sdcard/Download/groma/
```

Après « Actualiser », la carte signale « Nouvelle version disponible dans le dossier des
projets » ; menu
de la carte → **Mettre à jour**. Dès qu'une placette a été commencée sur
le téléphone, la mise à jour est refusée : envoyer les placettes faites,
puis préparer un nouveau projet nemetonshiny pour la suite.

## Déroulé sur une placette

1. Planter la canne au point prévu (QField guide jusqu'au point GRTS).
   Attendre le fix RTK, enregistrer le centre dans la couche
   `centres_mesures` (QField remplit la géométrie, `qualite_gnss`,
   `precision_h_m`).
2. Poser C1-C3 sur trois arbres bien répartis (idéalement à 120° les uns des
   autres, entre 5 et 15 m). Pour chacune, saisir dans `cibles` : azimut lu à
   la boussole de visée **dans l'unité de la placette**, distance horizontale
   du centre à la cible, diamètre de l'arbre au ruban.
3. Dans Groma, choisir la placette et filmer : départ et arrivée à la canne
   (fermeture de boucle), trois tours concentriques environ à 4, 10 et 16 m,
   en regardant alternativement vers l'extérieur et vers le centre, téléphone
   à hauteur de poitrine, sans courir. L'écran indique le suivi, les cibles
   vues, la couverture, et alerte en cas de flou, de vitesse excessive ou de
   perte de suivi.
4. « Terminer » : verdict **suffisante / à refaire** avec la raison
   (couverture < 90 %, suivi perdu plus de 5 s, cible vue moins de 3 fois,
   plus de 30 % d'images floues). Puis calcul provisoire, objectif moins de
   2 minutes : arbres numérotés, diamètres et hauteurs approximatifs.
5. Dans QField, saisir l'essence (`essence_arbre`), le statut, la qualité,
   les notes et les photos. Les arbres provisoires apparaissent dans la
   couche `arbres`.
6. Le paquet de la placette part au serveur dès qu'un réseau Wi-Fi est
   disponible (4G sur option), avec reprise après coupure.

## Échange entre Groma et QField (décision 3)

Groma n'écrit **que** dans la couche `detections`, en transaction SQLite
courte (`BEGIN IMMEDIATE`, attente de verrou de 5 s). Les déclencheurs du
GeoPackage (`core/src/groma/donnees/declencheurs.sql`) créent la ligne
`arbres` d'une détection nouvelle et ne mettent à jour que les colonnes de
mesure d'un arbre encore provisoire. Les attributs saisis dans QField ne
sont jamais écrits par Groma. Une hauteur mesurée (`h_source = mesuree`) est
conservée. Un faux positif n'est jamais promu. Testé en Python
(`core/tests/test_gpkg.py`) et en Kotlin (`GeoPackageTest`, sur le GeoPackage
produit par GDAL).

`placettes`, `arbres` et `detections` sont créées **sans index spatial**.
Les déclencheurs R*Tree de GDAL appellent `ST_IsEmpty`, absente du SQLite
d'Android, et ils se préparent sur tout `UPDATE`. Le cas a été vérifié par un
test.

### Point ouvert : stockage restreint d'Android 11+

Depuis Android 11, QField importe les projets dans **son** dossier privé
(`Android/data/ch.opengis.qfield/files/…`) et travaille sur cette copie
([documentation QField](https://docs.qfield.org/get-started/storage)). Une
autre application ne peut pas l'ouvrir, même avec l'accès à tous les
fichiers. Groma ne peut donc pas écrire directement dans le GeoPackage que
QField édite. Le protocole ci-dessus reste valable, mais il faut un
transport entre les deux bacs à sable. Options, par ordre de préférence :

1. **Plugin de projet QField** (`inventaire_video.qml`, livré dans le `.qgz`
   à côté du `.qgs` : QField le charge après accord de l'utilisateur). Il lit
   les détections provisoires exposées par Groma sur `http://127.0.0.1:8765/detections/<projet_id>/<plot_id>`
   (`/detections/<plot_id>` reste servi quand un seul projet a cette placette)
   (`ServeurLocal.kt`) et les écrit dans la couche `detections` **par QField
   lui-même**, ce qui déclenche les mêmes déclencheurs. Un seul processus
   écrit alors dans le fichier. **À valider sur appareil** : accès réseau
   local et API d'ajout d'entités depuis un plugin.
2. Groma écrit dans sa propre copie, puis l'utilisateur exporte vers QField.
   À écarter : QField réimporte et écrase sa copie, donc la saisie en cours.
3. Android ≤ 10, ou QField configuré sur un dossier partagé : écriture
   directe par Groma (`CalculEmbarque.ecrire`), déjà implémentée.

Tant que l'option 1 n'est pas validée, le calcul embarqué reste visible dans
Groma et le paquet part au serveur. Le rapprochement définitif ne dépend pas
de ce point.

## Corrections RTK Centipède (NTRIP)

* **QField ≥ 4.2** intègre un client NTRIP : il reçoit le flux RTCM du
  caster et le transmet au récepteur par la même liaison Bluetooth, BLE ou
  TCP. Le ZED-F9P calcule la position corrigée et renvoie des trames NMEA
  ([annonce QField 4.2](https://qfield.org/blog/2026/06/09/qfield-4.2-coral-sea-reaching-sub-centimeter-accuracy-out-of-the-box/),
  [API QfNtripClient](https://api.qfield.org/QField/classQfNtripClient/)).
  C'est la configuration recommandée : réglages > positionnement, récepteur
  Bluetooth, NTRIP activé.
* Paramètres Centipède usuels : caster `caster.centipede.fr`, port `2101`,
  point de montage = base la plus proche (carte sur centipede.fr),
  identifiant et mot de passe `centipede`. **À vérifier** sur
  docs.centipede.fr avant la première sortie.
* Variantes : application relais (Lefebure NTRIP Client, SW Maps) qui pousse
  le RTCM au récepteur, ou client NTRIP du micrologiciel SparkFun RTK via un
  partage de connexion Wi-Fi du téléphone.
* **NTRIP exige Internet (4G)**. Sans couverture en forêt, pas de fix RTK en
  temps réel. Repli : enregistrer les observations brutes UBX sur le
  récepteur, puis post-traiter (PPK) avec RTKLIB (BSD-2) contre les données
  RINEX de la base Centipède. Le centre est alors corrigé au bureau, avant
  l'intégration. Le calcul serveur utilise le centre présent dans le paquet :
  si le centre est corrigé après coup, il faut renvoyer le paquet.
* Contrôle : `qualite_gnss` doit valoir `fix` ; un `float` est signalé dans
  le rapport de qualité.

### Récepteur RTK dans Groma

Groma peut aussi tenir le récepteur pour afficher la position sur sa carte :
Paramètres → **Récepteur RTK** (Bluetooth : appareil appairé ; ou TCP),
corrections NTRIP par Groma (Centipède par défaut : caster
`caster.centipede.fr`, port `2101`, point de montage choisi par
« Charger », identifiant et mot de passe `centipede`), **Tester** et
diagnostic (trames NMEA reçues, statut du caster, octets GGA et RTCM). Le
récepteur démarre à l'ouverture de la carte et s'arrête à la sortie du
projet ; la notification « Groma — GNSS RTK » montre le fix et propose
**Libérer le récepteur**. Sans récepteur, la carte se replie sur le GNSS du
téléphone, signalé par le badge (« Position du téléphone, précision 4 m »).

**Une liaison Bluetooth SPP ne sert qu'une application à la fois** : pendant
que Groma tient le récepteur, QField n'y a pas accès, et inversement.
Libérer le récepteur dans Groma avant de passer à QField.

## Unités et nords

Les azimuts mesurés sont **magnétiques**, dans l'unité de la placette (degrés
ou grades). Groma applique `declinaison_deg` (positive vers l'est), puis la
convergence des méridiens Lambert-93 (`convergence_deg`, jusqu'à ±3° en
France). La numérotation se fait par rapport au **nord géographique**.
