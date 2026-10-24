# Mission 001 — test d'égalité du thème avec `terrain-style`

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, avant le code et sans le lire, le test JVM qui
vérifie que les `ColorScheme` clair et sombre de Groma sont égaux, rôle par rôle, aux jetons
`StyleTerrain.clair` et `StyleTerrain.sombre` de `terrain-style` v0.6.0, pour que le critère
d'acceptation de l'issue #57 soit vérifié à chaque PR et pas seulement à l'œil.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | contrat ci-dessous ; même test que la mission 005 de Marculus (`~/dev/marculus/app/src/test/kotlin/io/github/pobsteta/marculus/ui/theme/ThemeTerrainStyleTest.kt` sur sa branche `feat/006-theme-terrain-style`), relu « prêt » le 2026-10-24 |
| A-t-il les moyens ? | oui | `com.github.pobsteta.terrain:terrain-style:v0.6.0` servi par JitPack ; source dans `~/dev/terrain/terrain-style/` |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## Décisions de Pascal (2026-10-24)

- **Mode sombre gardé**, avec `StyleTerrain.sombre`.
- **`terrain-style` v0.6.0.** Groma ne prend encore aucun autre module de `terrain`.

## Contrat (ce que le test suppose, que la mission 002 doit fournir)

- Dans `android/app/src/main/kotlin/groma/app/ui/Theme.kt` (paquet `groma.app.ui`) :
  `internal val SchemaClair: ColorScheme` et `internal val SchemaSombre: ColorScheme`, ceux que
  `GromaTheme` utilise.
- Pour chacun des 48 rôles de `fr.nemeton.terrain.style.Schema` : `SchemaClair.<rôle> ==
  Color(StyleTerrain.clair.<rôle>)`, et de même en sombre. Un garde-fou compte les champs de
  `Schema` par réflexion ; un échec nomme tous les rôles fautifs.
- `reussi`, `alerte` et `interditsEnTexte` ne sont pas des rôles de `ColorScheme` : hors test.

## L'Homme

- **Agent :** `testeur`
- **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** toucher à `Theme.kt` ou à un écran ; faire passer le test
  (il ne compile pas tant que 002 n'est pas livrée : il le dit).

## Harnais

- **Peut :** créer `android/app/src/test/kotlin/groma/app/ui/ThemeTerrainStyleTest.kt` ; ajouter à
  `android/app/build.gradle.kts` `testImplementation(kotlin("test-junit"))` (ou l'idiome de
  `groma-core`) et `terrain-style` ; ajouter le dépôt JitPack à `android/settings.gradle.kts`,
  limité au groupe : `maven("https://jitpack.io") { content { includeGroup("com.github.pobsteta.terrain") } }` ;
  dans sa branche, partie de `main`.
- **Ne doit pas :** lire ni modifier `android/app/src/main/` ; toucher à `main`.
- **À vérifier et à dire :** la version de Material 3 résolue par le BOM `2026.09.00`
  (`gradle :app:dependencyInsight --dependency material3`). `Schema` suit Material 3 1.4.0 : si
  le `ColorScheme` résolu a d'autres rôles, le signaler sans trancher.
- **Sources autorisées :** ce qui précède, `~/dev/terrain/README.md`,
  `~/dev/terrain/terrain-style/src/main/`, `android/groma-core/build.gradle.kts`, le test de
  Marculus cité plus haut.

## Tests

- **Comment saura-t-on que c'est juste :** le test couvre les 48 rôles × 2 schémas (le relecteur
  compte) et compile contre `terrain-style` v0.6.0. Sur le `Theme.kt` actuel, il ne compile pas,
  faute de `SchemaClair` : c'est l'échec attendu.

## Terrain

- **Échantillon que Pascal vérifiera :** rien sur le terrain ; c'est la 002 qui se voit au soleil.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal (une seule PR avec 002).
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** test rendu, avant la livraison de 002.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Une PR qui change une couleur du thème sans passer par `terrain-style` échoue en test.
