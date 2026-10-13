# Brief Groma : liste des projets, comme les contextes de Marculus

* **Émetteur** : Pascal, via `groma_nemeton` (copie de référence :
  `briefs/groma/2026-10-13-liste-des-projets.md`).
* **Destinataire** : ce dépôt (application Android, `groma-preparer`,
  schéma). Une annexe (§ 7) est à transmettre à nemeton en brief séparé.
* **Type** : `feat:` (plusieurs PR, voir § 8).

## 1. Besoin

Un opérateur doit pouvoir porter **plusieurs plans d'échantillonnage** sur
le téléphone : plusieurs forêts, ou plusieurs campagnes. Aujourd'hui, Groma
n'ouvre qu'un projet à la fois (« Changer de projet », choix d'un dossier).

L'écran d'accueil devient la **liste des projets**, sur le modèle de la
liste des contextes de Marculus (`ListeContextesScreen.kt`). Chaque carte
montre la forêt, la surface, l'avancement. Un appui sur la carte ouvre la
liste des placettes de ce projet.

Un projet est un **dossier présent sur le téléphone** qui porte le nom du
projet nemetonshiny d'où vient le plan, c'est-à-dire son identifiant
(`20261008_212542_uywc`, le nom du dossier dans
`~/.local/share/nemeton/projects/`). Il contient `inventaire_video.gpkg` et,
le cas échéant, `rasters/`.

```
Téléchargements/groma/
├── 20261008_212542_uywc/
│   └── inventaire_video.gpkg
└── 20261007_084614_qapz/
    ├── inventaire_video.gpkg
    └── rasters/
```

On nomme le dossier d'après l'identifiant, pas d'après le nom affiché. Deux
projets peuvent porter le même nom (« Forêt communale de … »), et un nom
avec accents, parenthèses ou barres obliques fait un mauvais nom de
dossier. Le nom lisible vient des métadonnées (§ 3).

## 2. Découverte des projets

* **Dossier racine** : par défaut `Téléchargements/groma/`. Il est choisi
  une fois avec `ACTION_OPEN_DOCUMENT_TREE`, avec une permission persistante
  (`takePersistableUriPermission`). On peut le changer dans Paramètres
  (« Dossier des projets »). Pas de `MANAGE_EXTERNAL_STORAGE`.
* **Projet reconnu** : un sous-dossier direct de la racine qui contient
  `inventaire_video.gpkg`. Les autres sous-dossiers sont ignorés sans
  bruit.
* **Copie de travail** : SQLite exige un vrai chemin de fichier, ce que
  l'accès par l'arbre de documents ne donne pas. Le mécanisme actuel
  (`Projet.importer`) est donc conservé. À la première ouverture, le dossier
  est copié dans `getExternalFilesDir()/projets/<projet_id>/`, et la saisie
  terrain se fait dans cette copie.
* **La liste fusionne** les copies de travail et les dossiers de la racine,
  par `projet_id`. Un projet supprimé du dossier Téléchargements reste dans
  la liste tant que sa copie existe.
* **Mise à jour depuis le dossier** : si le GeoPackage du dossier est plus
  récent que la copie de travail (`prepare_le`, § 3), la carte le signale
  (« nouvelle version disponible »).
  * Le remplacement se fait seulement sur demande. Il est **refusé** si la
    copie contient des données terrain : des centres mesurés, des cibles,
    des arbres, ou un `etat_traitement` autre que `a_faire`.
  * Dans ce cas, un message demande d'envoyer d'abord les placettes faites.
    C'est l'esprit de la règle 5 : on ne perd jamais une saisie de terrain.
* **Actualisation** au retour à l'écran (`onResume`) et par le menu ⋮. Pas
  de scrutation.
* « Importer un dossier… » (le choix d'un dossier hors racine, comme
  aujourd'hui) reste disponible dans le menu ⋮ pour un dépannage.

## 3. Métadonnées du projet : table `projet` dans le GeoPackage

Nouvelle table non spatiale `projet`, d'une ligne, déclarée dans
`inventaire_video.schema.json`, qui reste la source unique (règle 3). Elle
est écrite par `groma-preparer` et, plus tard, par
`creer_projet_inventaire_video()` (§ 7).

| Champ | Type | Exemple (Couchey) | Source |
|---|---|---|---|
| `projet_id` | texte, obligatoire | `20261008_212542_uywc` | `metadata.json` → `id`, sinon nom du dossier de sortie |
| `projet_nom` | texte, obligatoire | Forêt communale de Couchey (ONF) | `metadata.json` → `name`, sinon `--nom` |
| `description` | texte | 19 parcelles publiques… | `metadata.json` → `description` |
| `proprietaire` | texte | | `metadata.json` → `owner` |
| `communes` | texte | Couchey (21200) | `parcels.gpkg` → `commune` (codes INSEE), libellés via `commune.gpkg` |
| `surface_zone_ha` | réel | 493,8 | surface SIG des parcelles du projet (`parcels.gpkg`, en Lambert 93) |
| `surface_cadastre_ha` | réel | 498,8 | Σ `contenance` / 10 000 |
| `nb_parcelles` | entier | 19 | `metadata.json` → `parcels_count` |
| `nb_ug` | entier | 63 | `metadata.json` → `ug_count` |
| `nb_placettes_base` | entier | 88 | plan, `type = 'Base'` |
| `nb_placettes_rempl` | entier | 24 | plan, `type = 'Over'` |
| `plan_genere_le` | date-heure | 2026-10-12 20:05:07 | `metadata.json` → `samples_generated_at` |
| `prepare_le` | date-heure | 2026-10-13 … | horloge de l'outil de préparation |
| `prepare_par` | texte | `groma-preparer 0.1.0` | nom et version de l'outil |
| `emprise` | texte (WKT, EPSG:4326) | `POLYGON((…))` | enveloppe des placettes, pour le tri par distance (§ 4) |

Les noms suivent la convention NMT : snake_case, sans accent, 30 caractères
au plus. Les réglages par placette (rayon, seuil, déclinaison, unité)
restent dans `placettes` : ils peuvent varier d'une placette à l'autre.

**Projet sans table `projet`** (préparé avant ce brief) : la carte affiche
le nom du dossier et calcule les nombres à partir de `placettes`. Les champs
manquants sont simplement omis.

## 4. Carte d'un projet

Même composition que `CarteContexte` de Marculus : `ElevatedCard`
cliquable, colonne de textes et menu ⋮ à droite.

```
┌───────────────────────────────────────────────┐
│ Forêt communale de Couchey (ONF)            ⋮ │  titleLarge
│ 19 parcelles publiques couvertes à 50 % …     │  bodySmall, 1 ligne
│ Couchey · 493,8 ha · 63 UG                    │  bodyMedium
│ 112 placettes (88 + 24 rempl.) · r = 15 m     │  bodyMedium
│ ▓▓▓▓▓▓▓░░░░░░░░░░░░  37 faites · 21 envoyées  │  LinearProgressIndicator
│ 1,26 ha inventoriés (0,26 %) · 12/10/2026     │  bodySmall
└───────────────────────────────────────────────┘
```

**Valeurs calculées sur le téléphone**, sans les stocker :

* `faites` : les placettes dont `etat_traitement` est autre que `a_faire`.
  `envoyées` : les placettes envoyées au serveur ou traitées.
* **Surface inventoriée** : Σ π·r² sur les placettes faites, avec le rayon
  propre à chaque placette, en hectares. Le pourcentage est rapporté à
  `surface_zone_ha`.
* La barre de progression compte les placettes Base faites sur
  `nb_placettes_base`, car les placettes de remplacement ne se font qu'en
  cas de besoin.
* La date affichée est celle du dernier relevé (dernier centre mesuré),
  sinon celle de `plan_genere_le`.

**« Surface parcourue »**, à confirmer :

* Ce brief l'affiche de deux façons : la surface de la zone échantillonnée
  (`surface_zone_ha`) et la surface réellement inventoriée.
* Si tu entendais autre chose, par exemple la surface des seules UG
  forestières, ou le linéaire de cheminement entre placettes, le champ est
  à ajouter à la table `projet`.

**Menu ⋮ de la carte** :

* Ouvrir
* Détails : toutes les métadonnées et les réglages, avec le rayon et la
  déclinaison min/max.
* Mettre à jour depuis le dossier : active seulement si une version plus
  récente existe (§ 2).
* Retirer du téléphone : supprime la copie de travail, après confirmation.
  C'est refusé tant qu'une placette faite n'est pas envoyée.

## 5. Écran « Projets »

* `BandeauCompact` titré « Groma ». Menu ⋮ de l'application :
  * Actualiser
  * Importer un dossier…
  * Paramètres
  * À propos
* Champ de recherche, comme dans Marculus. Il filtre sur le nom, la
  description, les communes et `projet_id`, sans tenir compte des accents
  ni de la casse.
* Puce « Terminés », à la manière de « Martelés » dans Marculus.
  * Par défaut, la liste montre les projets en cours : au moins une
    placette Base reste à faire.
  * La puce bascule vers les projets terminés.
  * La recherche signale ce qu'elle trouve dans l'autre liste.
* **Tri** : le dernier projet ouvert d'abord, puis les autres par distance à
  la position du téléphone (centre de `emprise`), s'il y a un fix GPS.
  Sinon, tri par nom.
* **Pas de FAB « Nouveau »** : un projet ne se crée pas sur le téléphone,
  il vient de nemetonshiny.
* **Liste vide** : un texte explique où déposer les dossiers
  (`Téléchargements/groma/<identifiant du projet nemetonshiny>/`), avec un
  bouton « Choisir le dossier des projets ».
* **Navigation** : Projets → Placettes du projet → Capture. Le retour
  remonte d'un niveau. `EcranPlacettes` affiche le nom du projet en titre de
  bandeau. Il perd l'entrée de menu « Changer de projet », que remplace le
  retour.
* Au lancement, l'application ouvre la liste. On ne rouvre pas
  automatiquement le dernier projet : l'opérateur doit voir dans quelle
  forêt il saisit.

## 6. Conséquences techniques à ne pas oublier

* **Collision des `plot_id`** : deux plans peuvent avoir une placette `P001`.
  Aujourd'hui, plusieurs éléments sont indexés par le seul `plot_id` :
  * le travail WorkManager `envoi_$plotId` ;
  * le JSON des détections (`detections/$plotId.json`, `ServeurLocal`) ;
  * le dossier du paquet.
  Tous doivent être indexés par `projet_id` + `plot_id`. Le paquet
  (`manifeste.json`) et la création d'envoi côté serveur portent
  `projet_id`. Le serveur range les résultats par projet. C'est une
  évolution compatible : un `projet_id` absent vaut « projet par défaut ».
* `Preferences` : `projet` (un chemin) devient `dernier_projet` (un
  `projet_id`). Il sert au tri seulement.
* La lecture des métadonnées d'un projet ouvre le GeoPackage en lecture
  seule. Elle est faite hors du fil principal, avec mise en cache par date
  de modification du fichier, pour que la liste reste fluide avec 20 projets.
* Les tests vont dans `groma-core` (JVM) :
  * lecture de la table `projet`, et repli sans elle ;
  * calcul de la surface inventoriée avec des rayons différents ;
  * décision « remplacement autorisé ou refusé » sur une copie avec et sans
    données terrain.

## 7. Annexe : `groma-preparer`, puis nemeton

**`groma-preparer`** (ce dépôt, tout de suite) :

* Si le plan est `<projets>/<id>/data/samples.gpkg`, l'outil lit
  `<projets>/<id>/metadata.json` et `data/parcels.gpkg`, en lecture seule,
  pour remplir la table `projet`.
* Sinon (export QField), `--nom` est obligatoire et les surfaces sont
  omises.
* `--sortie` devient facultatif. Par défaut, il vaut `./<projet_id>/`, pour
  que le dossier à copier porte déjà le bon nom.
* Nouvelle option `--copier-telephone` : un `adb push` vers
  `/sdcard/Download/groma/<projet_id>/`, avec échec explicite si aucun
  appareil n'est branché. Option de confort, non obligatoire.
* Lire `metadata.json` crée une dépendance au format interne de
  nemetonshiny. Elle est tolérée parce qu'elle est en lecture seule et
  qu'elle a un repli (règle 2 : pas d'import de code). Le chemin définitif
  est le suivant.

**Brief vers nemeton** (à rédiger séparément, en complément de
`2026-10-12-inventaire-video.md`) :

* `creer_projet_inventaire_video()` écrit la table `projet` selon le schéma
  ci-dessus.
* Elle nomme le dossier de sortie d'après l'identifiant du projet.

## 8. Découpage proposé

1. `feat(core)` : la table `projet` dans le schéma, `creer_gpkg`, et
   `groma-preparer` (métadonnées, `--sortie` par défaut, `--nom`). Tests
   Python et Kotlin de lecture.
2. `feat(android)` : `projet_id` dans les paquets, les détections et les
   travaux d'envoi. Côté serveur, rangement par projet.
3. `feat(android)` : écran Projets, cartes, découverte dans la racine, copie
   de travail et mise à jour protégée.
4. `docs` : `terrain.md` (déposer les dossiers, mettre à jour un projet),
   `PLAN.md`.

## 9. Critères d'acceptation

* Deux dossiers `Téléchargements/groma/<id>/` copiés par `adb push`
  apparaissent tous deux dans la liste après « Actualiser ». Chaque carte
  porte son nom de forêt, sa surface et son nombre de placettes.
* Une placette `P001` faite dans le projet A ne change ni l'état, ni les
  détections, ni l'envoi de la placette `P001` du projet B.
* Remplacer un projet qui contient une placette faite est refusé, avec un
  message. Sans donnée terrain, le remplacement réussit.
* Un GeoPackage préparé avant ce brief, sans table `projet`, s'affiche avec
  le nom de son dossier, sans plantage.
* Dans la carte, le rayon, le nombre de placettes et la surface
  inventoriée correspondent à la saisie.
