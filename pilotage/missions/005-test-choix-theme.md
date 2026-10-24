# Mission 005 — tests du choix du thème (Système, Clair, Sombre)

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, avant le code et sans le lire, les tests JVM de
`groma-core` d'un choix de thème Système / Clair / Sombre, pour que le réglage demandé par Pascal
le 2026-10-24 (« comme dans Marculus ») se comporte exactement comme celui de Marculus.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | modèle : Marculus, commit 042797a (`git -C /home/pascal/dev/marculus show 042797a`, lecture seule) : `ChoixTheme` et `ChoixThemeTest` |
| A-t-il les moyens ? | oui | `groma-core` (Kotlin JVM, `gradle :groma-core:test`) |

**Temps de la main :** appuyer.

## Contrat (que la mission 006 doit fournir)

- Dans `groma-core`, paquet de la logique de l'application (suivre l'idiome du module) :
  `enum class ChoixTheme { SYSTEME, CLAIR, SOMBRE }` avec `fun estSombre(systemeSombre: Boolean): Boolean`
  (SYSTEME → suit le téléphone, CLAIR → false, SOMBRE → true) ;
  `fun choixThemeDepuis(nom: String?): ChoixTheme` : relit le nom enregistré ; absent ou inconnu → `SYSTEME`
  (Groma n'a jamais eu de case « thème sombre » : pas de reprise d'ancien réglage).
- Défaut : `SYSTEME`.

## L'Homme

- **Agent :** `testeur` · **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** l'écran des paramètres, `Theme.kt`, faire passer les tests
  (ils ne compilent pas avant 006 : il le dit).

## Harnais

- **Peut :** créer le fichier de test dans `android/groma-core/src/test/…`, dans sa branche
  `test/005-choix-theme` partie de `origin/main`.
- **Ne doit pas :** lire ni modifier `android/app/src/main/` ni `android/groma-core/src/main/` ;
  toucher à `main` ; faire du git dans Marculus autre que `show`/`log`.

## Tests

Les cas : trois choix × téléphone clair/sombre ; nom enregistré valide, absent, inconnu, casse
différente (dire le comportement retenu, aligné sur Marculus).

## Qui veille, qui tranche

`relecteur` (avec 006, une seule PR), puis Pascal · 3 allers-retours.
