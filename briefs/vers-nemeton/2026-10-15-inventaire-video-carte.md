# Brief vers nemeton : annexe au brief inventaire vidéo — couches de la carte Groma

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemeton/2026-10-15-inventaire-video-carte.md`).
* **Destinataire** : `nemeton`. Complète
  `briefs/vers-nemeton/2026-10-12-inventaire-video.md` (§ 2 et § 3), pas
  encore traité : à intégrer au même chantier.
* **Type de changement** : `feat:` (sans effet tant que
  `creer_projet_inventaire_video()` n'existe pas).

## 1. Contexte

Depuis le brief du 2026-10-12, l'application Groma a une liste des projets
et une carte (placettes, parcours, parcellaire, position RTK, « Y aller »,
« Mesurer le centre »). Le schéma du GeoPackage est passé de 1.0.0 à
**1.2.0** :

* **1.1.0** : table non spatiale `projet` (une ligne), pour la liste des
  projets du téléphone ;
* **1.2.0** : couches `parcelles` et `ugf`, pour la carte.

En attendant `creer_projet_inventaire_video()`, c'est `groma-preparer`
(`core/src/groma/preparer.py`, côté Groma) qui les remplit à partir du
dossier du projet nemetonshiny, **sans rien y écrire**. Le jour où nemeton
crée lui-même le GeoPackage, il doit écrire les mêmes tables pour que la
carte et la liste fonctionnent.

Source unique du schéma, inchangée : `core/src/groma/donnees/inventaire_video.schema.json`.
**Recopier la version 1.2.0** dans `inst/inventaire_video/`.

## 2. Nouvelles tables

Toutes en **lecture seule** dans QField, **sans index spatial** (comme
`placettes` : le téléphone les lit en entier, sans fonction `ST_*`).
Couches vides (créées, sans ligne) quand la source manque : un plan qui ne
vient pas d'un projet nemetonshiny reste valide.

### 2.1 `projet` (non spatiale, une ligne)

| Colonne | Source dans le projet nemetonshiny |
|---|---|
| `projet_id` (requis) | `metadata.json$id`, sinon nom du dossier du projet |
| `projet_nom` (requis) | `metadata.json$name` |
| `description`, `proprietaire` | `metadata.json$description`, `$owner` |
| `communes` | codes INSEE distincts de `data/parcels.gpkg$commune`, libellés de `data/commune.gpkg` (`code`, `nom`) : « Couchey (21200), Brochon (21110) » |
| `surface_zone_ha` | surface SIG des parcelles (EPSG:2154), 2 décimales |
| `surface_cadastre_ha` | somme de `contenance`, 2 décimales |
| `nb_parcelles`, `nb_ug` | `metadata.json$parcels_count`, `$ug_count` |
| `nb_placettes_base`, `nb_placettes_rempl` | placettes `Base` et `Over` écrites |
| `plan_genere_le` | `metadata.json$samples_generated_at` |
| `prepare_le`, `prepare_par` | date d'écriture ; « nemeton <version> » |
| `emprise` | enveloppe des placettes en WKT **EPSG:4326** (`POLYGON ((lon lat, …))`) : le téléphone trie les projets par distance |

`projet_id` est aussi le nom du dossier du projet sur le téléphone et la clé
des paquets envoyés au serveur : il doit rester stable d'une préparation à
l'autre.

### 2.2 `parcelles` (MULTIPOLYGON, EPSG:2154)

Copie de `data/parcels.gpkg` : `id` → `idu` (requis), `commune`,
`section`, `numero`, `contenance` → `contenance_m2`. Rien d'autre.

### 2.3 `ugf` (MULTIPOLYGON, EPSG:2154)

Parcellaire des unités de gestion : ténements de `data/tenements.gpkg`
(géométries rendues valides) **fusionnés par `ug_id`**, `surface_m2` sommée ;
attributs de `data/ugs.json` : `label` → `libelle`, `groupe`,
`onf_foret_nom` → `foret`, `onf_parcelle` → `parcelle_onf`. Lignes triées
dans l'ordre naturel des numéros ONF, sinon du suffixe numérique de
`ug_id` (1, 2, 10 et non 1, 10, 2).

## 3. `centres_mesures` : plusieurs centres par placette

Groma écrit maintenant lui-même dans `centres_mesures` (« Mesurer le
centre » : moyenne des fix RTK fixes, refus sur le GNSS du téléphone,
confirmation en RTK flottant). Règle : **un centre n'est jamais réécrit** ;
une nouvelle mesure ajoute une ligne. Conséquences pour nemeton :

* une placette peut avoir **plusieurs lignes** dans `centres_mesures` ;
  **la plus récente compte** (`fid` le plus grand). `importer_inventaire_video()`
  et `rapprocher_arbres()` (argument `centre`) doivent la choisir ainsi, et
  non prendre la première ;
* `valider_inventaire_video()` ne doit pas signaler ces doublons comme une
  erreur (au plus une information) ;
* aucun changement d'écriture côté GDAL : Groma met lui-même à jour l'index
  R*Tree de la couche (ses déclencheurs appellent `ST_IsEmpty`, absente du
  SQLite d'Android), et le fichier reste un GeoPackage valide.

## 4. Fonctions touchées

| Fonction | Évolution |
|---|---|
| `schema_inventaire_video()` | lit la version 1.2.0 (tables `projet`, `parcelles`, `ugf`) |
| `creer_projet_inventaire_video()` | nouvel argument `project_dir = NULL` (dossier du projet nemetonshiny) ; quand il est fourni, remplit `projet`, `parcelles` et `ugf` comme au § 2, sinon les crée vides. Une erreur de lecture de ces sources n'empêche jamais la création (avertissement) |
| `importer_inventaire_video()`, `rapprocher_arbres()` | centre = dernière ligne de `centres_mesures` pour la placette (§ 3) |
| `valider_inventaire_video()` | ignore `projet`, `parcelles`, `ugf` (lecture seule) ; tolère plusieurs centres par placette |

## 5. Tests (testthat)

* Projet nemetonshiny minimal en fixture (`metadata.json`, `parcels.gpkg`
  de 2 parcelles, `tenements.gpkg` de 3 ténements pour 2 UG, `ugs.json`) :
  `projet` a une ligne avec `emprise` en EPSG:4326 qui contient toutes les
  placettes ; `parcelles` a 2 lignes ; `ugf` a 2 lignes, la surface de
  chacune est la somme de ses ténements.
* Sans `project_dir` : les trois tables existent et sont vides ; le
  GeoPackage est valide.
* Deux centres pour une placette : le rapprochement utilise le plus récent.
* Équivalence avec Groma : l'exemple `exemples/inventaire_video/` du dépôt
  groma_nemeton (schéma 1.2.0) passe `valider_inventaire_video()`.
