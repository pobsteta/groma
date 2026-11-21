# Groma — brief

> À remplir par Pascal ; critiqué par le `contradicteur` avec `/imperatrice:brief`.

## Ma réponse d'abord (fiche n° 15)

> « j'attends qu'on soit capable d'identifier les arbres, leur essence et leur diamètre à partir d'un centre et renvoyer le résultat sur nemeton »
>
> — Pascal, le 21 novembre 2026, écrit avant de lire la critique du contradicteur.

## Le problème, et pour qui (Abstraction)

- **Problème** : sur une placette d'inventaire, il faut aujourd'hui retrouver chaque arbre, mesurer son diamètre à 1,30 m au compas, le positionner (azimut et distance depuis le centre), le numéroter et noter son essence, à la main, puis ressaisir le tout pour nemeton. C'est lent, fait à plusieurs, et les erreurs de saisie ou d'oubli d'arbre ne se voient qu'au bureau. _(Retenu par Pascal le 2026-11-21.)_
- **Bénéficiaires** : **d'abord** Pascal, opérateur de terrain qui inventorie les placettes (forêts communales de Marsannay-la-Côte et de Couchey, Reconfort à Loury). **Ensuite** les gestionnaires de ces forêts (communes, ONF), qui reçoivent dans nemetonshiny un inventaire plus rapide à obtenir et vérifiable placette par placette. _(Retenu par Pascal le 2026-11-21.)_
- **Coût complet aujourd'hui** : inconnu (temps par placette à la main, nombre d'opérateurs et coût de ressaisie non mesurés). À renseigner quand une placette aura été faite des deux façons. _(Retenu par Pascal le 2026-11-21.)_

## Ce que fait Groma

- **En une phrase** : à partir d'un centre de placette relevé et d'une vidéo filmée au smartphone, Groma identifie chaque arbre de la placette, mesure son diamètre à 1,30 m, le positionne et le numérote, propose son essence (que l'opérateur confirme ou corrige), et renvoie le résultat à nemeton. _(Retenu par Pascal le 2026-11-21 ; l'essence proposée par Groma devient un engagement, PLAN.md la plaçait « plus tard ».)_
- **Les gestes sur le terrain** _(retenus par Pascal le 2026-11-21)_ :
  1. Rejoindre la placette avec la carte de Groma (« Y aller »), planter la canne et **relever C0** (RTK, ou GNSS du téléphone à défaut).
  2. Poser **C1 à C3** sur trois arbres (vers 120°, entre 8 et 15 m) et les **placer** dans Groma : azimut à la boussole de visée, distance au télémètre, diamètre au compas.
  3. **Filmer** : trois tours concentriques (environ 4, 10 et 16 m), départ et arrivée à la canne, en suivant les alertes de l'écran.
  4. **Terminer** : verdict suffisante / à refaire, puis arbres provisoires sur le plan de la placette.
  5. **Vérifier et compléter** dans Groma : confirmer ou corriger l'essence proposée, ajouter un arbre oublié, mesurer au compas un diamètre douteux.
  6. **Envoyer** au serveur quand le réseau le permet.
- **Ce qu'il produit** _(retenu par Pascal le 2026-11-21)_ : pour chaque placette, dans le GeoPackage du projet (schéma `inventaire_video`, source unique dans ce dépôt) :
  - **les arbres** : numéro selon une règle fixe, azimut et distance depuis le centre, position en Lambert-93, diamètre à 1,30 m (vidéo, ou compas quand il a été mesuré), hauteur tirée des houppiers LiDAR, essence proposée puis confirmée, état **provisoire** (téléphone) puis **définitif** (serveur) ;
  - **le centre relevé et les cibles** ;
  - **un rapport de qualité** (couverture, suivi, orientation).

  Le tout part au serveur avec le paquet de capture ; nemeton le récupère et le rapproche sans perdre aucun attribut saisi sur le terrain.

## Ce qu'il ne fait pas (hors périmètre)

_Retenu par Pascal le 2026-11-21._

- **Pas de plan d'échantillonnage** : le tirage et l'ordre de visite des placettes viennent de nemetonshiny.
- **Pas d'indicateurs forestiers** (volumes, surface terrière, statistiques par strate) : c'est nemeton, qui lit le GeoPackage.
- **Aucune modification de nemeton ni de nemetonshiny** : leurs évolutions passent par des briefs, et les échanges par GeoPackage et GeoTIFF, sans code R importé.
- **Pas d'arbres sous le seuil de diamètre** de la placette (`seuil_diam_cm`), ni de régénération ou de semis.
- **Pas de hauteur mesurée par la vidéo** : la hauteur vient des houppiers LiDAR.
- **Pas de placettes autres que circulaires** à rayon fixe autour d'un centre.
- **Pas de LiDAR du téléphone** : Groma vise les smartphones ARCore sans LiDAR.

## Contraintes connues

_Retenues par Pascal le 2026-11-21._

- **Hors ligne sur le terrain** : capture et calcul provisoire sur le téléphone en moins de 2 minutes ; l'envoi attend le réseau, et il reprend s'il est interrompu.
- **Matériel** : smartphone Samsung ARCore avec l'API de profondeur, sans LiDAR (S23 Ultra) ; récepteur RTK ZED-F9P facultatif, GNSS du téléphone à défaut ; serveur avec GPU (PC ou machine louée).
- **Plateforme Néméton** : une seule source pour le schéma du GeoPackage, dans ce dépôt ; numérotation identique en Python, Kotlin et R ; rapprochement sans jamais perdre un attribut saisi sur le terrain.
- **Licence** GPL-3.0-or-later ; toute dépendance non commerciale ou copyleft est signalée avant d'être ajoutée.
- **Unités et conventions** : Lambert-93, mètres, diamètres en cm, azimuts en degrés ou en grades selon la placette ; noms de champs en français, sans accent, 30 caractères au plus.
- **Terrain** : utilisable au soleil, avec des gants, par un seul opérateur.

## Piste envisagée

- **Plusieurs opérateurs sur une forêt** : découper la tournée entre équipes et échanger entre téléphones, comme le font Marculus et Surculus (Pascal, 2026-11-21 : « on pourra envisager un échange comme le fait marculus ou surculus pour découper la tournée » ; voir aussi le brief `briefs/groma/2026-10-14-partage-equipes.md`). À trancher par Pascal : quand, et si cela remplace « un seul opérateur ».
