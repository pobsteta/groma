# Mission 004 — retour aux anciennes couleurs de Groma (bandeau vert), comme Marculus

## Mission

**En une phrase (quoi, et pourquoi) :** annuler le changement de couleurs de la PR #58 (thème =
jetons `terrain-style` v0.6.0) pour rendre à Groma son ancien thème (`VertVif` #3E7A40, bandeau et
boutons verts), parce que sur le téléphone de Pascal le bandeau a perdu son vert (`primary`
#202F2B presque noir en clair, #E2F4ED presque blanc en sombre), comme chez Marculus, revenu à ses
couleurs (PR marculus #12) en attendant une `terrain-style` v0.6.1 qui reste verte (brief
`~/dev/briefs/vers-terrain/2026-10-24-marculus-palette-reste-verte.md`).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | l'état d'avant est le `Theme.kt` de `main` avant 6aaf042 (`git show 6aaf042^:android/app/src/main/kotlin/groma/app/ui/Theme.kt`) ; Marculus a fait de même (PR marculus #12) |
| A-t-il les moyens ? | oui | |

**Temps de la main :** appuyer.

## Décision de Pascal (2026-10-24)

« Revenir aux anciennes couleurs » : comme Marculus, jusqu'à ce que `terrain` publie la v0.6.1 ;
Groma réadoptera `terrain-style` en même temps que Marculus. Défaut connu et accepté : blanc sur
`VertVif` = 5,17:1.

## Contrat

- `Theme.kt` redevient celui d'avant 6aaf042 (cinq rôles, `VertMarculus`, `VertVif`, `Creme`).
  **Garder la signature `GromaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content)`** : la
  mission 006 (choix du thème) s'y branche.
- `ThemeTerrainStyleTest.kt` est retiré (il compare à `terrain-style`, qui n'est plus le thème) ;
  le garde-fou de la mission 003 attend sur sa branche `test/003-garde-fou-colorscheme` la
  réadoption.
- Dépendance `terrain-style` et dépôt JitPack retirés de `android/app/build.gradle.kts` et
  `android/settings.gradle.kts` (plus aucun module `terrain` utilisé) ; la ligne de
  `docs/dependances.md` retirée ou marquée « retirée le 2026-10-24, en attente de la v0.6.1 »
  (suivre l'usage du fichier pour une dépendance abandonnée).
- `testImplementation(kotlin("test-junit"))` et l'étape `:app:testDebugUnitTest` de la CI
  **restent** (la mission 005-006 y ajoute des tests).
- `PLAN.md`, étape 7i : dire le retour aux anciennes couleurs, pourquoi, et la réadoption attendue.

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** le choix du thème dans les paramètres (missions 005-006) ;
  les couleurs en dur des écrans.

## Harnais

- **Peut :** modifier `Theme.kt`, retirer `ThemeTerrainStyleTest.kt`, `android/app/build.gradle.kts`,
  `android/settings.gradle.kts`, `docs/dependances.md`, `PLAN.md`, dans sa branche
  `fix/004-anciennes-couleurs` partie de `origin/main`.
- **Ne doit pas :** toucher à `main` ; toucher aux écrans.

## Tests

`.claude/test-command` vert, `:app:assembleDebug` vert ; `Theme.kt` identique à
`6aaf042^` (le dire, avec le diff).

## Terrain

Pascal : bandeau vert de nouveau, en clair et en sombre, sur le S23 Ultra.

## Qui veille, qui tranche

`relecteur`, puis Pascal · 3 allers-retours · Pascal tranche.
