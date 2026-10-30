# Mission 013 — la carte de Groma utilise `terrain-carte`

## Mission

**En une phrase (quoi, et pourquoi) :** remplacer `ZoneTuiles`, `Tuile`, `Emprise` de `groma-core`
et l'URL WMTS écrite dans `app/carte/Fonds.kt` par `terrain-carte` v0.7.1, sans rien changer au
comportement, pour qu'une correction de la carte se fasse une fois dans `~/dev/terrain` pour
Groma, Marculus et Surculus (brief Marculus du 2026-10-17, première bibliothèque terrain choisie
le 2026-10-30 parce que c'est la plus simple à brancher).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | brief `vers-groma/2026-10-17-bibliotheques-terrain.md`, tableau « Ce qui remplace » |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui, après 012 | tests d'équivalence de 012 |

**Temps de la main :** appuyer — code déjà identique, risque faible ; pas de donnée de terrain écrite.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** `terrain-gnss`, `terrain-gpkg` (missions suivantes, à découper
  après celle-ci) ; changer la carte à l'écran.

## Harnais

- **Peut :** déclarer `terrain-carte` v0.7.1 en `implementation` (même dépôt JitPack que
  `terrain-style`) ; supprimer `ZoneTuiles.kt` et `Emprise` de `groma-core` et faire pointer
  `Carte.kt`, `TelechargementTuiles.kt`, `TuilesHorsLigne.kt`, `EcranCarte.kt`, `Fonds.kt` vers
  `fr.nemeton.terrain.carte` ; garder `SourceGeoplateforme` (osmdroid) en s'appuyant sur
  `Geoplateforme.*.url(z, x, y)` ; déplacer `ZoneTuilesTest` si ses cas sont déjà dans terrain ;
  mettre à jour `docs/dependances.md` (ligne `terrain-style` élargie à `terrain-carte`) et `PLAN.md`.
- **Ne doit pas :** modifier les tests de 012 (s'il les croit faux, il le dit) ; toucher à `main` ;
  toucher à `~/dev/terrain` (un écart se signale par un brief, pas par un correctif).
- **Sources autorisées :** fichiers ci-dessus, `~/dev/terrain/terrain-carte/` (lecture), brief cité.

## Valeur provisoire (chiffre, réglage)

aucune (limites de téléchargement reprises telles quelles : zooms 12 à 18, marge 200 m, 30 000 tuiles).

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 012 (même branche, après ses tests).
- **Ordre de fusion :** groupée avec 012 (une PR).
- **PR en brouillon jusqu'à :** relecture « prêt ».
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** tests de 012 verts ; `gradle :groma-core:test
  :app:testDebugUnitTest :app:assembleDebug` vert ; plus aucune référence à `groma.core.ZoneTuiles`.

## Terrain

- **Échantillon que Pascal vérifiera :** sur le S23 Ultra, écran Carte d'un projet : les trois fonds
  s'affichent, « Télécharger la zone » annonce le même nombre de tuiles qu'avant, la carte marche hors ligne.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** au rendu de l'agent.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Même nombre de tuiles annoncé et carte hors ligne identique sur le téléphone, avec un code carte en moins dans Groma.
