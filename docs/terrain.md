# Protocole de terrain et chaîne téléphone

## Matériel

* Smartphone Samsung compatible ARCore avec Depth API, sans LiDAR.
* Récepteur GNSS RTK SparkFun (u-blox ZED-F9P) sur canne au centre de la
  placette, relié à QField en Bluetooth.
* Boussole de visée (recommandée pour l'azimut des cibles : le téléphone, lui,
  se laisse tromper par les masses métalliques) et télémètre laser (distance
  horizontale du centre à la cible) ; ruban de diamètre.
* Cibles ArUco du dictionnaire `4x4_50` : **C0 = identifiant 0** sur la canne
  du RTK, à 1,30 m ; **C1, C2, C3 = identifiants 1, 2, 3** agrafées à 1,30 m
  sur trois arbres, face au centre. Planche prête à imprimer, une cible par
  page A4 : `docs/cibles_groma_a4.pdf` (motif de 15 cm, module de 2,5 cm,
  marge blanche de 3 cm). L'imprimer à 100 % (taille réelle), noir sur papier
  blanc mat, sans rien écrire ni agrafer dans la marge blanche, et plastifier
  (mat). La taille du motif ne sert qu'à la détection : l'échelle vient
  d'ARCore et des distances mesurées. Pour la régénérer :
  `python -m groma_server.planche_cibles docs/cibles_groma_a4.pdf`.

## Préparer le projet à partir du plan d'échantillonnage

En attendant l'onglet « Inventaire vidéo » de nemetonshiny (brief du
2026-10-12), le projet Groma se prépare à partir du plan tiré dans l'onglet
Échantillonnage, enregistré dans `<projet>/data/samples.gpkg` (couche
`plots`), ou du GeoPackage du projet QField exporté (couche `placettes`) :

```bash
pip install -e "core[geo]"
cd ~/terrain
groma-preparer ~/.local/share/nemeton/projects/<id>/data/samples.gpkg \
    --rayon 15
adb push <id> /sdcard/Download/groma/
```

* `--rayon` (obligatoire) : rayon de la placette en mètres. Reprendre celui
  du plan (15 m par défaut dans nemetonshiny), pour filmer la surface qui a
  servi au tirage.
* `--declinaison` (facultatif) : déclinaison magnétique en degrés, positive
  vers l'est. Sans elle, `declinaison_deg` est calculée **pour chaque
  placette** par le modèle WMM2025 de la NOAA (embarqué, valable de 2025 à
  2030), à sa position et à la date du jour : environ 2 à 3° est en France
  en 2026, + 0,1 à 0,2° par an. Une valeur saisie est prioritaire ; un
  avertissement s'affiche si elle s'écarte du modèle de plus de 1°.
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

La couche `ugf` (même schéma) donne le parcellaire des unités de gestion
forestière : les ténements de `data/tenements.gpkg` fusionnés par UG, avec
le libellé, la forêt et le numéro de parcelle ONF de `data/ugs.json`. La
carte les trace en vert, numéro ONF au centre. Sans `ugs.json`, les
contours viennent sans libellé.

## Projets sur le téléphone

Groma liste les projets du **dossier des projets**, choisi une fois par
⋮ → « Dossier des projets… » (par défaut `Téléchargements/groma/`). Chaque
sous-dossier qui contient `inventaire_video.gpkg` est un projet ; il garde
le nom que lui donne `groma-preparer`, l'identifiant du projet
nemetonshiny :

```
Téléchargements/groma/
├── 20261015_194200_yzoh/          reçu du serveur
│   ├── inventaire_video.gpkg
│   ├── rasters/P001_mnt.tif …
│   └── qfield/inventaire_video.qgz, inventaire_video.qml
├── 20261008_212542_uywc/inventaire_video.gpkg   déposé par câble
└── 20261007_084614_qapz/inventaire_video.gpkg
```

Un projet arrive de deux façons : **par le serveur Groma**, envoyé depuis
nemetonshiny (recommandé, avec le projet QField), ou **par câble**.

* Un nouveau dossier est copié dans le stockage de Groma (copie de
  travail) à l'ouverture de l'application ou par ⋮ → « Actualiser ». La
  saisie se fait dans cette copie.
* Chaque carte montre le nom de la forêt, les communes, la surface de la
  zone et le nombre d'UG, les placettes (base + remplacement) et le rayon,
  l'avancement (placettes Base faites, envoyées) et la surface inventoriée
  (Σ π·r² des placettes faites). La puce « Terminés » bascule vers les
  projets dont toutes les placettes Base sont faites.
* Ordre de la liste : le dernier projet ouvert en tête, puis les autres du
  plus proche au plus éloigné (distance au centre de l'emprise, depuis la
  dernière position connue du téléphone, affichée sur la carte du projet :
  « à 3,2 km »), sinon par nom. L'écran Projets n'écoute pas le GNSS : la
  position vient de la dernière connue d'Android ou de la carte.
* **Mettre à jour** (menu de la carte) : quand le dossier contient une
  préparation plus récente que la copie de travail. Refusé dès que la copie
  contient des données de terrain (centre, cibles, détections, placette
  traitée) : rien de ce qui a été saisi n'est écrasé.
* **Retirer du téléphone** : supprime la copie de travail et le dossier
  source ; refusé tant qu'une placette faite n'est pas envoyée.
* **Ouvrir dans QField** (menu de la carte) : seulement pour un projet reçu
  du serveur, voir plus bas.
* ⋮ → « Importer un dossier… » copie un dossier situé ailleurs.
* Un GeoPackage préparé avant la table `projet` (Groma < 0.3) s'affiche
  avec le nom de son dossier et ses seuls nombres de placettes.

### Recevoir un projet par le serveur

1. **Dans nemetonshiny** (Terrain → Inventaire vidéo) : « Générer le
   projet QField », puis **« Envoyer au téléphone »**
   (`nemeton::groma_envoyer_projet()`). Le serveur vérifie le `.qgz` et
   publie une nouvelle version du projet ; il garde les 3 dernières
   (`docs/serveur.md`).
2. **Dans Groma**, une fois : ⋮ → Paramètres → « Envoi au serveur » :
   * **Adresse du serveur**, par exemple `https://groma.exemple.fr`.
     Serveur sur l'ordinateur et téléphone branché en USB :
     `adb reverse tcp:8000 tcp:8000`, puis `http://127.0.0.1:8000` (la
     redirection tombe à chaque débranchement ; en Wi-Fi, mettre l'adresse
     de l'ordinateur sur le réseau local, port 8000 ouvert) ;
   * **Jeton d'accès** : le `GROMA_JETON` du serveur. Le champ est masqué ;
     pour un long jeton, toucher le champ puis, depuis l'ordinateur,
     `adb shell input text <jeton>`.
3. ⋮ → **« Projets du serveur… »** : chaque projet publié, avec sa date et
   son état sur le téléphone (« Pas encore sur le téléphone », « À jour »,
   « Version plus récente sur le serveur »). **Télécharger** :
   * en Wi-Fi, ou en 4G si « Autoriser l'envoi en 4G » est coché ;
   * reprise après une coupure, empreinte SHA-256 vérifiée à la fin,
     progression dans la notification ;
   * installation dans `Download/groma/<projet_id>/` : le GeoPackage et
     les rasters sont extraits du `.qgz`, le `.qgz` et le plugin `.qml`
     rangés dans `qfield/`.
   Remplacer un projet déjà présent demande confirmation. La copie de
   travail n'est jamais touchée : pour la rafraîchir, **Mettre à jour** sur
   la carte du projet (refusé dès qu'elle contient des données de terrain).

### Ouvrir le projet dans QField

Menu de la carte du projet → **« Ouvrir dans QField »** (projet reçu du
serveur, avec `qfield/`). Groma prépare un zip (`.qgz` et `.qml` côte à
côte, `inventaire_video.gpkg` et `rasters/` à côté d'eux) et le passe à
QField, qui l'importe dans « Imported Projects/<projet_id> » et l'ouvre.
Au premier lancement du projet, QField demande d'autoriser le plugin :
accepter (« Remember my choice ») ; son bouton apparaît dans la barre des
plugins.

QField travaille ensuite sur **sa** copie : c'est là que vont les saisies.

* **Même version, déjà envoyée** : Groma propose **« Ouvrir QField »**.
  Rouvrir le projet depuis QField (projets récents). « Renvoyer » ne sert
  que si la copie de QField a été supprimée : il crée une copie séparée
  (`<projet_id>_1`…), sans les saisies de la première.
* **Nouvelle version reçue du serveur** : **« Envoyer la nouvelle
  version »** crée elle aussi une copie séparée dans QField. L'ancienne y
  reste avec ses saisies ; ne pas mélanger les deux.
* **QField ne reçoit pas le projet** (absent, ou version qui refuse le
  zip) : Groma explique l'import à la main. Dans QField, « Ouvrir un fichier
  local », menu ⋮ → « Importer un projet depuis un dossier »,
  `Téléchargements/groma/<projet_id>/qfield`.

Défauts connus du projet produit par nemeton (brief
`briefs/vers-nemeton/2026-10-17-projet-qgs-crs-groupes.md`) : la
projection du projet n'est pas activée, donc **les fonds IGN et OSM ne
s'affichent pas** et la barre d'échelle est fausse ; la légende aligne à
plat les deux rasters (MNT, MNH) de chaque placette. Les couches de l'inventaire et la saisie ne sont
pas touchées.

### Déposer les dossiers par câble

Pour un projet préparé par `groma-preparer`, ou sans serveur. Par câble
USB, avec `adb` :

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
avant le début du terrain : régénérer le projet QField, « Envoyer au
téléphone », puis « Projets du serveur… » → Télécharger. Ou, par câble :

```bash
groma-preparer ~/.local/share/nemeton/projects/<id>/data/samples.gpkg \
    --rayon 15 --ecraser
adb push <id> /sdcard/Download/groma/
```

Après « Actualiser », la carte signale « Nouvelle version disponible dans le dossier des
projets » ; menu
de la carte → **Mettre à jour**. Dès qu'une placette a été commencée sur
le téléphone, la mise à jour est refusée : envoyer les placettes faites,
puis préparer un nouveau projet nemetonshiny pour la suite.

## Carte du projet

Dans la liste des placettes d'un projet, l'icône **Carte** ouvre la carte.
Groma demande la localisation précise à la première ouverture (jamais en
arrière-plan) et la notification du service RTK si un récepteur est
réglé.

* **Fonds** (bouton des couches) : Plan IGN, Ortho IGN, OpenStreetMap ;
  cadastre IGN en superposition. Tuiles IGN de la Géoplateforme, sans clé,
  attribution affichée.
* **Couches**, chacune masquable : UGF (vert, numéro ONF), parcelles
  (jaune), parcours (ligne dans l'ordre `visit_order`, ▲ rouge au départ,
  ◎ à l'arrivée), placettes (pastille blanche à faire, orange provisoire,
  bleue envoyée, verte définitive ; plus petite pour une placette de
  remplacement ; cercle au rayon réel à partir du zoom 18), centres
  mesurés (croix violette, reliée au point prévu au-delà de 1 m d'écart). Le nombre d'objets est indiqué à côté
  de chaque couche ; une couche absente du GeoPackage (projet ancien) est
  simplement vide.
* **Boutons** : zoom, **Ma position** (premier appui : recentre ; second :
  **suivi**, la carte se déplace avec l'opérateur), **Cadrer le projet**.
* **Badge du fix** en haut (RTK fixe, flottant, DGPS, autonome, sans
  position ; ou « Position du téléphone, précision 4 m ») : il ouvre
  **État GNSS** (source, fix, précision horizontale, coordonnées, satellites
  utilisés et en vue, HDOP/PDOP, âge des corrections, station de
  référence).
* **Feuille de placette** (toucher une placette) : identifiant, type, état,
  rayon, rang dans le parcours, écart du centre mesuré au point prévu,
  distance et azimut depuis la position ; boutons **Y aller** et **Filmer
  la placette**.

### Aller à une placette

**Y aller** (feuille de placette), ou **Aller à la prochaine placette à
faire** (première placette du parcours encore à faire) ouvre un bandeau
en bas de la carte :

* distance à vol d'oiseau, et **azimut magnétique dans l'unité de la
  placette** (degrés ou grades, avec `declinaison_deg`) à viser à la
  boussole, l'azimut géographique entre parenthèses (« Azimut 358°
  magnétique (0° géographique) ») ; sans déclinaison connue, l'azimut est
  donné en géographique et signalé ;
* flèche relative au cap du téléphone (« Pas de boussole : suivre
  l'azimut » si le téléphone n'en a pas) et ligne de la position à la
  placette ;
* à moins de 5 m, « Arrivé à P012 » et **Mesurer le centre** ;
  **Suivante** passe à la placette suivante du parcours, **Arrêter le
  guidage** ferme le bandeau. L'écran reste allumé pendant le guidage.

### Mesurer le centre

Canne immobile sur le point, bulle calée : **Mesurer le centre** fait la
moyenne des positions **RTK fixes** pendant la durée réglée (10 s par
défaut, Paramètres → Récepteur RTK) ; les positions non fixes sont
écartées et comptées. Le résultat (« Centre enregistré : RTK fixe,
précision 0,02 m, à 1,3 m du point prévu ») est ajouté à
`centres_mesures`.

* En RTK flottant, Groma demande confirmation (« Enregistrer en
  flottant ») ; `qualite_gnss` vaut alors `float`.
* Avec la position du téléphone, ou sans correction, la mesure est
  refusée.
* Si la placette a déjà un centre, le nouveau est **ajouté** (l'ancien est
  conservé) et c'est lui qui compte, pour la carte comme pour la capture.
* La capture reprend ce centre (le plus récent) à la place du point prévu.
  Tant que le transport vers QField n'est pas réglé (point ouvert
  ci-dessous), il reste dans la copie de travail de Groma et n'apparaît
  pas dans la copie QField.

### Carte hors ligne

La forêt est souvent sans réseau : avant de partir, sur le Wi-Fi, ouvrir la
carte du projet → menu → **Hors ligne…**. Choisir Plan IGN ou Ortho IGN
(et le cadastre si besoin) : Groma annonce le nombre de tuiles et le volume
(emprise du projet plus 200 m, zooms 12 à 18), puis télécharge en tâche de
fond. La progression s'affiche sous le badge GNSS ; un téléchargement
interrompu reprend là où il s'est arrêté. Les tuiles vont dans le dossier
du projet (`tuiles/`), sont gardées à la mise à jour du projet et
supprimées avec lui. OpenStreetMap ne se télécharge pas (politique des
tuiles OSM). Vérifier en mode avion que le fond s'affiche.

## Déroulé sur une placette

1. Planter la canne au point prévu. Dans Groma, carte du projet → **Y
   aller** (ou « Aller à la prochaine placette à faire ») : distance et
   azimut magnétique dans l'unité de la placette. À moins de 5 m, attendre
   le fix RTK, caler la bulle et toucher **Mesurer le centre** (voir
   « Carte du projet ») : ligne ajoutée à `centres_mesures` avec
   `qualite_gnss`, `precision_h_m`, `ecart_prevu_m` et `date_mesure`.
   **Libérer le récepteur** avant de passer à QField. Le centre peut aussi
   être saisi dans QField, comme avant. Une **seule cible C0** sur la canne,
   **orientée vers le début du premier tour** (décision de Pascal du
   2026-11-11).
2. Poser C1-C3 sur trois arbres bien répartis (idéalement à **120°** les uns
   des autres, entre **8 et 15 m** du centre). Puis, dans Groma, bouton
   **Placette** (ancien « Cibles »), dans la liste des placettes ou sur la carte du
   projet (feuille de la placette) (c'est ici, et plus dans QField, que les
   cibles se saisissent). Le plan de la placette s'affiche, nord en haut,
   avec le cercle du rayon et C0 au centre (le dernier centre mesuré ; sans
   centre mesuré, le centre prévu, avec une mention en rouge). Pour chaque
   cible : choisir C1, C2 ou C3, taper l'**azimut magnétique lu à la boussole
   de visée** dans l'unité de la placette (source `boussole_visee`, par
   défaut) ou, en option, lire le cap à la boussole du téléphone (« Prendre
   ce cap », source `boussole_telephone` ; ne l'utiliser que si l'étalonnage
   affiché est « bon »), puis la distance horizontale au télémètre et le
   diamètre au ruban, et toucher **Placer**. La cible apparaît sur le plan.
   Groma alerte (sans jamais refuser la saisie) si deux cibles sont à moins
   de 60° d'écart, si une cible est à moins de 8 m du centre, ou s'il y a
   moins de trois cibles. Replacer une cible remplace la mesure précédente.
   Un degré d'erreur d'azimut décale la cible de 26 cm à 15 m : viser avec
   soin. Les cibles sont écrites dans la couche `cibles` du projet de Groma
   (schéma 1.3.0, colonne `source_azimut`) ; la source de chaque azimut part
   aussi au serveur avec le paquet (`cibles_mesurees`). Une distance ou un
   diamètre de 0 est accepté (diamètre 0 : non mesuré au ruban).
   *Projets déjà générés :* la couche `cibles` reste éditable dans la copie
   de QField, mais Groma ne relit pas cette copie : une cible saisie ou
   modifiée dans QField n'est pas reprise. Les cibles déjà présentes dans le
   projet de Groma restent lues et affichées, et peuvent être replacées.
3. Dans Groma, choisir la placette et filmer : départ et arrivée à la canne
   (fermeture de boucle), trois tours concentriques environ à 4, 10 et 16 m,
   en regardant alternativement vers l'extérieur et vers le centre, téléphone
   à hauteur de poitrine, sans courir. L'écran indique le suivi, les cibles
   vues, la couverture, et alerte en cas de flou, de vitesse excessive ou de
   perte de suivi.
4. « Terminer » : verdict **suffisante / à refaire** avec la raison
   (couverture < 90 %, suivi perdu plus de 5 s, cible vue moins de 3 fois,
   plus de 30 % d'images floues). Puis calcul provisoire, objectif moins de
   2 minutes : arbres numérotés, diamètres et hauteurs approximatifs. Rouvrir
   **Placette** : les arbres détectés (hors faux positifs) apparaissent sur le
   plan, en pastilles. Couleur = groupe d'essence comme dans Marculus (chêne
   bleu, hêtre indigo, feuillus gris-bleu, sapin bordeaux, épicéa rouille,
   autres résineux taupe), **grise tant que l'essence n'est pas saisie** ;
   taille croissante avec le diamètre (bornée, 7,5 à 80 cm) ; contour blanc
   fin = diamètre estimé par la vidéo, **contour sombre épais = diamètre
   mesuré au ruban**. L'étiquette « numéro – diamètre » s'affiche quand la
   place le permet (zoomer pour les voir toutes). Pincer ou « + » / « − »
   pour zoomer, glisser pour déplacer (faire défiler la page en glissant
   hors du plan), « Tout voir » pour revenir.
5. Saisir les attributs des arbres : **dans Groma** (décision du 2026-11-11),
   sur le plan, en **touchant un arbre** : la fiche s'ouvre avec son numéro et
   son diamètre estimé. Essence : taper un code ou un nom (recherche dans la
   BD Forêt v2, accents et casse ignorés) puis choisir dans la liste ; diamètre
   au ruban en cm ; statut ; qualité. **Enregistrer** écrit et ferme ;
   **Enregistrer et arbre suivant** passe à l'arbre suivant dans l'ordre des
   numéros, pour enchaîner au ruban. Une valeur refusée est expliquée sous le
   champ et rien n'est écrit. Ce qui est saisi : l'essence (`essence_arbre`, code BD Forêt v2), le **diamètre au ruban**
   (`dbh_cm`, virgule ou point décimal ; il passe `dbh_source` à `mesure` ;
   **le ruban se saisit seulement dans Groma**, `dbh_cm` et `dbh_source`
   sont en lecture seule dans QField), le statut et la qualité ; **dans
   QField** les notes et les photos, et au besoin l'essence, le statut et
   la qualité. Les arbres provisoires apparaissent dans la couche
   `arbres` de QField. Un diamètre au ruban n'est jamais écrasé : ni par
   une nouvelle capture, ni par le calcul définitif du serveur (l'alerte
   `ecart_dbh` reste émise s'il diffère de 10 cm **et** de 50 %) ; vider le
   diamètre dans Groma revient à l'estimation de la vidéo. Pour passer ce
   que Groma a saisi à QField, appuyer sur le bouton du plugin (voir
   « Échange entre Groma et QField ») : **rien de ce qui a été saisi dans
   QField n'est écrasé**.
6. **Arbre oublié par la vidéo** (mission 023, schéma 1.5.0) : dans
   **Placette**, « Ajouter un arbre ». Le centre de la placette doit avoir été
   **mesuré** (sinon le bouton « Saisir le diamètre au ruban » est grisé).
   Saisir l'**azimut** depuis le centre (dans l'unité de la placette, degrés
   ou grades ; boussole de visée tapée, ou boussole du téléphone comme pour
   les cibles) et la **distance horizontale** en mètres : l'emplacement
   s'affiche en **cercle pointillé** sur le plan. « Saisir le diamètre au
   ruban » ouvre la fiche : le **diamètre au ruban est obligatoire** (0 à
   1000 cm) ; essence, statut et qualité sont facultatifs. L'arbre reçoit le
   numéro provisoire **A1, A2…** (par placette), `methode = terrain`,
   `dbh_source = mesure`, `etat_detection = provisoire` ; il est écrit
   directement dans `arbres` (rien dans `detections`) et part avec l'archive
   de terrain. Sa pastille a un **contour pointillé violet**. Dans sa fiche,
   « Supprimer cet arbre » (avec confirmation) n'existe que pour un arbre
   ajouté à la main ; un arbre de la vidéo ne se supprime jamais (le diamètre
   d'un arbre ajouté ne peut pas être vidé non plus). Au calcul définitif,
   un arbre ajouté apparié à une détection (tolérance 0,5 m) garde son
   ruban et ses saisies ; non apparié, il est **conservé**, `a_verifier`,
   numéro `V<n>`, et listé dans les arbres non retrouvés. *Projets déjà
   générés :* la valeur `terrain` est une simple valeur de domaine, il n'y a
   rien à migrer (la colonne `dbh_source` est ajoutée comme pour la
   mission 018 si elle manque).
7. Le paquet de la placette part au serveur dès qu'un réseau Wi-Fi est
   disponible (4G sur option), avec reprise après coupure.

## Envoi des saisies de terrain au serveur (sans QField)

QField est **facultatif** : tout ce qui est saisi dans Groma (centre mesuré,
cibles, essence, diamètre au ruban, statut, qualité) part au serveur dans
une archive de terrain, `<plot_id>.terrain.zip`, que nemetonshiny peut
intégrer sans passer par la copie de QField (brief
`briefs/vers-nemeton/2026-11-11-terrain-sans-qfield.md`). Le plugin et
« Ouvrir dans QField » restent disponibles.

* **Quand** : après chaque enregistrement (fiche d'arbre, arbre ajouté ou supprimé, cible, centre),
  Groma attend **30 s** après la dernière modification (valeur provisoire, à
  juger sur le terrain : plusieurs saisies de suite ne font qu'un envoi), puis
  envoie. Même serveur, même jeton et même réglage Wi-Fi / 4G que l'envoi de
  la vidéo (Paramètres) ; l'envoi reprend après une coupure.
* **Rien d'inutile** : si le contenu n'a pas changé depuis le dernier envoi
  réussi (empreinte SHA-256 des lignes et des photos), rien n'est envoyé.
* **État** : dans la liste des placettes, « Terrain à envoyer » (saisie pas
  encore arrivée) ou « Terrain envoyé à HH:MM ». Sans serveur configuré, la
  saisie reste « à envoyer » ; elle part à l'enregistrement suivant, une fois
  le serveur réglé.
* **Contenu** : `terrain.gpkg` (même schéma, **cette placette seule**) et
  `photos/`. Un renvoi remplace le précédent côté serveur.
* **À vérifier** : saisir une placette dans Groma sans ouvrir QField,
  attendre « Terrain envoyé à … », puis télécharger l'archive depuis le
  serveur (`GET /projets/<projet_id>/placettes/<plot_id>/terrain.zip`) : ses
  arbres et leurs saisies y sont.

## Échange entre Groma et QField (décision 3)

Groma n'écrit que dans quatre couches de **sa** copie du GeoPackage, en
transaction SQLite courte (`BEGIN IMMEDIATE`, attente de verrou de 5 s) :
`detections`, `centres_mesures` pour un centre mesuré sur sa carte, `cibles`
et, pour les seuls attributs saisis après la capture (essence, diamètre au
ruban, statut, qualité), `arbres` (`GeoPackage.modifierArbre`, par `uuid`,
seules les colonnes saisies ; une valeur vide n'efface que son attribut ;
colonne `dbh_source` ajoutée, et déclencheurs recréés à l'identique de
`declencheurs.sql`, dans un projet préparé avant le schéma 1.4.0). Un centre n'est jamais
réécrit : une nouvelle mesure ajoute une ligne, et c'est la plus récente
(`fid` le plus grand) qui compte. Comme `centres_mesures` a l'index spatial
de GDAL, Groma retire ses déclencheurs R*Tree le temps de l'insertion, écrit
lui-même la ligne d'index, puis les remet à l'identique, dans la même
transaction (`GeoPackage.ajouterCentreMesure`, testé). Les déclencheurs du
GeoPackage (`core/src/groma/donnees/declencheurs.sql`) créent la ligne
`arbres` d'une détection nouvelle et ne mettent à jour que les colonnes de
mesure d'un arbre encore provisoire. Les attributs saisis (essence,
statut, qualité, notes, photos) ne sont jamais écrits par les déclencheurs.
Une hauteur mesurée (`h_source = mesuree`) et un diamètre au ruban
(`dbh_source = mesure`) sont conservés. Un faux positif n'est jamais promu. Testé en Python
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

1. **Plugin de projet QField** (`qfield/inventaire_video.qml`, prototype).
   Copié à côté du projet sous le nom `<projet>.qml` (pas *dans* le
   `.qgz` : QField le cherche sur le disque ; dans un `.zip` importé, le
   mettre à côté du `.qgz`), il est chargé à l'ouverture du projet après
   accord de l'utilisateur (« Remember my choice » ; l'accord est à
   redonner si le projet est réimporté ailleurs). Il ajoute un bouton à la
   barre des plugins. Un appui :
   * lit `projet_id` dans la couche `projet` ;
   * appelle `http://127.0.0.1:8765/inventaire/<projet_id>`
     (`ServeurLocal.kt`) : détections provisoires de toutes les placettes
     (exports des captures), dernier centre mesuré de chaque placette et
     attributs saisis dans Groma sur les arbres (copie de travail de Groma),
     en Lambert-93 ;
   * ajoute **par QField lui-même** les détections absentes (clé `uuid`) à
     `detections` et les centres absents (clé `plot_id` + `date_mesure`) à
     `centres_mesures`, en une édition validée par couche : les
     déclencheurs du GeoPackage créent les lignes `arbres` comme pour une
     écriture directe, et un seul processus écrit dans le fichier ;
   * recopie ensuite, par `uuid`, ce que Groma a saisi sur les arbres
     (réponse `arbres` du serveur local : `essence_arbre`, `dbh_cm`,
     `dbh_source`, `statut`, `qualite`) dans `arbres` de QField, **sans jamais
     écraser une saisie de QField** (règle `RecopieQfield.aRecopier`, Kotlin,
     reproduite ligne à ligne dans le plugin) : essence et qualité seulement
     si vides dans QField ; statut seulement si vide ou `vivant` dans QField ;
     diamètre et `dbh_source` ensemble, seulement si Groma a un diamètre au
     ruban et QField une source vide ou `estime`, et jamais dans un projet
     QField antérieur à 1.4.0 (sans colonne `dbh_source` : un diamètre tapé
     dans QField y reste) (le `dbh_cm` de QField n'est
     jamais vide : il porte l'estimation de la vidéo) ; une édition validée
     par couche ;
   * n'écrit rien si la couche est déjà en cours d'édition (il ne valide
     jamais les modifications de quelqu'un d'autre), et affiche un bilan
     (« … n attributs recopiés »). La recopie est à essayer sur l'appareil
     (`layer.changeAttributeValue` depuis le QML, pas encore vérifié sur
     QField) ;

   Le serveur local vit avec l'application. Après une capture ou une
   mesure de centre, Groma affiche la notification « Groma — partage avec
   QField » (service de premier plan) : Android ne coupe pas Groma pendant
   qu'on travaille dans QField. Le partage s'arrête seul après 30 min sans
   demande du plugin, ou par **Arrêter le partage** ; ensuite, rouvrir
   Groma avant d'appuyer sur le bouton du plugin. API vérifiées dans le source de QField 3.7 à 4.3
   (`FeatureUtils.createFeature`, `LayerUtils.addFeature`,
   `iface.createHttpRequest` à partir de 4.1, sinon `XMLHttpRequest` ;
   trafic HTTP en clair autorisé par QField). Essayé le 2026-10-16 sur un
   Galaxy S23 Ultra avec QField 4.3.5 : détections ajoutées. Centres mesurés
   et détections d'une vraie capture pas encore essayés.

   Le projet QField complet est généré par nemetonshiny (Terrain →
   Inventaire vidéo → « Générer le projet QField », qui appelle
   `nemeton::creer_projet_inventaire_video()`), envoyé au téléphone par le
   serveur Groma et passé à QField par « Ouvrir dans QField », plugin
   compris (voir « Projets sur le téléphone »). Essayé le 2026-10-17 sur le
   S23 Ultra avec QField 4.3.6 : import, couches et bouton du plugin. Pour un essai sans
   nemetonshiny, projet de test minimal (couches, plugin, symbologie ; sans
   formulaires complets ni rasters) :

   ```bash
   python3 qfield/creer_projet_test.py <dossier du projet>   # PyQGIS requis
   adb push <dossier du projet> /sdcard/Download/qfield-test/
   ```

   puis, dans QField, « Ouvrir un projet local » → « Importer un projet
   depuis un dossier ».

   Le projet de test reprend la symbologie de Marculus (`qfield/symbologie.py`) :
   arbres en pastilles de la couleur du groupe d'essence de Marculus (gris
   tant que l'essence n'est pas saisie), contour blanc, taille selon le
   diamètre, étiquette « numéro – diamètre » ; UGF comme les parcelles
   forestières de Marculus (contour épais, fond à 20 %, une couleur par
   forêt, numéro ONF) ; placettes, centres mesurés, cibles et parcelles
   cadastrales comme sur la carte de Groma ; détections en anneau orange ;
   houppiers en contour vert seul, masqués par défaut. Fonds comme Marculus,
   sous toutes les couches : Plan IGN affiché, Ortho IGN et OpenStreetMap
   au choix, cadastre IGN (`PCI vecteur`) masqué. Tuiles WMTS de la
   Géoplateforme lues en XYZ (grille `PM_0_19`, comme la carte de Groma),
   sans clé.
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

* `declinaison_deg` vient de `groma-preparer` : saisie, ou calculée par
  WMM2025 (`groma.declinaison`, `groma.core.Declinaison` en Kotlin, testés
  sur les 100 valeurs officielles de la NOAA, `fixtures/declinaison/`).
  Pour un GeoPackage où elle est vide, Groma la calcule à la volée au point
  de la placette et à la date du jour, pour la capture et pour « Y aller »,
  sans l'écrire dans le GeoPackage. Après 2030, le modèle n'est plus
  valable : la valeur est alors à saisir (un nouveau modèle WMM sort tous
  les cinq ans).
* La flèche de « Y aller » suit la **boussole du téléphone**, ramenée au
  nord géographique par le modèle géomagnétique d'Android
  (`GeomagneticField`) ; elle ne dépend pas de `declinaison_deg`. La
  boussole est perturbée près d'un véhicule, d'une canne métallique ou d'un
  récepteur : s'en écarter d'un mètre pour viser.
