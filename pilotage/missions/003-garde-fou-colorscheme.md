# Mission 003 — garde-fou inverse : le test du thème compte aussi les rôles de `ColorScheme`

## Mission

**En une phrase (quoi, et pourquoi) :** compléter `ThemeTerrainStyleTest` pour qu'il échoue aussi
quand le `ColorScheme` de Material 3 a un rôle de couleur que `Schema` de `terrain-style` n'a pas,
parce qu'une mise à jour du BOM Compose qui ajouterait un rôle le laisserait à sa valeur par défaut
claire, y compris dans le thème sombre, sans qu'aucun test le voie (relecture de 001-002 du 2026-10-24).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | réflexion sur les accesseurs de couleur de `ColorScheme` (en Material 3 1.4.0 : 48 accesseurs `get<Rôle>-<suffixe>` qui rendent un `long`, noms identiques aux 48 champs de `Schema`, vérifié dans l'AAR par le relecteur) |
| A-t-il les moyens ? | oui | test existant `android/app/src/test/kotlin/groma/app/ui/ThemeTerrainStyleTest.kt` sur `main` |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## Contrat

- Un test (dans le même fichier) qui collecte par réflexion les noms des rôles de couleur de
  `androidx.compose.material3.ColorScheme` (accesseurs publics sans paramètre qui rendent la couleur ;
  nom débarrassé du préfixe `get` et du suffixe de mangling Kotlin `-…`) et vérifie qu'ils sont
  exactement l'ensemble des rôles testés. En cas d'écart, le message nomme les rôles en trop et
  en moins, et dit quoi faire (monter `terrain-style` ou demander le rôle à `terrain`).
- Il doit passer sur `main` (Material 3 1.4.0, 48 rôles).
- Preuve qu'il mord : l'agent montre, sans le commiter, qu'en retirant un rôle de la liste
  attendue le test échoue en le nommant.

## L'Homme

- **Agent :** `testeur`
- **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** toucher à `Theme.kt`, à `android/app/src/main/`, ou changer
  de version de Compose.

## Harnais

- **Peut :** modifier `android/app/src/test/kotlin/groma/app/ui/ThemeTerrainStyleTest.kt`, dans sa
  branche partie de `main`.
- **Ne doit pas :** affaiblir les tests existants ; toucher à `main`.
- **Sources autorisées :** le test, `~/dev/terrain/terrain-style/src/main/`, le cache Gradle de
  `material3-android-1.4.0`.

## Tests

- **Comment saura-t-on que c'est juste :** `.claude/test-command` vert ; le test échoue quand on
  retire ou ajoute un rôle à la liste attendue (montré par l'agent).

## Terrain

- Rien : c'est un garde-fou de test.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Une montée du BOM Compose qui ajoute un rôle de couleur fait échouer la CI au lieu de laisser
passer un rôle clair dans le thème sombre. Même angle mort chez Marculus : à lui proposer ensuite.
