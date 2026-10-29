# Mission 008 — vue Kanban des projets, colonnes de Marculus et Surculus

> Fiche écrite par l'orchestrateur le 2026-10-29. Demandée par Pascal le 2026-10-29 :
> « pourquoi groma n'a pas la vue kanban ? », puis « les mêmes colonnes que marculus et
> surculus ».

## Mission

**En une phrase (quoi, et pourquoi) :** ajouter à la liste des projets de Groma une vue Kanban à
cinq colonnes, celles de Marculus et Surculus (Proposée, Validée, Planifiée, Réalisée,
Abandonnée), avec glisser-déposer des cartes et un statut minimal imposé par l'avancement des
placettes, pour que le forestier voie d'un coup d'œil où en est chacun de ses projets, comme dans
Marculus et Surculus.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Vérifié à la source

Constats vérifiés par l'orchestrateur le 2026-10-29.

| Constat | Lu à | Source du constat |
|---|---|---|
| Colonnes, dans cet ordre (ordre d'affichage et de comparaison) : Proposée, Validée, Planifiée, Réalisée, Abandonnée | `surculus` `app/src/main/kotlin/io/github/pobsteta/surculus/tournee/EtatKanban.kt` l. 19-31 (origin/main) ; `marculus` `res/values/strings.xml` l. 270-274 | orchestrateur |
| Logique pure à recopier : `depuisNom` (absent ou inconnu → Proposée), `statutMinimal`, `colonneAffichee`, `deposer` (refusé sous le minimum), `repartir` | `surculus` `tournee/EtatKanban.kt` l. 27-66, tests `app/src/test/kotlin/.../tournee/EtatKanbanTest.kt` | orchestrateur |
| Vue : une colonne par état (260 dp), appui long puis glissé pour changer de colonne, appui court pour ouvrir | `surculus` `tournee/VueKanban.kt` l. 60-110 | orchestrateur |
| Activation : bouton liste/colonnes toujours visible dans la barre, sans case dans Paramètres (Surculus, mission 050) ; icônes `ViewColumn` / `ViewList` de Marculus | `surculus` `pilotage/missions/050-sans-case-kanban.md` et `043-kanban-tournees.md` | orchestrateur |
| Groma : les cartes de la liste sont les projets ; leur avancement est `ResumeProjet` (`faites`, `envoyees`, `nonEnvoyees`, `termine`) | `android/groma-core/src/main/kotlin/groma/core/ResumeProjet.kt` l. 9-30 ; `ui/EcranProjets.kt` l. 219 | orchestrateur |
| Groma a déjà une recherche et une puce « Terminés » au-dessus de la liste | `ui/EcranProjets.kt` ; `res/values/strings.xml` l. 54-56 | orchestrateur |
| Groma n'a aucune vue Kanban | recherche de « kanban » dans `android/` | orchestrateur |

## Réponses de Pascal (2026-10-29)

1. **Colonnes** : celles de Marculus et Surculus.
2. **Minimum imposé par l'avancement** : au moins une placette faite (`faites > 0`) → au moins
   Planifiée ; toutes les placettes de base faites et toutes envoyées (`termine` et
   `nonEnvoyees == 0`) → au moins Réalisée ; sinon Proposée.
3. **Activation et stockage, comme Surculus aujourd'hui** : bouton liste/colonnes toujours visible
   dans la barre de la liste des projets, pas de case dans Paramètres ; le statut choisi à la main
   est gardé sur le téléphone seulement (SharedPreferences, par identifiant de projet), hors du
   GeoPackage et hors du serveur. Il est perdu à la réinstallation ; c'est accepté.

Règles, avec leur auteur :
- **Les colonnes de Marculus et Surculus** : Pascal, 2026-10-29.
- **Rien dans le GeoPackage** (son schéma n'a qu'une source, `CLAUDE.md` règle 3) : orchestrateur,
  d'après la réponse 3.
- **La vue Kanban montre les mêmes projets que la liste** (recherche et puce « Terminés »
  appliquées) : orchestrateur, à confirmer par Pascal à la relecture.
- **L'écran sert le forestier en forêt** : cibles d'au moins 48 dp, une main.

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | modèle Surculus (lecture seule) : `git -C /home/pascal/dev/surculus show origin/main:<chemin>` pour `EtatKanban.kt`, `VueKanban.kt`, `EtatKanbanTest.kt`, `EcranTournees.kt` |
| A-t-il les moyens ? | à vérifier | les icônes `ViewColumn` / `ViewList` demandent `material-icons-extended` : si Groma ne l'a pas, le dire avant de l'ajouter (Apache-2.0, `docs/dependances.md`) |

**Temps de la main :** appuyer.

## Contrat

- `groma-core` : `EtatKanban` (cinq états, `depuisNom`), `statutMinimal(resume: ResumeProjet)`,
  `colonneAffichee`, `deposer`, `repartir`, avec leurs tests JVM (dont : minimum Planifiée dès une
  placette faite, Réalisée seulement si tout est fait **et** envoyé, dépôt refusé sous le minimum).
- `app` : bouton liste/colonnes dans `BandeauCompact` de la liste des projets ; choix de la vue
  gardé (SharedPreferences `groma`) ; vue colonnes à défilement horizontal ; appui court ouvre le
  projet comme dans la liste, appui long puis glissé change de colonne.
- `PLAN.md` : une ligne.

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** envoyer le statut au serveur ou l'écrire dans le GeoPackage ;
  toucher au plein écran (mission 009) ; changer la liste ou la recherche existantes.

## Harnais

- **Peut :** `groma-core` (nouveau fichier et son test), `ui/EcranProjets.kt`, un nouveau
  `ui/VueKanban.kt`, `MainActivity.kt` (préférences), `res/values/strings.xml`, `PLAN.md`,
  `docs/dependances.md` si une dépendance est autorisée ; branche `feat/008-vue-kanban-projets`
  partie de `origin/main`.
- **Ne doit pas :** toucher à `main` ; modifier `fixtures/` ou le schéma du GeoPackage ; modifier
  les tests existants.

## Tests

`.claude/test-command` vert, `gradle :groma-core:test` et `:app:assembleDebug` verts.

## Terrain

Pascal, S23 Ultra, en paysage : le bouton bascule liste ↔ colonnes ; un projet avec une placette
faite est au moins en Planifiée ; glisser une carte vers Validée ou Abandonnée la déplace, vers une
colonne sous le minimum la laisse en place ; le choix survit à une relance.

## Qui veille, qui tranche

`relecteur`, puis Pascal · 3 allers-retours · Pascal tranche.
