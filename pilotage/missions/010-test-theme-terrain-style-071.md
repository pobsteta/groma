# Mission 010 — tests du thème de Groma contre `terrain-style` v0.7.1 (couleurs, formes, typographie)

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, avant le code, le test JVM qui vérifie que les
`ColorScheme` clair et sombre, les `Shapes` et la `Typography` de Groma sont égaux à ceux de
`terrain-style` v0.7.1, parce que ce module reprend désormais le style de Marculus et qu'il
« s'impose à Groma » (ADR 0006 de terrain, cap de Pascal du 2026-10-24), et qu'il faut savoir
au premier test rouge si Groma s'en écarte. Il absorbe la mission 003 (garde-fou inverse).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | le test existe déjà : `ThemeTerrainStyleTest.kt` de 6aaf042 (mission 001, 48 rôles × 2) et son garde-fou inverse 2d42339 (mission 003, branche `test/003-garde-fou-colorscheme`) |
| A-t-il les moyens ? | oui | `terrain-style` v0.7.1 sur JitPack (pom vérifié, HTTP 200), sources dans `~/dev/terrain/terrain-style/src/main/` |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## Décisions de Pascal (2026-10-24)

- `terrain-style` reprend le style de Marculus et s'impose à Groma (ADR 0006 de terrain,
  nuancier validé : « oui c'est le vert de Marculus »).
- Mode sombre gardé ; choix du thème (Système, Clair, Sombre) et plein écran inchangés.

## Contrat

Dans `android/app/src/main/kotlin/groma/app/ui/Theme.kt` (écrit par la mission 011) :

- `internal val SchemaClair: ColorScheme` et `internal val SchemaSombre: ColorScheme`, égaux rôle
  par rôle (48 rôles) à `StyleTerrain.clair` et `StyleTerrain.sombre` ;
- `internal val FormesGroma: Shapes`, coins `RoundedCornerShape(x.dp)` égaux à `FormesTerrain`
  (`extraSmall` 4, `small` 8, `medium` 12, `large` 16, `extraLarge` 28) ;
- `internal val TypoGroma: Typography`, 15 styles dont `fontSize`, `lineHeight`, `fontWeight`
  et `letterSpacing` égaux à ceux de `TypoTerrain` (`StyleTexte` : taille sp, interligne sp,
  graisse, espacement sp — vérifier les noms des champs dans `StyleTexte.kt`).

Le test :

1. **Couleurs** : reprendre `ThemeTerrainStyleTest.kt` de 6aaf042 (`git show
   6aaf042:android/app/src/test/kotlin/groma/app/ui/ThemeTerrainStyleTest.kt`) avec le
   garde-fou inverse de 2d42339 (`git show 2d42339`) : égalité rôle par rôle, garde-fou par
   réflexion sur les champs de `Schema`, et sur les accesseurs de `ColorScheme`, échec qui nomme
   tous les rôles fautifs.
2. **Formes** : égalité des 5 rayons ; un message qui nomme la forme fautive.
3. **Typographie** : égalité des 15 styles × 4 valeurs ; un message qui nomme le style et la
   valeur fautifs ; garde-fou : les 15 noms couvrent tous les champs de `TypoTerrain`.
4. Il doit échouer sur `main` aujourd'hui seulement parce que le contrat n'existe pas encore
   (compilation) ; montrer, sans le commiter, qu'en changeant une valeur attendue le test
   échoue en la nommant.

Les boutons (`BoutonsTerrain`) ne sont pas testés : Groma ne règle aucun bouton, ses valeurs sont
celles de Material 3 1.4.0, comme `BoutonsTerrain`. Le dire dans le commentaire du test.

## L'Homme

- **Agent :** `testeur`
- **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** lire ou écrire `android/app/src/main/` (sauf pour constater
  qu'un nom n'existe pas), ajouter la dépendance (mission 011), changer de version de Compose.

## Harnais

- **Peut :** créer `android/app/src/test/kotlin/groma/app/ui/ThemeTerrainStyleTest.kt`, dans une
  branche `feat/theme-terrain-style-071` partie de `origin/main`. Pour le compiler localement,
  il peut ajouter **sans le commiter** `implementation("com.github.pobsteta.terrain:terrain-style:v0.7.1")`
  et le dépôt JitPack (comme dans 6aaf042) ; c'est la mission 011 qui les commite.
- **Ne doit pas :** toucher à `main`, au code de production, ni affaiblir les tests existants.
- **Sources autorisées :** 6aaf042, 2d42339, `~/dev/terrain/terrain-style/src/main/`,
  `~/dev/terrain/docs/adr/0006-style-de-marculus.md`, le cache Gradle de `material3-android-1.4.0`.

## Tests

- **Comment saura-t-on que c'est juste :** les valeurs attendues viennent de `terrain-style`
  lui-même (jamais recopiées à la main) ; le test mord (montré par l'agent).

## Terrain

- Rien : c'est un test.

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (avec la 011, une seule PR), puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Si Marculus change son style puis `terrain-style` le suit, la montée de version dans Groma
échoue en nommant chaque rôle, forme ou style à recopier.
