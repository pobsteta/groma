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

**Redite par l'agent :** passer le schéma à 1.7.0 (dalles MNT de 1 km à plat dans `rasters/`, sans MNH, `rasters/cvat.tif`, houppiers de toute la forêt avec `x_sommet`/`y_sommet` et index B-tree), écrit par `creer_gpkg` et contrôlé par `groma.dalles`, pour qu'une placette déplacée garde MNT et houppiers et que 038 à 040 et nemeton lisent un seul format vérifiable.

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
  `core/src/groma/dalles.py` (nouveau), `core/tests/`, `exemples/creer_exemple.py` (ajouté le 2026-11-20,
  plan validé par Pascal),
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

## Précisions de l'orchestrateur (2026-11-20, après le rendu de 037a)

Arrêtées pour que les tests de 037a et le code de 037 parlent de la même chose :

- `groma.dalles.nom_dalle(x_km: int, y_km: int) -> str` : `"rasters/mnt_0849_6689.tif"` ; refuse un
  kilomètre négatif (`ValueError`).
- `dalle_du_point(x: float, y: float) -> tuple[int, int]` : `(x_km, y_km)` du coin sud-ouest ; un
  point pile sur un bord à 1 000 m appartient à la dalle au nord-est.
- `dalles_du_rectangle(xmin, ymin, xmax, ymax) -> list[tuple[int, int]]` : intervalle semi-ouvert,
  un bord max pile sur 1 000 m n'ajoute pas la dalle suivante ; triée par x puis y.
- `verifier_projet(dossier: Path | str) -> list[str]` : `dossier` contient `rasters/` ; un écart par
  défaut, en français, nommant le fichier ; un raster illisible est un écart (« illisible »).
- Schéma : l'index des houppiers est déclaré dans la couche par
  `"index": [{"nom": "houppiers_sommet", "colonnes": ["x_sommet", "y_sommet"]}]` ; le CVAT porte
  `"resolution_m": 2`.
- Les tests de 037a peuvent être resserrés par 037 sur ces points (clé d'index, résolution du CVAT)
  sans en relâcher aucun ; tout autre changement de test passe par l'orchestrateur.
- **Environnement :** le `.venv` installe `groma` en mode éditable depuis le checkout principal ;
  dans un worktree, lancer pytest avec `PYTHONPATH=$PWD/core/src:$PWD/server/src`. La machine est
  saturée le 2026-11-20 (un `creer_gpkg` prend 70 s) : lancer les tests ciblés, la suite complète
  tournera en CI.

## Plan validé (Pascal, 2026-11-20)

Plan du dev rendu le 2026-11-20 (journal), validé par Pascal avec : `exemples/creer_exemple.py` dans le
périmètre ; **quatre** dalles pour l'exemple (son emprise croise x = 925 km et y = 6 700 km) ; `z0` de
P002 remonté pour que les plans de sol de P001 et P002 coïncident (données synthétiques). Hors
périmètre, notés : apex du serveur encore au centroïde (brancher `x_sommet` plus tard) ; unicité de
`houppier_id` sur la forêt non garantie par le brief.

## Compte rendu du dev (2026-11-20)

Fait :
- schéma 1.7.0 (`rasters.dalles`, `rasters.cvat`, `houppiers.x_sommet`/`y_sommet` requis, clé `index`) ;
- `groma.dalles` : `nom_dalle`, `dalle_du_point`, `dalles_du_rectangle`, `verifier_projet` ;
- `creer_gpkg` : index du schéma créés, houppiers sans sommets refusés avant toute écriture ;
- exemple régénéré : quatre dalles, `cvat.tif`, houppiers hors placette, `z0` de P002 remonté à 438,9 m ;
- `docs/architecture.md`, `PLAN.md` (7ac).

Écarts et constats :
- les dalles de l'exemple arrondissent l'altitude au 1/128 m (moins de 4 mm d'écart avec les rasters par
  placette) : sans cela, DEFLATE sans prédicteur donne 2,26 Mo, au-dessus de la limite de 2 Mo ; avec,
  1,66 Mo (dalles et CVAT). Données synthétiques seulement ;
- `rayon_m` de l'exemple fixé à 20 m dans `creer_exemple.py` : depuis la mission 035, le script relancé
  prenait le défaut de 15 m, ce qui retirait la moitié des arbres et cassait les tests Kotlin qui lisent
  l'exemple (projets déjà préparés inchangés). Avec ce réglage, l'exemple garde ses 18 arbres ;
- `exemples/resultats/P001_definitif.gpkg` remis par `git checkout` : seuls les `uuid` (tirés au hasard)
  changeaient ;
- test de 037a faux, non modifié : `test_gpkg_16_ecriture_protocole_telephone_toujours_possible` écrit
  `etat_traitement = "en_cours"`, valeur absente du domaine (`a_faire`, `provisoire`, `envoye`,
  `definitif`) ; `mettre_a_jour_etat` la refuse à bon droit. **Corrigé par le testeur** en `"provisoire"`
  (aller-retour 1/3, commit c522253) ;
- une interpolation bilinéaire à moins d'un demi-pixel d'un bord de dalle a besoin de la dalle voisine
  (P001 est pile sur le coin 925 km / 6 700 km) : à traiter par 039.

## Relecture (2026-11-20)

`relecteur` : **prêt pour Pascal** (74 tests ciblés verts, tests de 037a intacts sauf « en_cours » →
« provisoire », règle 3 et lecture des projets 1.6.0 respectées). Points ouverts : `verifier_projet` ignore
sans écart un fichier `mnt_*` mal nommé (par exemple trois chiffres) — à trancher par Pascal ; CI à voir
sur la PR.
