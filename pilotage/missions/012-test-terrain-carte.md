# Mission 012 — tests d'équivalence de `terrain-carte` avec le code carte de Groma

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, avant le code, les tests JVM qui vérifient que
`terrain-carte` v0.7.1 donne exactement ce que donne aujourd'hui Groma (tuiles et plages de
`ZoneTuiles`, emprise d'un projet avec sa marge, URL WMTS des fonds Plan IGN, ortho et cadastre),
parce que la mission 013 remplace ce code par la bibliothèque commune (brief Marculus du
2026-10-17, carte validée par Pascal le 2026-10-30) et qu'aucune tuile hors ligne ne doit changer.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | les deux codes sont presque identiques (`Tuiles.kt` de terrain est « repris de Groma ») |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | `ZoneTuilesTest.kt` existant, JitPack `terrain-carte:v0.7.1` |

**Temps de la main :** appuyer — tests seuls, aucune donnée de terrain touchée.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `testeur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** remplacer le code de Groma (mission 013) ; modifier `~/dev/terrain`.

## Harnais

- **Peut :** ajouter `terrain-carte` v0.7.1 en `testImplementation` de `groma-core` (dépôt JitPack
  limité au groupe `com.github.pobsteta.terrain`, déjà déclaré pour `terrain-style`) ; écrire un test
  qui compare, sur des emprises et zooms variés (dont les bornes 12 et 18, une emprise à cheval sur
  le méridien de Greenwich, une emprise refusée par la limite de 30 000 tuiles), les résultats de
  `groma.core.ZoneTuiles` et de `fr.nemeton.terrain.carte.ZoneTuiles`, et les URL de
  `app/carte/Fonds.kt` avec `Geoplateforme.*.url(z, x, y)`.
- **Ne doit pas :** lire ou modifier le code de la mission 013 ; toucher à `main` ; toucher à `~/dev/terrain`.
- **Sources autorisées :** `android/groma-core/src/main/kotlin/groma/core/ZoneTuiles.kt`, `Carte.kt`
  (`Emprise`), `android/app/src/main/kotlin/groma/app/carte/Fonds.kt`,
  `~/dev/terrain/terrain-carte/` (lecture), `/home/pascal/dev/briefs/vers-groma/2026-10-17-bibliotheques-terrain.md`.

## Valeur provisoire (chiffre, réglage)

aucune (les valeurs actuelles de Groma sont la référence).

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `origin/main`.
- **Ordre de fusion :** groupée avec 013 (une PR).
- **PR en brouillon jusqu'à :** la fin de 013.
- **CI rouge attendue :** aucune : les tests comparent deux codes qui existent déjà. Si un test est
  rouge, c'est une différence réelle entre Groma et terrain : l'agent la signale et s'arrête.

## Tests

- **Comment saura-t-on que c'est juste :** `cd android && gradle :groma-core:test` vert ; chaque
  écart possible (plage, nombre de tuiles, URL octet pour octet) a son assertion.

## Terrain

- **Échantillon que Pascal vérifiera :** aucun pour cette mission (voir 013).

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (avec 013, une seule PR), puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** au rendu de l'agent.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Les tests passent avant le remplacement (013) et restent verts après.
