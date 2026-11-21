# Groma — brief

> Rempli avec Pascal le 2026-11-21, critiqué par le `contradicteur` le même jour ; arbitrages de Pascal reportés dans chaque rubrique.

## Ma réponse d'abord (fiche n° 15)

> « j'attends qu'on soit capable d'identifier les arbres, leur essence et leur diamètre à partir d'un centre et renvoyer le résultat sur nemeton »
>
> — Pascal, le 21 novembre 2026, écrit avant de lire la critique du contradicteur.

## Le problème, et pour qui (Abstraction)

- **Problème** : sur une placette d'inventaire, il faut aujourd'hui retrouver chaque arbre, mesurer son diamètre à 1,30 m au compas, le positionner (azimut et distance depuis le centre), le numéroter et noter son essence, à la main, puis ressaisir le tout pour nemeton. C'est lent, fait à plusieurs, et les erreurs de saisie ou d'oubli d'arbre ne se voient qu'au bureau. _(Retenu par Pascal le 2026-11-21.)_
- **Bénéficiaires** : **d'abord** Pascal, opérateur de terrain qui inventorie les placettes (forêts communales de Marsannay-la-Côte et de Couchey, Reconfort à Loury). **Ensuite** les gestionnaires de ces forêts (communes, ONF), qui reçoivent dans nemetonshiny un inventaire plus rapide à obtenir et vérifiable placette par placette. _(Retenu par Pascal le 2026-11-21.)_
- **Coût complet aujourd'hui** : inconnu ; mesuré par un protocole _(arbitrage de Pascal du 2026-11-21 sur la critique I1)_ : sur les placettes du critère de réussite, chronométrer par élément de travail (nomenclature IUFRO, guide COST FP0902) : trajet, relevé de C0, pose et placement de C1 à C3, film, attente du calcul, vérification et compas, correction au bureau, placettes à refaire ; compter les personnes et le temps de GPU ; faire de même avec la méthode classique, tenue par son équipe habituelle. Les 40 min par placette du brief `briefs/groma/2026-10-14-partage-equipes.md` restent une hypothèse jusque-là.

## Ce que fait Groma

- **En une phrase** : à partir d'un centre de placette relevé et d'une vidéo filmée au smartphone, Groma identifie chaque arbre de la placette, mesure son diamètre à 1,30 m, le positionne et le numérote, propose son essence (que l'opérateur confirme ou corrige), et renvoie le résultat à nemeton. _(Retenu par Pascal le 2026-11-21 ; l'essence proposée par Groma devient un engagement, PLAN.md la plaçait « plus tard ».)_
- **Essence proposée** _(arbitrage de Pascal du 2026-11-21 sur la critique B1 du contradicteur)_ : Groma propose le **groupe d'essence BD Forêt** (chênes, hêtre, autres feuillus, résineux…) avec un **niveau de confiance**, pas l'espèce ; à partir d'une **photo d'écorce rapprochée** prise dans la fiche de l'arbre (2048 px), pas des images de la vidéo (640×480). La proposition est écrite dans des colonnes à part (par exemple `essence_proposee`, `essence_confiance`, `essence_source`, à ajouter au schéma source et à annoncer par brief à nemeton), **jamais dans `essence_arbre` sans confirmation** de l'opérateur (règle 5).
- **Les gestes sur le terrain** _(retenus par Pascal le 2026-11-21)_ :
  1. Rejoindre la placette avec la carte de Groma (« Y aller »), planter la canne et **relever C0** (RTK, ou GNSS du téléphone à défaut).
  2. Poser **C1 à C3** sur trois arbres (vers 120°, entre 8 et 15 m) et les **placer** dans Groma : azimut à la boussole de visée, distance au télémètre, diamètre au compas.
  3. **Filmer** : trois tours concentriques (environ 4, 10 et 16 m), départ et arrivée à la canne, en suivant les alertes de l'écran.
  4. **Terminer** : verdict suffisante / à refaire, puis arbres provisoires sur le plan de la placette.
  5. **Vérifier et compléter** dans Groma : confirmer ou corriger l'essence proposée, ajouter un arbre oublié, mesurer au compas un diamètre douteux.
  6. **Envoyer** au serveur quand le réseau le permet.
- **Ce qu'il produit** _(retenu par Pascal le 2026-11-21)_ : pour chaque placette, dans le GeoPackage du projet (schéma `inventaire_video`, source unique dans ce dépôt) :
  - **les arbres** : numéro selon une règle fixe, azimut et distance depuis le centre, position en Lambert-93, diamètre à 1,30 m (vidéo, ou compas quand il a été mesuré), hauteur tirée du houppier LiDAR quand l'arbre y est rattaché (`h_source = mnh`), mesurée quand elle est saisie (`mesuree`), sinon aucune (`aucune` ; fréquent pour les arbres dominés, que le LiDAR aérien rate) _(précisé par Pascal le 2026-11-21, critique I3)_, essence proposée puis confirmée, état **provisoire** (téléphone) puis **définitif** (serveur) ;
  - **le centre relevé et les cibles** ;
  - **un rapport de qualité** (couverture, suivi, orientation).

  Le tout part au serveur avec le paquet de capture ; nemeton le récupère et le rapproche sans perdre aucun attribut saisi sur le terrain.

## Conventions de mesure

_Arbitrage de Pascal du 2026-11-21 sur la critique I4 du contradicteur ; les changements de code deviennent des missions._

- **Diamètre à 1,30 m côté amont** en pente, comme l'IGN et l'ONF (aujourd'hui, le serveur prend le sol au pied de l'arbre : tranche trop basse côté amont sur les pentes de Marsannay). Mission à écrire, avec un cas de test.
- **Arbres limites à confirmer au compas** : Groma signale ceux dont le diamètre estimé est proche du seuil (seuil + 2 à 3 cm) ou qui sont proches du bord de la placette (moins de 0,5 m du rayon) ; valeurs exactes à fixer par Pascal.
- **Tours de film réglés sur le rayon** de la placette, au lieu de 4, 10 et 16 m fixes.
- **Diamètre au compas forestier** (décision de Pascal du 2026-11-21, PR #127), pas au ruban.

## Critère de réussite

_Arbitrage de Pascal du 2026-11-21 sur la critique B2 du contradicteur._

Sur plusieurs placettes relevées aussi à la main (compas, azimut et distance), on mesure :

- la part des arbres retrouvés, par classe de diamètre ;
- l'écart de diamètre avec le compas (biais et écart type) ;
- l'erreur de position ;
- la part de faux positifs.

Nombre de placettes et seuils à atteindre : à trancher par Pascal. Tant qu'ils ne sont pas atteints, Groma **estime** le diamètre ; il ne le « mesure » pas. Les mêmes placettes servent à chronométrer le coût complet.

## Ce qu'il ne fait pas (hors périmètre)

_Retenu par Pascal le 2026-11-21._

- **Pas de plan d'échantillonnage** : le tirage et l'ordre de visite des placettes viennent de nemetonshiny.
- **Pas d'indicateurs forestiers** (volumes, surface terrière, statistiques par strate) : c'est nemeton, qui lit le GeoPackage.
- **Aucune modification de nemeton ni de nemetonshiny** : leurs évolutions passent par des briefs, et les échanges par GeoPackage et GeoTIFF, sans code R importé.
- **Pas d'arbres sous le seuil de diamètre** de la placette (`seuil_diam_cm`), ni de régénération ou de semis.
- **Pas de hauteur mesurée par la vidéo** : la hauteur vient du houppier LiDAR ou d'une saisie, sinon il n'y en a pas.
- **Pas de placettes autres que circulaires** à rayon fixe autour d'un centre ; en particulier pas de placettes concentriques comme l'IGN (6, 9 et 15 m selon la grosseur). Prix assumé : les résultats de Groma ne se comparent pas directement aux placettes IGN _(Pascal, 2026-11-21, critique I4)_.
- **Pas de LiDAR du téléphone** : Groma vise les smartphones ARCore sans LiDAR.

## Contraintes connues

_Retenues par Pascal le 2026-11-21._

- **Hors ligne sur le terrain** : capture et calcul provisoire sur le téléphone en moins de 2 minutes ; l'envoi attend le réseau, et il reprend s'il est interrompu.
- **Matériel** : smartphone Samsung ARCore avec l'API de profondeur, sans LiDAR (S23 Ultra) ; récepteur RTK ZED-F9P facultatif, GNSS du téléphone à défaut ; serveur avec GPU (PC ou machine louée).
- **Plateforme Néméton** : une seule source pour le schéma du GeoPackage, dans ce dépôt ; numérotation identique en Python, Kotlin et R ; rapprochement sans jamais perdre un attribut saisi sur le terrain.
- **Dépendance à nemeton** : le bénéfice pour les gestionnaires passe par nemeton, qui doit traiter les briefs en attente (voir PLAN.md) ; ce n'est pas acquis.
- **Licence** GPL-3.0-or-later ; toute dépendance non commerciale ou copyleft est signalée avant d'être ajoutée.
- **Unités et conventions** : Lambert-93, mètres, diamètres en cm, azimuts en degrés ou en grades selon la placette ; noms de champs en français, sans accent, 30 caractères au plus.
- **Terrain** : utilisable au soleil, avec des gants ; **une placette est faite par une seule personne** _(précisé par Pascal le 2026-11-21, critique I2)_.
- **Sécurité (travail isolé)** : quelqu'un sait où travaille l'opérateur et quand il doit revenir ; la conduite à tenir sans réseau est prévue. Groma n'a pas à le faire lui-même, mais c'est une condition du travail seul en forêt (INRS, travail isolé) _(ajouté par Pascal le 2026-11-21, critique I2)_.

## Piste envisagée

- **Plusieurs équipes sur une forêt** : découper la tournée entre équipes et échanger entre téléphones, comme le font Marculus et Surculus (Pascal, 2026-11-21 : « on pourra envisager un échange comme le fait marculus ou surculus pour découper la tournée »). Le brief `briefs/groma/2026-10-14-partage-equipes.md` le décrit déjà ; il n'est pas réalisé. Cela ne remplace pas « une placette, une personne » : c'est un autre niveau (la forêt, pas la placette). Quand : à trancher par Pascal.
