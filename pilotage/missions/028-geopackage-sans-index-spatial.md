# Mission 028 — GeoPackages du téléphone sans index spatial

## Mission

**En une phrase (quoi, et pourquoi) :** préparer `centres_mesures` et `cibles` sans index spatial
R*Tree et faire que Groma écrive sans erreur dans un projet ancien qui en a un, parce que le SQLite
d'Android n'a pas le module `rtree` : sur le S23 Ultra, mesurer le centre, placer une cible et
l'envoi de terrain échouent (« no such module: rtree », essai de fumée du 2026-11-12, 5/11 rouges ;
les 3 projets du téléphone ont l'index et aucun centre). Choix de Pascal du 2026-11-12 : pas d'index
sur le téléphone, le serveur le recrée s'il en a besoin.

**Redite par l'agent :** _à remplir par l'agent._

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Redire en une phrase ? | oui | |
| Sait-il comment faire ? | oui | `index_spatial` du schéma (lignes ~56 et ~75), `gpkg._ecrire` (`SPATIAL_INDEX`), `GeoPackage.ajouterCentreMesure` / `ecrireCible`, `ExportTerrain` (DELETE l. ~57) |
| A-t-il les moyens ? | oui | essai de fumée (`feat/026-essai-fumee`), S23 Ultra branché |

**Temps de la main :** appuyer.

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet

## Harnais

- **Peut :**
  1. schéma : `index_spatial: false` pour `centres_mesures` et `cibles` (version 1.6.0 inchangée,
     non publiée ; remarque) ; vérifier qu'aucune autre table écrite par le téléphone n'a d'index ;
  2. groma-core : pour un projet ancien qui a `rtree_centres_mesures_geom` / `rtree_cibles_geom`,
     retirer l'index proprement à la première écriture (déclencheurs `rtree_*`, table virtuelle et
     ses tables `_node/_rowid/_parent`, ligne de `gpkg_extensions`), sans module rtree (le DROP TABLE
     d'une table virtuelle exige le module : passer par `PRAGMA writable_schema` si nécessaire, dans
     une transaction, puis `PRAGMA integrity_check`), sans perdre une ligne (règle 5) ; même chose
     avant les DELETE d'`ExportTerrain` ; tests JVM (base avec index factice) ;
  3. serveur / Python : à la réception d'un envoi de terrain, rien ne doit exiger l'index ;
     le recréer seulement si un traitement en a besoin ;
  4. ADR courte dans `docs/` (là où sont les décisions existantes) ; brief vers nemeton seulement
     si nemeton lit l'index (sinon le dire) ; `docs/dependances.md` inchangé ;
  5. ligne de fin dans `pilotage/journal.md` (`>>`).
- **Ne peut pas :** installer un APK `fr.nemeton.groma` ; désinstaller ; `pm clear` ; lire
  `~/.config/groma/` ; toucher `nemeton`/`nemetonshiny` ; changer une version.
- **Fini quand :** `gradle :groma-core:test :app:testDebugUnitTest :app:assembleDebug`,
  `pytest core server benchmark` verts ; commit poussé sur `feat/024-centre-telephone-ajout-complet`.
