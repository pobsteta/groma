# Mission 015 — tests de la vue « Cibles » (placer C1-C3 depuis le centre)

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, avant le code, les tests JVM de `groma-core` qui
fixent le calcul et les contrôles de la future vue « Cibles » (position d'une cible à partir du
centre mesuré, d'un azimut magnétique dans l'unité de la placette et d'une distance horizontale ;
source de l'azimut ; répartition des cibles), parce que la mission 016 fait de Groma le seul endroit
où l'on saisit les cibles (QField ne les saisit plus, décision de Pascal du 2026-11-11) et que
l'azimut des cibles oriente toute la placette : 1° d'erreur décale de 26 cm un arbre à 15 m.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | la chaîne d'azimut existe déjà dans Groma (« Y aller » : unité de la placette, déclinaison WMM2025, convergence Lambert 93) et dans Surculus (`SaisiePointage.kt`, spec § unités) |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | `groma.core.Declinaison` et ses 102 valeurs de référence, `core/src/groma/georeferencement.py` (`azimut_grille_mesure`) comme référence Python |

**Temps de la main :** appuyer — tests seuls.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `testeur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** écrire la vue ou le calcul (mission 016) ; modifier `~/dev/surculus`.

## Harnais

- **Peut :** écrire dans `android/groma-core/src/test/` les tests d'un futur `groma.core.PlacementCible`
  (nom à confirmer avec 016, API décrite en tête du test) :
  1. **position** : centre (Lambert 93) + azimut magnétique (degrés ou grades) + déclinaison +
     convergence + distance horizontale → point Lambert 93, à 1 mm près ; cas : nord, est, 400 gr = 0,
     azimut 399,9 gr, déclinaison négative, convergence de Brest et de Strasbourg ; mêmes résultats
     que `azimut_grille_mesure` de `core/src/groma/georeferencement.py` (valeurs recopiées à la main) ;
  2. **saisie refusée** : distance ≤ 0 ou > 60 m, azimut hors [0, 360[ ou [0, 400[, diamètre ≤ 0 ou
     > 1000 cm (bornes du schéma), identifiant hors C1-C3 ;
  3. **source de l'azimut** : `boussole_visee` (tapé au clavier, référence) ou `boussole_telephone`
     (lu au capteur, toujours en degrés, converti dans l'unité de la placette pour l'affichage) ;
  4. **répartition** (alertes, jamais un refus) : deux cibles à moins de la valeur provisoire d'écart
     angulaire ; une cible plus proche du centre que la distance minimale ; moins de 3 cibles ; une
     cible déjà placée et replacée (la nouvelle mesure remplace l'ancienne, l'ancienne n'est pas gardée).
- **Ne doit pas :** lire ou modifier le code de la mission 016 ; toucher à `main` ; toucher au schéma.
- **Sources autorisées :** `android/groma-core/src/main/kotlin/groma/core/` (déclinaison, azimut,
  projections, « Y aller »), `core/src/groma/georeferencement.py`, `docs/terrain.md`,
  `~/dev/surculus/docs/spec.md` (tableau des unités) et `~/dev/surculus/app/.../controle/SaisiePointage.kt` (lecture).

## Valeur provisoire (chiffre, réglage)

- **Écart angulaire minimal entre deux cibles :** 60° (idéal 120°) ; **distance minimale au centre :** 8 m.
- **Mesurée par :** résidu d'orientation (`rmse_azimut_deg`) des premières placettes test.
- **Gel :** après trois placettes test.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `origin/main`.
- **Ordre de fusion :** groupée avec 016 (une PR).
- **PR en brouillon jusqu'à :** la fin de 016.
- **CI rouge attendue :** oui, `:groma-core:test` rouge (classe absente) jusqu'à la fin de 016.

## Tests

- **Comment saura-t-on que c'est juste :** chaque cas ci-dessus a son assertion ; les valeurs
  attendues de position sont calculées à la main ou recopiées de Python, jamais de l'implémentation.

## Terrain

- **Échantillon que Pascal vérifiera :** aucun pour cette mission (voir 016).

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (avec 016, une seule PR), puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** au rendu de l'agent.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Les tests décrivent sans ambiguïté le calcul et les alertes ; 016 les fait passer sans les modifier.
