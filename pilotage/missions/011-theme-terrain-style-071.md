# Mission 011 — thème de Groma = `terrain-style` v0.7.1

## Mission

**En une phrase (quoi, et pourquoi) :** faire que `Theme.kt` prenne ses couleurs (48 rôles,
clair et sombre), ses formes et sa typographie dans `terrain-style` v0.7.1 au lieu de les écrire
à la main, parce que ce module porte désormais le style de Marculus et qu'il s'impose à Groma
(ADR 0006 de terrain, cap de Pascal du 2026-10-24) ; à l'écran, rien ne doit changer : la
v0.7.1 vaut exactement la palette actuelle de Groma (VertVif, VertMarculus, Creme, défauts
Material 3 1.4.0).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | déjà fait pour v0.6.0 (6aaf042, `versColorScheme(Schema)`) ; formes et typographie : recopie de nombres en `dp`/`sp` |
| A-t-il les moyens ? | oui | `terrain-style` v0.7.1 sur JitPack ; test de la mission 010 dans la même branche |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## Décisions de Pascal (2026-10-24)

- `terrain-style` reprend le style de Marculus et s'impose à Groma (ADR 0006 de terrain).
- Mode sombre, choix du thème (`ChoixTheme`, `GromaApp.appliquerTheme`) et plein écran gardés tels quels.
- Une seule PR avec la 010.

## Contrat (fixé par la mission 010)

`internal val SchemaClair`, `SchemaSombre` (`ColorScheme`), `FormesGroma` (`Shapes`),
`TypoGroma` (`Typography`) dans `Theme.kt`, utilisés par `GromaTheme`
(`MaterialTheme(colorScheme = …, shapes = FormesGroma, typography = TypoGroma)`). Une seule
fonction `versColorScheme(Schema)` pour clair et sombre (comme 6aaf042) et une fonction
`StyleTexte → TextStyle` pour les 15 styles. Chargeables sur la JVM sans Robolectric.
Signature de `GromaTheme(darkTheme, content)` inchangée.

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet.
- **`testeur` associé :** mission 010 (le test précède le code).
- **Ce qui ne lui est pas demandé :** toucher aux 19 `Color(0x…)` écrites en dur dans les écrans
  (carte à part), utiliser `reussi` / `alerte`, régler les boutons, changer de version de Compose.

## Harnais

- **Peut :** modifier `Theme.kt`, `android/app/build.gradle.kts` (`terrain-style:v0.7.1` en
  `implementation`), `android/settings.gradle.kts` (dépôt JitPack limité au groupe
  `com.github.pobsteta.terrain`, comme 6aaf042), `docs/dependances.md` (règle 6 : ligne
  `terrain-style` v0.7.1, GPL-3.0-or-later, même auteur, Kotlin pur), `PLAN.md` (règle 7 : une
  ligne 7n), dans la branche de la 010.
- **Ne doit pas :** modifier le test de la 010 (s'il le croit faux, il le dit) ; toucher à `main`.
- **Sources autorisées :** `android/app/src/main/kotlin/groma/app/ui/`, 6aaf042 et 6aaf042^,
  `~/dev/terrain/terrain-style/src/main/`, `~/dev/terrain/docs/adr/0006-style-de-marculus.md`.

## Tests

- **Comment saura-t-on que c'est juste :** le test de la 010 passe ; `cd android && gradle
  :groma-core:test :app:testDebugUnitTest :app:assembleDebug` passe ; la CI de la PR est verte.

## Terrain

- **Échantillon que Pascal vérifiera :** l'APK sur le S23 Ultra : bandeau vert, Paramètres,
  capture, en clair et en sombre — identique à 0.12.0.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Groma a le même vert que Marculus, et le jour où Marculus change de style, Groma le suit par une
montée de version de `terrain-style` que le test guide.
