# Brief vers nemeton : annexe au brief inventaire vidéo — plugin QField et déclinaison

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemeton/2026-10-16-inventaire-video-plugin-qfield.md`).
* **Destinataire** : `nemeton`. Complète
  `briefs/vers-nemeton/2026-10-12-inventaire-video.md` (§ 2.5 et § 3) et
  l'annexe du 2026-10-15 (couches de la carte). Aucun des deux n'est encore
  traité : à intégrer au même chantier.
* **Type de changement** : `feat:` (sans effet tant que
  `creer_projet_inventaire_video()` n'existe pas).

## 1. Contexte

Le brief du 2026-10-12 réservait, au § 2.5, un **plugin de projet**
`<projet>.qml` à côté du `.qgs`, « dont le brief donnera le contenu une fois
validé sur appareil ». C'est fait.

Depuis Android 11, QField travaille sur **sa** copie du projet, dans son
dossier privé, que Groma ne peut pas ouvrir. Groma sert donc ce qu'il
produit sur `http://127.0.0.1:8765/inventaire/<projet_id>` :

* les détections provisoires de toutes les placettes ;
* le dernier centre mesuré au RTK de chaque placette.

Le plugin, lancé par un bouton dans QField, ajoute **par QField lui-même**
ce qui manque dans `detections` et `centres_mesures`. Les déclencheurs du
GeoPackage (`declencheurs.sql`) créent alors les lignes `arbres`, comme pour
une écriture directe. Rien de ce qui existe déjà n'est modifié : les
détections sont reconnues par `uuid`, les centres par `plot_id` +
`date_mesure`.

**Essayé le 2026-10-16** sur un Galaxy S23 Ultra avec QField 4.3.5 : les
détections sont bien ajoutées. API vérifiées dans le source de QField, de la
3.7 à la 4.3.

## 2. Ce que `creer_projet_inventaire_video()` doit livrer

1. **Le fichier du plugin.** Copie conforme de
   `inst/inventaire_video/inventaire_video.qml`. La copie de référence est
   `qfield/inventaire_video.qml` du dépôt groma_nemeton : la recopier telle
   quelle, **sans la modifier** (Groma en est propriétaire et la fera
   évoluer par brief).
2. **Son nom et son emplacement.** Écrit **à côté** du projet, sous le nom
   `<project_name>.qml` (par défaut `inventaire_video.qml` à côté de
   `inventaire_video.qgz`). QField cherche ce fichier sur le disque : il
   **ne doit pas** être placé *dans* le `.qgz`. Dans un `.zip` livré au
   téléphone, il se met à côté du `.qgz`. QFieldSync et QFieldCloud
   l'empaquettent d'eux-mêmes.
3. **Les noms des couches.** Le plugin trouve les couches par leur nom dans
   le projet. Ces noms doivent être **exactement** `projet`, `detections`,
   `centres_mesures` et `arbres`. La couche non spatiale `projet` (annexe du
   2026-10-15, § 2.1) doit être chargée dans le projet : le plugin y lit
   `projet_id`.
4. **L'ajout d'entités autorisé.** `detections` reste en lecture seule
   **dans les formulaires** : tous les champs `ReadOnly`, comme au § 2.5.
   Mais la couche elle-même ne doit pas être en lecture seule, ni dans
   « Propriétés du projet → Sources de données », ni par l'option QField
   « verrouiller l'ajout d'entités ». Sinon le plugin ne peut pas écrire.
   Même règle pour `centres_mesures`.
5. **Des chemins relatifs**, déjà prévus : QField importe le dossier
   ailleurs.

Au premier chargement, QField demande l'accord de l'utilisateur, avec le
titre du projet. Donner au projet un titre parlant, par exemple « Inventaire
vidéo — <nom du projet nemetonshiny> ».

## 2 bis. Symbologie

Pour que l'opérateur retrouve dans QField ce qu'il voit dans Marculus et
dans Groma, reprendre la symbologie de `qfield/symbologie.py` (dépôt
groma_nemeton). Elle **remplace** la « symbologie catégorisée de `arbres`
selon `etat_detection` » du § 2.5 :

* `arbres` : catégorisé sur `essence_arbre`, pastille pleine de la couleur du
  groupe d'essence de Marculus. Chênes `#2a7fa6`, hêtre `#5061b8`, autres
  feuillus `#7e8e9c`, sapin `#94304d`, épicéa `#c2502f`, autres résineux
  `#ae9381`. Gris `#888888` sans essence. Contour blanc de 0,5 mm ; taille
  `scale_linear(coalesce(dbh_cm, 7.5), 7.5, 80, 2.4, 6.4)` mm ; étiquette
  `"tree_id" || ' – ' || format_number("dbh_cm", 0)` au-dessous du 1/1000 ;
* `ugf` : catégorisé sur `foret`, palette « foncier » de Marculus
  (`#1e88e5`, `#43a047`, `#f4511e`, `#8e24aa`, `#fdd835`, `#00acc1`,
  `#6d4c41`, `#ec407a`). Contour de 0,8 mm, fond de la même couleur à 20 %,
  étiquette `parcelle_onf` ;
* `placettes` : catégorisé sur `etat_traitement`. À faire blanc, provisoire
  `#f57c00`, envoyée `#1976d2`, définitive `#2e7d32` ; contour `#212121` ;
  3,5 mm, 2,4 mm pour une placette de remplacement ;
* `centres_mesures` : croix `#d500f9` ; `cibles` : carré `#ff6f00` ;
  `detections` : anneau `#f57c00` sans remplissage ; `parcelles` : contour
  `#ffd54f` fin, étiquette `section numero`.

## 3. Déclinaison magnétique

Le § 3 du brief du 2026-10-12 disait de laisser `declinaison_deg` à saisir,
faute de modèle géomagnétique embarqué. Groma embarque maintenant le
**WMM2025** (NOAA, domaine public, valable de 2025 à 2030), en Python et en
Kotlin :

* `declinaison_deg` peut rester **vide** dans le GeoPackage : Groma la
  calcule au point de la placette pour la capture et le guidage ;
* une valeur saisie reste **prioritaire** ;
* nemeton n'a donc **pas** à embarquer de modèle. S'il veut pré-remplir la
  colonne, la valeur doit venir du WMM2025, au point de la placette et à la
  date de préparation, arrondie au centième.

`creer_projet_inventaire_video(declinaison_deg = NULL)` laisse donc la
colonne vide sans erreur. Ne pas la mettre à 0.

## 4. Tests (testthat)

* Après `creer_projet_inventaire_video()` :
  * `<project_name>.qml` existe à côté du `.qgz` ;
  * il est identique octet pour octet à `inst/inventaire_video/inventaire_video.qml` ;
  * il n'est pas dans l'archive `.qgz`.
* Le `.qgs` du projet contient des couches nommées `projet`, `detections`,
  `centres_mesures` et `arbres`.
* Aucune de ces couches n'est marquée en lecture seule au niveau du projet.
* Sans `declinaison_deg`, la colonne est vide (`NA`), pas 0.
