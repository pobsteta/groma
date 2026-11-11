# Mission 016 — vue « Cibles » : placer C1-C3 dans Groma, plus dans QField

## Mission

**En une phrase (quoi, et pourquoi) :** ajouter à Groma une vue « Cibles » où, une fois le centre
mesuré (C0 sur la canne), on place C1, C2 et C3 sur un plan de la placette à partir de l'azimut (boussole
de visée tapée, ou boussole du téléphone) et de la distance au télémètre, avec le diamètre au ruban,
parce que les cibles se saisissent aujourd'hui dans QField, hors de la vue de la placette, alors que
leur azimut oriente toute la placette ; désormais **tout se fait dans Groma** et QField ne saisit plus
les cibles (décision de Pascal du 2026-11-11, sur le modèle de la vue Pointer de Surculus).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | vue Pointer de Surculus (même auteur, GPL-3.0) ; écriture dans le GeoPackage déjà faite pour `centres_mesures` (déclencheurs R*Tree) ; tests de 015 |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | tests de 015, projets Couchey et Reconfort, S23 Ultra de Pascal pour l'essai |

**Temps de la main :** appuyer — la saisie existe déjà (QField), elle change de lieu ; Pascal essaie
la vue sur le terrain avant la fusion.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** télémètre ou boussole Bluetooth (étape ultérieure) ; changer
  le géoréférencement du serveur ; modifier `~/dev/surculus`, `nemeton` ou `nemetonshiny`.

## Harnais

- **Peut :**
  1. **calcul** dans `groma-core` (`PlacementCible`, API fixée par 015) ;
  2. **vue** dans `android/app` : ouverte depuis la feuille de placette (après « Mesurer le centre »)
     et avant de filmer ; plan à l'échelle, nord en haut, cercle du rayon, C0 au centre (dernier
     centre mesuré, sinon centre prévu avec une mention) ; choix de C1, C2 ou C3 ; azimut tapé dans
     l'unité de la placette (**par défaut**, boussole de visée) ou lu en direct à la boussole du
     téléphone (avec l'état d'étalonnage du capteur, repris de `Boussole.kt` / `BoussoleTelephone.kt`
     de Surculus) ; distance horizontale et diamètre tapés ; « Placer » ; alertes de répartition de 015 ;
  3. **écriture** dans la couche `cibles` du GeoPackage du projet (une ligne par `plot_id` et
     `id_cible`, remplacée si on replace la cible, géométrie Lambert 93 calculée), comme
     `centres_mesures` ; la capture reprend ces cibles comme aujourd'hui (`Projet.kt:71`) ;
  4. **schéma** (`core/src/groma/donnees/inventaire_video.schema.json`, seule source) : colonne
     `source_azimut` (domaine `boussole_visee`, `boussole_telephone`), `cibles` marquée lecture seule
     pour QField, version mineure suivante ; tests Python et Kotlin du schéma ajustés ;
  5. **brief nemeton** (`briefs/vers-nemeton/AAAA-MM-JJ-cibles-saisies-dans-groma.md`, copie déposée
     dans `/home/pascal/dev/briefs/vers-nemeton/`) : la couche `cibles` du projet QField devient
     lecture seule (plus de formulaire de saisie), colonne `source_azimut` ; projets déjà générés :
     la couche reste éditable dans QField mais Groma ne lit que ce qu'il a écrit lui-même (à dire dans
     `terrain.md`) ;
  6. **docs** : `docs/terrain.md` (Matériel, étape 2 du protocole : saisie dans Groma, boussole de
     visée recommandée, répartition à 120°, 8 à 15 m) ; `PLAN.md`.
- **Ne doit pas :** modifier les tests de 015 sans le dire ; toucher à `main` ; écrire dans la copie
  QField du projet ; perdre une cible déjà saisie (une cible lue d'un projet existant reste affichée
  et peut être replacée).
- **Sources autorisées :** tout `android/`, `core/src/groma/donnees/`, `docs/terrain.md`,
  `~/dev/surculus/app/src/main/kotlin/io/github/pobsteta/surculus/controle/` et `docs/spec.md`
  (lecture), `briefs/vers-nemeton/` (modèles de brief).

## Valeur provisoire (chiffre, réglage)

- **Celles de 015** (écart angulaire 60°, distance minimale 8 m).
- **Azimut par défaut :** boussole de visée tapée ; boussole du téléphone en option.
- **Mesurée par :** résidu d'orientation des premières placettes test, selon la source de l'azimut.
- **Gel :** après trois placettes test.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 015 (tests).
- **Ordre de fusion :** une PR avec 015 ; le brief nemeton peut être traité après la fusion (Groma
  n'en dépend pas pour fonctionner).
- **PR en brouillon jusqu'à :** l'essai de Pascal sur le S23 Ultra.
- **CI rouge attendue :** aucune à la fin de la mission.

## Tests

- **Comment saura-t-on que c'est juste :** tests de 015 verts ; test d'écriture de `cibles` dans un
  GeoPackage d'essai (ligne remplacée, R*Tree à jour, `source_azimut` écrite) ; `pytest core server`
  et `gradle :groma-core:test` verts ; APK de debug compilé en CI.

## Terrain

- **Échantillon que Pascal vérifiera :** une placette de Couchey ou Reconfort : centre mesuré, trois
  cibles placées dans Groma (une à la boussole du téléphone, deux à la boussole de visée), position
  sur le plan, capture, résidu d'orientation dans le rapport du serveur.

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (avec 015, une seule PR), puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** au rendu de l'agent, puis après l'essai sur le terrain.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Sur le terrain, on place les trois cibles sans ouvrir QField, la capture les reprend et le résidu
d'orientation reste sous 2°.
