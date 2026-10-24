# Mission 002 — thème de Groma = jetons de `terrain-style`

## Mission

**En une phrase (quoi, et pourquoi) :** remplacer les cinq rôles écrits à la main de
`android/app/src/main/kotlin/groma/app/ui/Theme.kt` par les 48 jetons `StyleTerrain.clair` et
`StyleTerrain.sombre` de `terrain-style` v0.6.0, recopiés un pour un dans les `ColorScheme`, pour
que le texte tienne 7:1 au soleil (blanc sur `VertVif` = 5,17:1 aujourd'hui) et que Groma ait les
mêmes couleurs que Marculus et Surculus (issue #57, ADR 0008 de Surculus acceptée par Pascal le
2026-10-23).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | même travail que la mission 006 de Marculus, relue « prêt » le 2026-10-24 (PR marculus #9) ; Groma n'a ni catalogue de versions ni tests JVM dans `:app` |
| A-t-il les moyens ? | oui | `terrain-style` v0.6.0 sur JitPack ; test d'acceptation de la mission 001 |

**Temps de la main :** appuyer (la 006 de Marculus a levé les inconnues).

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## Décisions de Pascal (2026-10-24)

- **Mode sombre gardé**, avec `StyleTerrain.sombre` ; `GromaTheme` garde `isSystemInDarkTheme()`.
- **`terrain-style` v0.6.0.**
- Le bouton principal change de vert : coût accepté par Pascal (issue #57).
- `reussi` est provisoire jusqu'à l'essai au soleil du 2026-10-30 : ne pas l'utiliser ici.

## Contrat (fixé par la mission 001)

`internal val SchemaClair: ColorScheme` et `internal val SchemaSombre: ColorScheme` dans
`Theme.kt`, égaux rôle par rôle (48 rôles) à `StyleTerrain.clair` et `StyleTerrain.sombre`,
utilisés par `GromaTheme`. Simples `lightColorScheme(...)` / `darkColorScheme(...)`, chargeables
sur la JVM sans Robolectric. Une seule fonction pour clair et sombre plutôt que deux listes de 48
lignes (remarque du relecteur de Marculus).

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet.
- **`testeur` associé :** mission 001 (le test précède le code).
- **Ce qui ne lui est pas demandé :** toucher aux `Color(0x…)` écrites en dur dans les écrans
  (21 aujourd'hui, carte à part), aux couleurs de la carte, à `reussi` / `alerte`, ni changer de
  version de Compose.

## Harnais

- **Peut :** modifier `Theme.kt`, `android/app/build.gradle.kts` (`terrain-style` en
  `implementation`), `.github/workflows/ci.yml` (ajouter `gradle :app:testDebugUnitTest` au job
  `android-app`), dans la branche qui porte le test de 001.
- **Ne doit pas :** modifier le test de 001 (s'il le croit faux, il le dit) ; toucher à `main`.
- **Sources autorisées :** `android/app/src/main/kotlin/groma/app/ui/`, `~/dev/terrain/README.md`,
  `~/dev/terrain/terrain-style/src/main/`, issue #57, le `Theme.kt` de Marculus sur sa branche
  `feat/006-theme-terrain-style`.

## Tests

- **Comment saura-t-on que c'est juste :** le test de 001 passe ; `cd android && gradle
  :groma-core:test :app:testDebugUnitTest :app:assembleDebug` passe ; la CI de la PR est verte.

## Terrain

- **Échantillon que Pascal vérifiera :** l'APK de la PR sur le S23 Ultra, au soleil (essai du
  2026-10-30, avec Marculus) : écrans Projets, Carte et capture, bouton principal, mode sombre.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** PR ouverte.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Au soleil, le texte des boutons se lit sans plisser les yeux, et Groma a le même vert que Marculus.

## Reprise après relecture (aller-retour 1/3, 2026-10-24)

Le relecteur juge le code et le test prêts ; il manque deux lignes de documentation, que la
fiche ne permettait pas d'écrire. À faire **dans la même branche** `feat/002-theme-terrain-style`
(worktree `/home/pascal/dev/groma/.claude/worktrees/agent-a2b679147f5be7d27`), un commit `docs(…)` :

- **`docs/dependances.md` (règle 6 : copyleft signalé)** : une ligne dans le tableau des
  dépendances utilisées pour `terrain-style` v0.6.0 (bibliothèque `terrain`, JitPack) · Android
  (thème) · GPL-3.0-or-later · même auteur, jetons de couleur communs à Marculus, Groma et
  Surculus, Kotlin pur, seule dépendance `kotlin-stdlib` ; dépôt JitPack limité au groupe
  `com.github.pobsteta.terrain`. Suivre la forme des lignes voisines.
- **`PLAN.md` (règle 7)** : une ligne courte dans l'avancement : thème = jetons `terrain-style`
  v0.6.0 (#57), test d'égalité, CI `:app:testDebugUnitTest` ; essai au soleil le 2026-10-30.
  Ne pas toucher au `PLAN.md` de la copie principale (`/home/pascal/dev/groma`, autre branche).
- **Facultatif, cosmétique** : `Theme.kt` l. 17-64, arguments indentés de 8 espaces au lieu de 4.

**Harnais élargi** : peut modifier `docs/dependances.md` et `PLAN.md` de la branche. Toujours
interdit : le test de 001, `main`, le code hors `Theme.kt`.
