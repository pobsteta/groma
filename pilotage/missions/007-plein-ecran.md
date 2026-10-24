# Mission 007 — affichage plein écran, réglable dans les paramètres

## Mission

**En une phrase (quoi, et pourquoi) :** ajouter à Paramètres de Groma un réglage « Plein écran »
(case, désactivé par défaut) qui masque les barres de statut et de navigation, réapparaissant au
glissement, comme dans Marculus, parce que Pascal le demande (2026-10-24) pour gagner de la place à
l'écran sur le terrain.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | modèle Marculus, lecture seule : `git -C /home/pascal/dev/marculus show origin/main:app/src/main/kotlin/io/github/pobsteta/marculus/MainActivity.kt` (l. 51-61 : `WindowCompat.getInsetsController`, `hide(systemBars())`, `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), `ParametresScreen.kt` l. 178 (`LigneReglage`), libellés `param_plein_titre` / `param_plein_desc` |
| A-t-il les moyens ? | oui | réglages de Groma (`Reglages` dans `EcranParametres.kt`, `Preferences` de `MainActivity.kt`, comme `theme` de la mission 006) |

**Temps de la main :** appuyer.

## Contrat

- `Reglages.pleinEcran: Boolean = false`, enregistré et relu comme `theme` (SharedPreferences `groma`).
- Paramètres : une ligne à case, titre « Plein écran », description « Masque les barres de statut et
  de navigation. », dans l'idiome des autres réglages de Groma.
- Appliqué **à toute l'application, comme le thème** (décision de Pascal du 2026-10-24 pour le
  thème) : `MainActivity` (tout de suite au changement) **et** `CaptureActivity` (à son ouverture,
  en relisant le réglage). Les barres réapparaissent au glissement et se masquent de nouveau.
- Le contenu reste lisible : `BandeauCompact` et la surcouche de capture gardent leurs marges
  d'encoche et de navigation (`statusBarsPadding`, `displayCutout`, l'écouteur d'insets de
  `CaptureActivity` l. 73) ; dire ce qui change quand les barres sont masquées.
- Désactivé : les barres reviennent tout de suite.
- `PLAN.md` : une ligne (7k).

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** l'anti-veille de Marculus, le thème, les couleurs.

## Harnais

- **Peut :** `EcranParametres.kt`, `MainActivity.kt`, `CaptureActivity.kt`, `res/values/strings.xml`,
  `PLAN.md` ; logique pure dans `groma-core` avec un test si utile ; branche `feat/007-plein-ecran`
  partie de `origin/main`.
- **Ne doit pas :** toucher à `main` ; modifier les tests existants.

## Tests

`.claude/test-command` vert, `:app:assembleDebug` vert.

## Terrain

Pascal, S23 Ultra : case cochée → barres masquées sur l'accueil, la carte et la capture ; glisser
depuis le bord les montre puis elles repartent ; bandeau et boutons de capture non masqués par
l'encoche ; le réglage survit à une relance ; décoché → barres de retour.

## Qui veille, qui tranche

`relecteur`, puis Pascal · 3 allers-retours · Pascal tranche.
