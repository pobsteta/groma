# Mission 037a — Tests d'acceptation du schéma 1.7.0

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, sans lire le code de la mission 037, les tests
d'acceptation du schéma 1.7.0 (dalles MNT à plat dans `rasters/`, `cvat.tif`, houppiers avec
`x_sommet`/`y_sommet`, `verifier_projet`), pour que 037 ne s'évalue pas elle-même.

**Redite par l'agent :** « J'écris, sans lire le code de 037, les tests d'acceptation du schéma 1.7.0
(dalles MNT à plat dans `rasters/`, `cvat.tif`, houppiers avec `x_sommet`/`y_sommet`, `verifier_projet`),
pour que 037 ne s'évalue pas elle-même. » (rendu du 2026-11-20, recopié par l'orchestrateur : la fiche
n'était pas dans le worktree du testeur.)

**Écart de périmètre déclaré :** la fixture `fixtures/schema_1_6_0/` (copie de l'exemple 1.6.0 de `main`
et son README) n'était pas dans « Peut » ; elle sert le test « un GeoPackage 1.6.0 se relit » demandé par
la rubrique « Tests ». Gardée, signalée par le relecteur le 2026-11-20.

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui : brief du 2026-11-20 (§ 2 et § 6) et fiche 037 (« Ce qui est attendu », « Tests ») | |
| A-t-il les moyens ? | oui : pytest, `rasterio`, `pyogrio` dans `.venv` | confier |

**Temps de la main :** donner. **Degré de la main :** le chef tranche.

## Ma réponse d'abord

_Rempli par Pascal seul._

## L'Homme

- **Agent :** `testeur`. **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** écrire le code de 037 ; lire `core/src/groma/dalles.py` ou les
  changements de `gpkg.py` s'ils existent déjà.

## Harnais

- **Peut (périmètre) :** `core/tests/test_schema_1_7.py`, `core/tests/test_dalles.py` (nouveaux),
  `pilotage/journal.md` (ajout seul), cette fiche (« Redite »).
- **Câblé :** pas de commit sur `main` ; journal en ajout seul.
- **Ne doit pas :** lire le code de 037 ; inventer une API au-delà de la fiche 037 (noms :
  `groma.dalles.nom_dalle`, `dalle_du_point`, `dalles_du_rectangle`, `verifier_projet` ; s'il en
  faut un autre, le demander à l'orchestrateur).
- **Sources autorisées :** `briefs/vers-nemeton/2026-11-20-relief-cvat-houppiers-foret.md`,
  `pilotage/missions/037-schema-1-7-foret.md`, le schéma actuel, les tests existants de `core/tests/`.

## Journal

- Une ligne par rendu : tests écrits, rouge attendu et pourquoi.

## Valeur provisoire (chiffre, réglage)

- Celles de la fiche 037 (1 km, 0,5 m, 50 m, 2 m) : les tests les lisent dans le schéma, jamais en dur.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `origin/main`, branche partagée avec 037 (`feat/037-schema-1-7`).
- **Ordre de fusion :** groupée avec 037 (une PR).
- **CI rouge attendue :** les nouveaux tests, jusqu'au code de 037 sur la même branche ; jamais à la fusion.

## Tests

- Version 1.7.0 ; clés `rasters.dalles` et `rasters.cvat` conformes au brief ; pas de clé MNH de dalle.
- Nommage : `nom_dalle(849, 6689) == "rasters/mnt_0849_6689.tif"` ; point sur un bord à 1 000 m pile
  rattaché à la dalle au nord-est ; rectangle à cheval sur 4 dalles → 4 noms.
- `creer_gpkg` : houppiers avec `x_sommet`/`y_sommet` et index ; `plot_id` NULL accepté ; un
  GeoPackage 1.6.0 (fixture) relu sans erreur.
- `verifier_projet` : dalle Float64, dalle décalée de 0,5 m, dalle de 1 999 pixels, `cvat.tif` en
  Float32 → un écart chacun, en français ; dossier sans dalles → aucun écart.

## Terrain

- Aucun (tests seulement).

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Les tests sont rouges avant 037, verts après, sans avoir été retouchés pour passer.
