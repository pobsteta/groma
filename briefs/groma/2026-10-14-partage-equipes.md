# Brief Groma : plusieurs équipes sur un projet, répartition par le parcours

* **Émetteur** : Pascal, via `groma_nemeton` (copie de référence :
  `briefs/groma/2026-10-14-partage-equipes.md`).
* **Destinataire** : ce dépôt (`groma-preparer`, schéma, application
  Android, serveur). Annexes (§ 9) à transmettre à nemetonshiny et à nemeton
  en briefs séparés.
* **Type** : `feat:` (plusieurs PR, voir § 10).
* **Suite de** : `briefs/groma/2026-10-13-liste-des-projets.md` (projets
  sur le téléphone, `projet_id` dans les paquets).

## 1. Besoin

Plusieurs équipes inventorient **le même projet** en même temps, chacune
avec son téléphone, comme les opérateurs d'un contexte Marculus. Il faut :

1. **répartir les placettes avant le terrain**, en fonction du chemin à
   parcourir : chaque équipe reçoit un tronçon du parcours du voyageur de
   commerce calculé par nemetonshiny, de charge comparable ;
2. que chaque équipe voie **ses placettes dans son ordre de visite**, et
   celles des autres (grisées, avec l'équipe et l'état) ;
3. **échanger les placettes faites** entre téléphones, sans réseau, et
   voir l'avancement de tout le projet ;
4. avec du réseau, voir cet avancement par le serveur, sans échange de
   fichier.

## 2. Ce que fait Marculus, et ce que Groma fait autrement

Marculus (`ListeContextesScreen.kt:152`, `SauvegardeRepository.kt:60-99`,
`MergeDao.kt:37-54`) partage un contexte par un fichier `.marsync` (JSON)
envoyé par le partage Android (`ACTION_SEND` : Quick Share, Bluetooth,
messagerie). Le destinataire le fusionne dans Paramètres → « Fusionner ».
Chaque tige a un UUID et porte l'opérateur (nom saisi ou UUID de
l'appareil). La fusion fait l'union par UUID, et sur une même tige la
dernière modification gagne (horloge du téléphone).

Groma reprend le transport (partage Android, fichier, fusion idempotente,
identité d'opérateur) mais **pas la règle de fusion** :

* **l'unité est la placette**, pas l'arbre. Une placette est faite par
  une seule équipe, d'un bloc (centre RTK, cibles, vidéo, détections). Il
  n'y a pas d'arbre à fusionner entre équipes, donc pas d'arbitrage par
  l'horloge, sensible au décalage entre téléphones ;
* la répartition (§ 3) dit d'avance à qui est chaque placette ; un conflit
  ne vient que d'une placette faite hors de sa répartition (§ 6) ;
* les vidéos ne voyagent pas entre téléphones (centaines de Mo) : elles
  vont au serveur, comme aujourd'hui. Le fichier d'échange ne porte que
  la saisie et les résultats (quelques Ko par placette).

## 3. Répartition par le parcours

### 3.1 Le parcours existe déjà

nemeton ordonne les placettes Base en tournée ouverte
(`nemeton/R/sampling_plan.R`, `.tsp_tour()` : `TSP::solve_TSP`
« nearest_insertion » puis « 2-opt », repli plus proche voisin + 2-opt ;
départ par défaut au point le plus au sud-est, accès routier le plus
probable). Le résultat est la colonne `visit_order` de `samples.gpkg`,
gardée telle quelle par `groma-preparer`. Les placettes Over suivent, dans
l'ordre de priorité du tirage.

Groma **ne recalcule pas** le parcours : il le découpe. Un tronçon contigu
d'une bonne tournée est une bonne tournée, et la répartition reste
cohérente avec la carte « Parcours » de nemetonshiny.

### 3.2 Découpage en tronçons équilibrés

Entrée : les N placettes Base dans l'ordre `visit_order`, k équipes, un
point de départ D (accès, parking), une vitesse de marche v et un temps
par placette t.

* **Coût d'un tronçon** `[i..j]` :
  `approche(D, tronçon) / v + longueur(i..j) / v + (j - i + 1) · t`,
  où `longueur` est la somme des distances entre placettes consécutives
  (en Lambert 93, à vol d'oiseau) et `approche` la distance de D à
  l'extrémité la plus proche du tronçon. Le tronçon est parcouru **depuis
  cette extrémité** (il peut donc être inversé).
* **Objectif** : minimiser le coût du tronçon le plus long (journée de la
  dernière équipe à finir). Programmation dynamique sur les points de
  coupe, O(k·N²) : instantané pour N ≈ 100.
* Chaque équipe reçoit un tronçon ; `ordre_equipe` numérote ses placettes
  dans le sens de marche (1, 2, …).
* Sans `--depart`, D est la première placette du parcours (le départ de
  nemeton).
* Valeurs par défaut, modifiables : v = 2 km/h (forêt, hors chemin),
  t = 40 min par placette (RTK, cibles, vidéo).

### 3.3 Placettes de remplacement (Over)

Une placette Over remplace une placette Base inaccessible, dans l'ordre
de priorité du tirage (GRTS), au sein de la même strate. Si les Over
restaient communes, deux équipes hors réseau pourraient prendre la même.
Elles sont donc **distribuées d'avance** :

* dans chaque strate, les Over sont distribuées dans l'ordre de priorité,
  tour à tour, aux équipes qui ont des placettes Base de cette strate,
  au prorata de ce nombre de placettes ;
* chaque équipe utilise **ses** Over dans l'ordre, quand une de ses
  placettes Base est inaccessible ;
* sans strate, une seule distribution pour tout le plan.

**Point à valider** (statistique) : l'ordre de remplacement n'est plus
l'ordre global du tirage mais l'ordre par équipe. L'écart est faible si
chaque équipe a des Over dans chaque strate qu'elle parcourt ; il est
signalé dans le rapport de `groma-preparer` quand une équipe n'en a pas.

### 3.4 Où se fait la répartition

* **Maintenant** : `groma-preparer` (Python, `groma.repartition`), au
  moment de préparer le projet :

  ```bash
  groma-preparer <projet>/data/samples.gpkg --rayon 15 \
      --equipes "Équipe A,Équipe B,Équipe C" \
      --depart 5.0123,47.2345 --vitesse 2 --temps-placette 40
  ```

  (`--declinaison` est facultatif depuis le modèle WMM2025.)

  Le rapport donne, par équipe : nombre de placettes Base et Over,
  distance, durée estimée.
* **Ensuite** : un bouton « Répartir entre équipes » dans l'onglet
  Échantillonnage de nemetonshiny (annexe A), qui dessine chaque tronçon
  d'une couleur sur la carte « Parcours ». Groma lit alors la répartition
  telle quelle et ne la recalcule pas.
* **Sur le terrain** : une placette peut être **cédée** à une autre équipe
  depuis le téléphone (§ 5), pour rééquilibrer en cours de journée.

## 4. Schéma (version 1.2.0)

* Nouvelle table non spatiale `equipes` : `equipe_id` (texte court,
  `A`, `B`…, requis), `equipe_nom` (requis), `couleur` (`#RRGGBB`),
  `depart_lon`, `depart_lat`, `distance_m`, `duree_min` (estimations du
  § 3.2).
* `placettes` : colonnes `equipe` (référence `equipes.equipe_id`, vide =
  non répartie) et `ordre_equipe` (entier). `observateur`, qui existe,
  reçoit le nom de l'opérateur qui a fait la placette.
* Un projet sans table `equipes`, ou avec une table vide, fonctionne comme
  aujourd'hui : une seule équipe implicite, ordre `visit_order`.
* La source reste `core/src/groma/donnees/inventaire_video.schema.json`
  (règle 3).

## 5. Téléphone

### 5.1 Identité

* Paramètres : **Nom d'opérateur** (comme Marculus ; par défaut un
  identifiant court de l'appareil).
* À la première ouverture d'un projet réparti : « Quelle équipe êtes-
  vous ? » (liste de la table `equipes`). Le choix est gardé par projet et
  modifiable dans le menu du projet.

### 5.2 Liste des placettes

* Filtre en tête : **Mon équipe** (par défaut), **Toutes**.
* « Mon équipe » : ses placettes Base dans l'ordre `ordre_equipe`, puis ses
  Over. La prochaine placette à faire est mise en avant.
* Les placettes des autres équipes sont grisées, avec un badge de la
  couleur de l'équipe et leur état connu (à faire, faite, envoyée). Elles
  s'ouvrent en lecture seule ; filmer une placette d'une autre équipe
  demande une confirmation (§ 6).
* Menu d'une placette à soi, pas encore faite : **Céder à…** (une autre
  équipe). La cession part dans le prochain échange (§ 5.4).

### 5.3 Carte du projet

* L'avancement de la carte (liste des projets) devient celui **de tout le
  projet**, avec une ligne par équipe : `A 12/30 · B 8/29 · C 15/29`.
* `nonEnvoyees` (qui bloque « Retirer ») ne compte que les placettes faites
  **sur ce téléphone**.

### 5.4 Échange sans réseau

* Menu de la carte → **Partager le projet** : un `.zip` du dossier
  préparé (le GeoPackage tel que livré, sans saisie), par le partage
  Android. Le téléphone qui le reçoit l'ouvre avec Groma (intent-filter
  sur le `.zip` partagé, ou ⋮ → « Importer un fichier… »).
* Menu de la carte → **Partager l'avancement** : un fichier
  `<projet_id>-<equipe>-<AAAAMMJJ_HHMM>.gromasync`, envoyé par le partage
  Android. Il contient, pour chaque placette faite ou cédée sur ce
  téléphone :
  * `plot_id`, `equipe`, `etat_traitement`, `observateur`, `date_visite` ;
  * les lignes de `centres_mesures`, `cibles`, `detections`, `arbres` de
    la placette (avec leurs `uuid`) ;
  * les cessions (`plot_id`, de, vers, horodatage, opérateur).

  Format : JSON dans un zip, avec `version`, `projet_id`,
  `schema_version`, l'appareil et l'opérateur émetteurs.
* ⋮ → **Fusionner un avancement** (ou ouverture directe du fichier).
  Règles :
  * le `projet_id` doit être celui d'un projet présent, sinon refus ;
  * une placette **sans donnée terrain locale** prend celles du fichier,
    en bloc ;
  * une placette **avec des données locales** et une **autre origine**
    (appareil différent) : conflit, voir § 6 ;
  * une placette déjà reçue du même appareil est remplacée en bloc par la
    version la plus récente de cet appareil (son propre horodatage, pas
    l'horloge locale) ;
  * idempotent : fusionner deux fois le même fichier ne change rien ;
  * rien n'est jamais effacé (règle 5).
* Les placettes reçues sont en lecture seule et ne partent pas au serveur
  depuis ce téléphone : c'est leur téléphone d'origine qui envoie la
  vidéo.

### 5.5 Avec du réseau

* À l'ouverture du projet et sur « Actualiser », si le serveur répond :
  `GET /placettes?projet_id=…` (existe) renvoie, par placette, l'état et
  désormais l'équipe et l'opérateur. Les placettes des autres équipes
  prennent cet état ; aucune donnée terrain n'est téléchargée.
* Le manifeste des paquets porte `equipe` et `operateur`, rangés par le
  serveur avec la tâche.

## 6. Conflits

Une placette faite par deux téléphones (oubli de répartition, cession
pas encore reçue) :

* les deux saisies sont **gardées** : la locale reste en place, l'autre
  est rangée dans `conflits/<plot_id>-<appareil>.json` de la copie de
  travail ;
* la placette affiche « 2 saisies » ; l'utilisateur choisit celle qui
  compte. Le choix part dans le prochain échange ;
* côté serveur, deux paquets pour le même `(projet_id, plot_id)` sont
  traités tous les deux et marqués en conflit ; le rapprochement par
  nemeton (annexe B) garde les deux et signale.

## 7. Sécurité et vie privée

* Le fichier `.gromasync` contient des positions de placettes et des
  noms d'opérateurs : il circule par les moyens que l'utilisateur choisit,
  rien n'est publié.
* Le zip reçu est lu entrée par entrée ; tout chemin qui n'est pas un nom
  de fichier nu fait refuser le fichier (zip-slip, comme le lot Marculus).

## 8. Hors périmètre

* Synchronisation en direct entre téléphones (Nearby, Wi-Fi Direct).
* Réoptimiser le parcours sur le terrain ou suivre le chemin réel
  (réseau de pistes, pente) : la distance reste à vol d'oiseau.
* Carte des placettes et du parcours dans Groma : brief séparé
  (`briefs/groma/2026-10-14-carte-rtk.md`), qui affiche les tronçons de
  ce brief.

## 9. Annexes (briefs séparés)

**A. nemetonshiny** : dans l'onglet Échantillonnage, « Répartir entre
équipes » (nombre et noms, point de départ sur la carte, vitesse, temps
par placette) ; tronçons colorés sur la carte « Parcours » ; colonnes
`equipe` et `ordre_equipe` écrites dans `samples.gpkg` ; tableau par
équipe (placettes, distance, durée). Algorithme : celui du § 3.2, à
partir de `visit_order`.

**B. nemeton** : `groma_telecharger_resultats()` et le rapprochement
gardent `equipe`, `operateur` et les placettes en conflit (deux saisies,
aucune perdue).

## 10. Découpage proposé

1. `feat(core)` : schéma 1.2.0 (`equipes`, `placettes.equipe`,
   `ordre_equipe`) ; `groma.repartition` (tronçons, Over par strate,
   rapport) ; `groma-preparer --equipes --depart --vitesse
   --temps-placette` ; lecture Kotlin. Tests : découpage optimal sur des
   cas écrits à la main, inversion de tronçon, Over au prorata, projet
   sans équipe.
2. `feat(android)` : identité (opérateur, équipe par projet), filtre « Mon
   équipe », ordre de visite, placettes des autres grisées, avancement par
   équipe sur la carte ; `equipe` et `operateur` dans le manifeste et
   au serveur.
3. `feat(android)` : « Partager le projet », « Partager l'avancement »,
   « Fusionner », cessions, conflits. Fusion dans `groma-core` (Kotlin,
   testée sur JVM).
4. `feat(server)` : `equipe` et `operateur` dans `/placettes`,
   conflits ; le téléphone lit l'état des autres quand il a du réseau.
5. `docs` : `terrain.md` (répartir, partager, fusionner), `PLAN.md` ;
   annexes A et B déposées.

## 11. Critères d'acceptation

* `groma-preparer --equipes "A,B,C"` sur le plan de Couchey (88 Base,
  24 Over) donne trois tronçons contigus du parcours dont les durées
  estimées diffèrent de moins de 15 % ; chaque Over appartient à une
  équipe qui a des Base de sa strate.
* Sur deux téléphones (A et B), chacun voit d'abord ses placettes dans
  son ordre de marche, celles de l'autre grisées.
* A fait P003 et partage son avancement ; après fusion sur B, P003 est
  « faite par A » sur B, en lecture seule, et l'avancement par équipe est
  à jour. Refusionner le même fichier ne change rien.
* A cède P010 à B : après échange, P010 est dans la liste de B.
* A et B font tous deux P020 : après échange, les deux saisies existent
  sur les deux téléphones et rien n'est perdu.
* Un projet préparé sans `--equipes` se comporte comme avant.
