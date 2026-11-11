# Mission 018 — saisie des arbres dans Groma : données, priorité du ruban, recopie vers QField

## Mission

**En une phrase (quoi, et pourquoi) :** permettre à Groma d'écrire l'essence, le diamètre au ruban,
le statut et la qualité d'un arbre dans sa copie du GeoPackage, avec un diamètre mesuré jamais écrasé
(`dbh_source`), et de les passer à QField par le serveur local et le plugin sans écraser une saisie
de QField, parce que Pascal veut tout saisir dans Groma (décision du 2026-11-11) sans rien perdre de
ce qui est saisi sur le terrain (règle 5).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | modèle `h_source = mesuree` ; écriture Groma en transaction courte (`GeoPackage.ajouterCentreMesure`, `ecrireCible`) ; plugin `qfield/inventaire_video.qml` |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | tests de 017 |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** la vue (019) ; modifier `nemeton` ou `nemetonshiny`.

## Décisions de Pascal (2026-11-11)

Voir la fiche 017 : Groma saisit, QField reçoit sans écrasement ; essence, diamètre au ruban, statut,
qualité ; diamètre au ruban prioritaire (`dbh_source`).

## Harnais

- **Peut :**
  1. **schéma** (`core/src/groma/donnees/inventaire_video.schema.json`, seule source) : colonne
     `dbh_source` de `arbres` (domaine `dbh_source` : `estime`, `mesure` ; défaut `estime`), version
     mineure suivante ; exemples régénérés (`exemples/creer_exemple.py`) ;
  2. **déclencheurs** (`declencheurs.sql`) : `dbh_source = 'estime'` à la création ; mise à jour d'une
     détection sans effet sur `dbh_cm` si `dbh_source = 'mesure'` ; l'en-tête dit désormais que Groma
     écrit aussi les attributs saisis dans **sa** copie de `arbres` ; Kotlin et Python appliquent le
     même fichier ;
  3. **écriture Groma** (`groma-core`) : `SaisieArbre` (contrôles) et l'écriture dans `arbres`, en
     transaction courte, seules les colonnes saisies ; ajout de la colonne si absente (projets déjà
     générés, comme `source_azimut`) ; `Projet.kt` côté application ;
  4. **rapprochement** (`core/src/groma/rapprochement.py`) : `dbh_source = mesure` garde son diamètre ;
     attributs conservés ; ce que nemeton relit est décrit dans le brief ;
  5. **serveur local** (`ServeurLocal.kt`) : `/inventaire/<projet_id>` renvoie aussi `arbres` (par
     `uuid` : `essence_arbre`, `dbh_cm`, `dbh_source`, `statut`, `qualite`) ;
  6. **plugin QField** (`qfield/inventaire_video.qml`) : après l'ajout des détections, recopie les
     attributs de Groma dans `arbres` de QField, par `uuid`, seulement là où QField est vide (`statut` :
     vide ou `vivant`) ; une édition validée par couche ; rien si la couche est en cours d'édition ;
     bilan affiché (« n attributs recopiés ») ;
  7. **brief nemeton** (`briefs/vers-nemeton/2026-11-11-arbres-saisis-dans-groma.md`, copie déposée
     dans `/home/pascal/dev/briefs/vers-nemeton/`) : colonne `dbh_source`, priorité du ruban, attributs
     pouvant venir de Groma ; **docs** : `docs/terrain.md` (étape 5, échange Groma-QField), `PLAN.md`.
- **Ne doit pas :** modifier les tests de 017 sans le dire ; toucher à `main` ; écrire dans la copie
  QField autrement que par le plugin ; écraser une saisie QField ; toucher à `photos` et `notes`.
- **Sources autorisées :** tout `core/`, `android/`, `qfield/`, `docs/`, `briefs/vers-nemeton/`.

## Valeur provisoire (chiffre, réglage)

- Aucune nouvelle.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 017.
- **Ordre de fusion :** une PR avec 017 et 019 ; le brief nemeton après la fusion.
- **PR en brouillon jusqu'à :** la fin de 019.
- **CI rouge attendue :** aucune à la fin de la mission.

## Tests

- **Comment saura-t-on que c'est juste :** tests de 017 verts ; `pytest core server benchmark`,
  `gradle :groma-core:test` verts.

## Terrain

- **Échantillon que Pascal vérifiera :** voir 019.

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (une seule PR avec 017 et 019), puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** au rendu de l'agent.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Un diamètre au ruban saisi dans Groma survit à une nouvelle capture et au calcul définitif, et
arrive dans QField au bouton du plugin sans écraser ce qui y avait été saisi.
