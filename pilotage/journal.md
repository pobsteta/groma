# Journal de Groma

> **Règle du sommier : on n'efface pas, on ajoute.**
> Chaque inscription est datée et signée (agent ou personne). Une correction est une
> nouvelle ligne qui renvoie à l'ancienne. Un hook refuse toute modification qui
> supprimerait ou réécrirait une inscription existante.
>
> Format : `AAAA-MM-JJ · auteur · mission · ce qui a été fait / décidé / bloqué`
>
> **Le carnet des écarts** (fiche n° 3), en une ligne marquée `écart` :
> `AAAA-MM-JJ · auteur · mission · écart · attendu : … · vu : … · vérifié : comment (ou « non ») · décision : …`
> La décision est l'une des trois : **garder** (vérifié : on adopte), **creuser** (prochain
> essai, daté), **abandonner** (vrai hasard : on le note).
> Règle : on ne sanctionne pas un écart signalé ; on regarde d'abord, on corrige ensuite,
> s'il le faut.
>
> **Les changements de cap** (fiche n° 13), en une ligne marquée `cap`, signée par qui l'a
> décidé : `AAAA-MM-JJ · auteur · mission · cap · ancien → nouveau · pourquoi : … · fait : …`

2026-10-23 · Claude (orchestrateur) · — · méthode installée (docs/brief.md, pilotage/, docs/adr/). PLAN.md reste maître pour l'avancement et les décisions antérieures (choix de Pascal). Reste à remplir : docs/brief.md (Pascal) ; rubriques de l'orchestrateur à ajouter au CLAUDE.md (Pascal) ; `.claude/test-command` à écrire au `/brief`.
2026-10-24 · Pascal · 001-002 · issue #57 (style commun) : mode sombre gardé avec `StyleTerrain.sombre` ; `terrain-style` v0.6.0
2026-10-24 · Claude (orchestrateur) · 001-002 · fiches 001 (testeur, test d'égalité 48 rôles × 2) et 002 (dev, thème) écrites sur le modèle de 005-006 de Marculus, cartes au bac à sable · en attente de la porte n° 2 (validation de l'équipe par Pascal)
2026-10-24 · Pascal · — · plafond : 3 PR ouvertes en même temps ; relecture à chaque fin de boucle
2026-10-24 · Pascal · 001-002 · porte n° 2 : équipe validée (« oui aux deux, lance la boucle ») ; `.claude/test-command` = `cd android && gradle :groma-core:test :app:testDebugUnitTest`
2026-10-24 · testeur · 001 · test d'égalité du thème (48 rôles × clair/sombre) avec terrain-style v0.6.0 rendu, rouge à la compilation faute de SchemaClair/SchemaSombre (002) ; Material 3 résolu 1.4.0 ; commit d3de84d
2026-10-24 · Claude (orchestrateur) · 001 · écart · attendu : le testeur relit le test de la 005 de Marculus · vu : fichier introuvable, il n'existe que sur la branche `feat/006-theme-terrain-style` (worktree `theme-006`), la fiche ne donnait pas ce chemin · vérifié : oui, le fichier n'est pas sur `main` de Marculus · décision : garder (test écrit d'après le contrat et `Schema.kt` ; le relecteur comparera) ; à l'avenir, donner le chemin du worktree
2026-10-24 · dev · 002 · thème Groma = 48 jetons terrain-style v0.6.0 (SchemaClair/SchemaSombre), test 001 vert, CI lance :app:testDebugUnitTest ; branche feat/002-theme-terrain-style non poussée, essai soleil 2026-10-30
2026-10-24 · relecteur · 001-002 · à reprendre (documentaire) : code et test prêts (48 rôles × 2 égaux aux jetons, garde-fou par réflexion, test 001 intact, ColorScheme de Material 3 1.4.0 = 48 rôles vérifié dans l'AAR, build et tests verts relancés avec --rerun : 3 + 90 tests, APK construit, CI android-app lance :app:testDebugUnitTest) ; manquent la ligne terrain-style GPL-3.0 dans docs/dependances.md (règle 6) et une ligne PLAN.md (règle 7) ; question à Pascal : garde-fou côté ColorScheme pour les futures mises à jour du BOM
2026-10-24 · Claude (orchestrateur) · 002 · aller-retour 1/3 : renvoyé au dev, fiche complétée (docs/dependances.md, PLAN.md, harnais élargi)
2026-10-24 · dev · 002 · reprise 1/3 faite : terrain-style v0.6.0 déclarée dans docs/dependances.md (règle 6) et étape 7i dans PLAN.md (règle 7), commit 6754810 ; indentation de Theme.kt laissée ; essai au soleil le 2026-10-30
2026-10-24 · relecteur · 001-002 · aller-retour 1/3 clos : 6754810 ajoute terrain-style v0.6.0 (GPL-3.0-or-later, vérifié sur ~/dev/terrain) dans docs/dependances.md:22 et l'étape 7i dans PLAN.md:41 ; android inchangé depuis 2264d37 ; 3 commits conformes · prêt pour Pascal (essai au soleil le 2026-10-30)
2026-10-24 · Claude (orchestrateur) · 001-002 · PR #58 ouverte vers main (Closes #57), en attente de Pascal (porte n° 3) · fin du cycle 1
2026-10-24 · Pascal · 001-002 · porte n° 3 : PR #58 validée (« oui vas-y ») ; livraison du cycle 1 acceptée · fait : squash 6aaf042 sur main (CI verte), issue #57 fermée, brief classé dans ~/dev/briefs/vers-groma/traites/, cartes 001-002 terminées, carte « garde-fou inverse » au bac à sable
2026-10-24 · Pascal · 002 · essai au soleil fait : positif en thème clair et en thème sombre (« l'essai au soleil a eu lieu et est positif en thème clair et sombre »)
2026-10-24 · Pascal · — · release 0.10.0 validée (« merge et déploie ») · fait : PR #54 fusionnée, workflow release vert, groma-0.10.0.apk installé sur le S23 Ultra par `adb install -r` (mise à jour, même signature que la 0.9.0, données gardées)
