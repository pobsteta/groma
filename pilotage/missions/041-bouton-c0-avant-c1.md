# Mission 041 — « Relever C0 » à gauche de C1 dans la vue Placette

## Mission

**En une phrase (quoi, et pourquoi) :** déplacer le bouton « Relever C0 » de la ligne d'état du
centre, en haut de la vue Placette, vers la rangée des cibles, à gauche de C1, parce que Pascal le
cherche là où il saisit C1 à C3 (demande du 2026-11-20).

**Redite par l'agent :** _à compléter par l'agent avant d'agir._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui : `ui/EcranPlacette.kt` (bouton l. ~226, rangée des cibles), même action `releve.demanderDepuisFiche` | |
| A-t-il les moyens ? | oui : compilation, essai de Pascal | confier |

**Temps de la main :** donner. **Degré de la main :** le chef tranche.

## Ma réponse d'abord

_Rempli par Pascal seul._

## L'Homme

- **Agent :** `dev`. **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** changer le flux du relevé de C0 (mission 030), ni la carte.
- **Pas de mission testeur** (exception validée par Pascal le 2026-11-20) : déplacement d'un
  bouton, l'essai de Pascal sur le S23 en tient lieu.

## Harnais

- **Peut (périmètre) :** `android/app/src/main/kotlin/groma/app/ui/EcranPlacette.kt`,
  `res/values/strings.xml` si un libellé court est nécessaire, `docs/terrain.md`, `PLAN.md`,
  journal (ajout seul).
- **Câblé :** pas de `main` ; pas de « terminé » sans tests verts ; journal en ajout seul.
- **Ne doit pas :** supprimer la ligne d'état de C0 (elle reste, sans bouton) ; casser la rangée sur
  un écran étroit (C0 à C3 restent sur une ligne au S23 en portrait).

## Valeur provisoire (chiffre, réglage)

- Aucune.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `origin/main`. Aucun fichier commun avec 037.
- **CI rouge attendue :** aucune.

## Tests

- Tests existants verts ; `:app:assembleDebug` compile.

## Terrain

- Vue Placette sur le S23 : « Relever C0 » à gauche de C1, même relevé qu'avant.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Pascal relève C0 depuis la rangée des cibles sans remonter en haut de l'écran.
