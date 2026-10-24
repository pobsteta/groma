# Mission 006 — choix du thème dans les paramètres de Groma

## Mission

**En une phrase (quoi, et pourquoi) :** ajouter à l'écran Paramètres de Groma le choix du thème
Système / Clair / Sombre en liste déroulante, enregistré avec les autres réglages et appliqué à
toute l'application, comme dans Marculus (missions 007-008 et 011 de Marculus), parce que Pascal
le demande (2026-10-24) pour choisir son affichage au soleil ou en sous-bois.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | modèle Marculus : `git -C /home/pascal/dev/marculus show 042797a c73c9a0` (lecture seule) — `ChoixTheme`, `ParametresScreen.ChoixThemeReglage` (liste déroulante), `MainActivity` (`reglages.theme.estSombre(isSystemInDarkTheme())`), libellés FR et EN |
| A-t-il les moyens ? | oui | tests de la mission 005 ; `Theme.kt` de la mission 004 |

**Temps de la main :** appuyer.

## Contrat

- Celui de la mission 005 (`ChoixTheme`, `choixThemeDepuis`) dans `groma-core`.
- `Reglages` (`android/app/src/main/kotlin/groma/app/ui/EcranParametres.kt`) gagne `theme: ChoixTheme`
  (défaut `SYSTEME`), enregistré et relu là où les autres réglages le sont.
- `MainActivity` appelle `GromaTheme(darkTheme = reglages.theme.estSombre(isSystemInDarkTheme()))`
  et le changement s'applique sans redémarrer.
- Libellés « Thème », « Système », « Clair », « Sombre » en français (et en anglais si Groma a
  `values-en`), repris de Marculus.

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet · **`testeur` associé :** 005.
- **Ce qui ne lui est pas demandé :** les couleurs elles-mêmes (mission 004), les couleurs en dur.

## Harnais

- **Peut :** `groma-core` (code de `ChoixTheme`), `EcranParametres.kt`, `MainActivity.kt`,
  `res/values*/strings.xml`, `PLAN.md` (une ligne), dans une branche `feat/006-choix-theme` qui
  réunit les commits de 004 et de 005 (l'orchestrateur indique les commits de départ).
- **Ne doit pas :** modifier les tests de 005 ; toucher à `main`.

## Tests

`.claude/test-command` vert (tests de 005 compris), `:app:assembleDebug` vert.

## Terrain

Pascal, sur le S23 Ultra : Paramètres → Thème ; Clair, Sombre et Système changent l'affichage
tout de suite et le choix survit à un redémarrage ; bandeau vert dans les deux.

## Qui veille, qui tranche

`relecteur`, puis Pascal · 3 allers-retours · Pascal tranche.

## Reprise après relecture (aller-retour 1/3, 2026-10-24)

Dans la même branche `feat/006-choix-theme` (worktree
`/home/pascal/dev/groma/.claude/worktrees/agent-a98ca480795d16a61`), sans toucher aux tests de 005 :

1. **Liste déroulante, comme Marculus aujourd'hui** (`git -C /home/pascal/dev/marculus show c73c9a0`,
   mission 011 de Marculus) : remplacer les boutons radio de `EcranParametres.kt:116-141` par le
   même `DropdownMenu` (`Role.DropdownList`, flèche `ArrowDropDown`, coche `Icons.Default.Check` sur
   le choix actuel, libellé `param_theme_choix_actuel` ou son équivalent Groma).
2. **Toute l'application suit le choix (décision de Pascal du 2026-10-24)** : aujourd'hui seul le
   Compose de `MainActivity` change ; `Theme.Groma` (`res/values/themes.xml`, `Theme.AppCompat.DayNight`)
   fait suivre le téléphone à `CaptureActivity` et aux `AlertDialog` (`MainActivity.kt:229`, `:262`,
   `CaptureActivity.kt:243`). Appliquer le choix par `AppCompatDelegate.setDefaultNightMode(...)`
   (SYSTEME → `MODE_NIGHT_FOLLOW_SYSTEM`, CLAIR → `MODE_NIGHT_NO`, SOMBRE → `MODE_NIGHT_YES`) au
   démarrage (avant le premier écran) et à chaque changement ; Pascal accepte que l'écran se
   recharge une fois au changement. Le `GromaTheme` doit rester cohérent avec ce mode.
   Si la correspondance `ChoixTheme` → mode peut vivre en logique pure, la mettre là où elle est
   testable et le dire (le testeur n'est pas relancé : un test simple écrit par toi est permis,
   à part, sans toucher à `ChoixThemeTest`).
3. **`PLAN.md` 7j** : dire juste ce qui est livré (liste déroulante, toute l'appli) ; et le nombre
   exact de tests (13 `@Test`, 14 cas).

**Une seule PR** (décision de Pascal) ; l'orchestrateur l'ouvre.

**Harnais élargi** : `res/values/themes.xml`, `CaptureActivity.kt`, application de démarrage si besoin.
