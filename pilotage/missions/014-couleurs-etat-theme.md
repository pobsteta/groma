# Mission 014 — plan : les couleurs d'état GNSS et de la carte passent au thème

## Mission

**En une phrase (quoi, et pourquoi) :** proposer, avant tout code, la correspondance entre les 18
couleurs écrites en dur de `Gnss.kt` (qualité du fix, état RTK, barres SNR) et `EcranCarte.kt`
(étiquettes, « Fini ») et les rôles du thème ou de `terrain-style` (`reussi`, `alerte`, `error`,
`outline`…), avec leur contraste en clair et en sombre, parce que ces couleurs ne suivent ni le
mode sombre ni le style commun, et que Pascal doit choisir le sens avant qu'on les remplace
(carte validée le 2026-10-30).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | **non** | le thème n'a que trois couleurs d'état (`reussi`, `alerte`, `error`) pour cinq niveaux SNR et cinq qualités de fix (dont le bleu DGPS) : il faut fusionner des niveaux, ajouter des rôles dans terrain, ou garder des couleurs de signal fixes. Marculus a les mêmes couleurs en dur (`BadgeFix.kt`). |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | `Contraste.kt` de terrain-style, Schema clair et sombre, captures d'écran |

**Temps de la main :** **tenir** — l'agent rend un plan, que Pascal tranche avant toute exécution ;
la mission de code (et son testeur) sera écrite d'après ce plan.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `afficheur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** modifier le code ; modifier `~/dev/terrain`.

## Harnais

- **Peut :** écrire le plan dans `pilotage/missions/014-plan.md` : un tableau par couleur (fichier:ligne,
  usage, valeur actuelle, rôle proposé en clair et en sombre, contraste mesuré avec `Contraste.kt`
  sur le fond réel), et au plus trois options de sens (par exemple : tout au thème en fusionnant des
  niveaux / rôles nouveaux proposés à terrain par brief / couleurs de signal fixes nommées dans
  `Theme.kt`), avec sa recommandation.
- **Ne doit pas :** toucher au code ; toucher à `main` ; inventer un rôle de thème qui n'existe pas
  sans le dire.
- **Sources autorisées :** `android/app/src/main/kotlin/groma/app/ui/Gnss.kt`, `EcranCarte.kt`,
  `Theme.kt`, `~/dev/terrain/terrain-style/` (lecture, dont `Schema.kt`, `Semantique.kt`,
  `Contraste.kt` et l'ADR 0002 / 0006 de terrain), `~/dev/marculus/.../ui/gnss/BadgeFix.kt` (lecture).

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** les 18 couleurs actuelles (essai au soleil positif le 2026-10-24 sur l'ensemble de l'appli).
- **Mesurée par :** contraste calculé par l'agent (première tâche) ; lisibilité au soleil par Pascal sur le S23 Ultra, à la mission de code.
- **Gel :** avant la fusion de la mission de code.

## Dépend de / rouge attendu jusqu'à

aucune (plan seul, pas de PR de code).

## Tests

- **Comment saura-t-on que c'est juste :** chaque couleur en dur figure dans le tableau (18 lignes,
  vérifiable par `grep -n 'Color(0x' Gnss.kt EcranCarte.kt`) ; chaque contraste proposé atteint 4,5:1
  pour du texte, 3:1 pour un aplat (WCAG), ou l'écart est dit.

## Terrain

- **Échantillon que Pascal vérifiera :** le tableau, puis, à la mission de code, le badge GNSS et
  les barres SNR au soleil en clair et en sombre.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** au rendu du plan.
- **Qui tranche :** Pascal (choix du sens).

## Comment saura-t-on que ça marche ?

Pascal choisit une option en lisant le plan, et la mission de code peut s'écrire sans autre question.
