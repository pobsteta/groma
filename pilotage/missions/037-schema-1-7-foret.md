# Mission 037 — Schéma 1.7.0 : relief CVAT, dalles MNT et houppiers sur toute la forêt

> Une mission = une phrase = une branche = une PR. Côté Groma du brief
> `briefs/vers-nemeton/2026-11-20-relief-cvat-houppiers-foret.md` (déposé le 2026-11-20).

## Mission

**En une phrase (quoi, et pourquoi) :** passer le schéma du GeoPackage à 1.7.0, avec les
dalles MNT de 1 km (pas de MNH, décision de Pascal du 2026-11-20), le relief `rasters/cvat.tif` et les houppiers de toute la forêt
munis de `x_sommet` et `y_sommet`, avec l'écriture (`creer_gpkg`) et la vérification d'un
dossier de projet en Python, parce
qu'une placette déplacée sur le terrain (P067 filmée à 152 m de son centre prévu, le
2026-11-20) n'avait plus ni MNT ni houppiers et que les missions 038 à 040 lisent ce format.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui : le § 2 du brief fixe chemins, formats et colonnes | |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui : pytest, `exemples/inventaire_video`, `rasterio` ; la mission testeur 037a écrit les tests d'acceptation | confier, après 037a |

**Temps de la main :** prendre — c'est le modèle de données (règle 3 du dépôt : une seule
source du schéma), il engage nemeton et les missions 038 à 040.

**Degré de la main :** le chef tranche (formats et chemins déjà fixés par le brief validé
par Pascal le 2026-11-20).

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev` ; tests d'acceptation par `testeur` (mission 037a, sans lire le code).
- **Modèle :** opus (modèle de données partagé avec nemeton).
- **Ce qui ne lui est pas demandé :** rien sur le téléphone (transport : 038 ; paquet :
  039 ; carte : 040) ; ne pas calculer de CVAT (il vient de nemetonshiny) ; ne pas toucher
  à nemeton ni à nemetonshiny (règle 1).

## Ce qui est attendu

1. **Schéma** `core/src/groma/donnees/inventaire_video.schema.json`, version **1.7.0** :
   * `rasters` : garder `format`, `resolution_m`, `marge_m` et `chemins.mnt` / `chemins.mnh`
     (rasters par placette, inchangés) ; ajouter `dalles` (`mnt`:
     `rasters/mnt_{x}_{y}.tif`, **à plat dans `rasters/`** car le téléphone n'extrait du
     `.qgz` que `rasters/<nom>.tif` ; pas de dalles MNH ; `cote_m`: 1000, coin sud-ouest en km Lambert-93 sur quatre chiffres, même format que
     `rasters.format`, `marge_foret_m`: 50) et `cvat` (`chemin`: `rasters/cvat.tif`,
     format Byte, une bande, 2 m, EPSG:2154, DEFLATE, 0 = nodata, valeurs
     `1 + round(v * 254)`). Texte des remarques repris du brief, § 2.
   * `houppiers` : remarque « toute l'emprise de la forêt » ; `plot_id` reste facultatif
     (NULL hors placette) ; colonnes `x_sommet` et `y_sommet` (REAL, Lambert-93, m),
     marquées requises pour un projet 1.7.0 ; index B-tree sur (`x_sommet`, `y_sommet`)
     déclaré dans le schéma et créé par `creer_gpkg`.
2. **`groma.gpkg.creer_gpkg`** : écrit les deux colonnes et l'index ; accepte des
   houppiers sans `plot_id`. Un projet 1.6.0 (sans ces colonnes) reste lisible par
   `groma` et le serveur.
3. **Vérification d'un dossier de projet** (`groma.dalles.verifier_projet`) : les dalles
   et `cvat.tif` présents sont au format du schéma (Float32 / Byte, EPSG:2154, taille et
   alignement des dalles, nom = coin sud-ouest) ; renvoie la liste des écarts, en
   français. Un dossier sans dalles ni CVAT est conforme (projet 1.6.0). Elle sert aux
   tests et à recevoir le premier projet 1.7.0 de nemeton. `groma-preparer` ne change
   pas (il n'écrit pas de rasters : c'est nemeton qui les écrit).
4. **Fonction de nommage** `groma.dalles` (Python) : dalle contenant un point, dalles
   couvrant un rectangle, nom d'une dalle ; c'est la référence des tests Kotlin de 039.
5. **Exemple** `exemples/inventaire_video` régénéré (houppiers avec sommets, deux dalles
   MNT de test, un petit `cvat.tif`) ; garder l'exemple petit (moins de 2 Mo ajoutés).
6. **Docs** : `docs/` (section du GeoPackage et des rasters), `PLAN.md`, une ligne de
   journal. Mettre à jour `core/tests/test_schema_qualite_reception.py`, qui fige
   aujourd'hui `version == "1.6.0"` (passer à « au moins 1.6.0 »).

## Harnais

- **Peut (périmètre) :** `core/src/groma/donnees/`, `core/src/groma/gpkg.py`,
  `core/src/groma/dalles.py` (nouveau), `core/tests/`,
  `exemples/inventaire_video/`, `docs/`, `PLAN.md`, `pilotage/journal.md` (ajout seul),
  cette fiche (rubrique « Redite » et compte rendu).
- **Câblé (ne peut pas) :** pas de commit sur `main` ; pas de « terminé » sans tests verts
  (hook `tests_obligatoires`) ; journal en ajout seul.
- **Ne doit pas (écrit) :** toucher à `android/`, `server/` (sauf si un test serveur casse
  à cause du schéma : le signaler d'abord) ; modifier nemeton ou nemetonshiny ; changer un
  numéro de version de paquet à la main ; inventer un chemin ou un format absent du brief.
- **Sources autorisées :** le brief du 2026-11-20 ; le schéma actuel ; `core/src/groma/`
  et ses tests ; `briefs/vers-nemeton/2026-10-16-inventaire-video-fonds-houppiers.md` ;
  en lecture seule `nemeton/R/inventaire_video.R` et `nemeton/R/houppiers.R` (pour les
  noms existants). Fermées : tout le reste de nemeton et nemetonshiny.

## Journal

- **Ce que l'agent inscrit :** une ligne par rendu dans `pilotage/journal.md` : ce qui a
  changé dans le schéma, comment le défaire (revenir à 1.6.0 : colonnes et clés
  ajoutées seulement, rien de supprimé).

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** dalles MNT de 1 km à 0,5 m ; marge de 50 m autour des UGF ; CVAT à
  2 m. Choisies le 2026-11-20 sans mesure sur le téléphone.
- **Mesurée par :** mission 039 (temps de lecture d'une dalle et mémoire sur le S23) et
  premier projet livré par nemeton (taille réelle des dalles pour Marsannay).
- **Gel :** avant la publication de la version qui contient 039 ; d'ici là, le schéma
  garde ces valeurs dans des clés (`cote_m`, `marge_foret_m`), jamais en dur ailleurs.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 037a (tests d'acceptation) ; part de `origin/main`.
- **Ordre de fusion :** 037a et 037 groupées (une PR) → 038 (test du transport, sans code
  nouveau attendu : les dalles à plat passent déjà l'extraction du `.qgz`) → 039 → 040.
- **PR en brouillon jusqu'à :** tests de 037a verts.
- **CI rouge attendue :** aucune à la fusion. Les tests de 037a sont rouges tant que 037
  n'est pas écrite (même branche, rouge justifié jusqu'au premier commit de 037).

## Tests

- **Comment saura-t-on que c'est juste :** tests de 037a : version 1.7.0 ; clés `dalles`
  et `cvat` présentes et cohérentes avec le brief ; nommage des dalles (coin sud-ouest,
  bords exacts à 1 000 m, coordonnées négatives impossibles en Lambert-93 refusées) ;
  `creer_gpkg` écrit `x_sommet`/`y_sommet` et l'index, accepte `plot_id` NULL ; un
  GeoPackage 1.6.0 se relit ; `verifier_projet` signale une dalle en Float64 ou décalée,
  accepte une dalle conforme et un dossier sans dalles ; l'exemple régénéré est conforme. `pytest core server
  benchmark` vert.

## Terrain

- **Échantillon que Pascal vérifiera :** l'exemple régénéré ouvert dans QGIS : dalles
  jointives, CVAT lisible, sommets à l'intérieur de leurs houppiers.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours ; au-delà, Pascal décide.
- **Point de suivi :** à la PR de 037a + 037.
- **Qui tranche :** Pascal. Le format engage nemeton : toute divergence avec le brief
  déposé se règle par un brief complémentaire, pas par un écart silencieux.

## Comment saura-t-on que ça marche ?

Le premier projet livré par nemeton en 1.7.0 passe `verifier_projet`
sans retouche, et la placette déplacée de Marsannay a ses dalles.
