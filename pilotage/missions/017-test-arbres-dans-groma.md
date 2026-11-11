# Mission 017 — tests de la saisie des arbres dans Groma (essence, diamètre au ruban, statut, qualité)

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, avant le code, les tests qui fixent la saisie des
attributs d'un arbre dans Groma après la capture (essence, diamètre au ruban, statut, qualité), sa
priorité sur les mesures automatiques (déclencheurs, rapprochement définitif) et sa transmission à
QField par le serveur local, parce que Pascal veut tout saisir dans Groma, sur le plan de la placette
(décision du 2026-11-11), sans jamais perdre un attribut saisi sur le terrain (règle 5).

**Redite par l'agent :** écrire, avant tout code, les tests (Python, Kotlin `groma-core`) qui fixent la saisie dans Groma de l'essence, du diamètre au ruban (`dbh_source`, jamais écrasé), du statut et de la qualité, leur conservation par les déclencheurs et le rapprochement, leur envoi à QField par le serveur local et la règle de recopie sans écrasement, pour que 018-019 les fassent passer sans les modifier.

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | `h_source = mesuree` est déjà le modèle d'une mesure qui n'est jamais écrasée (déclencheurs, `rapprochement.py`) ; tests existants `core/tests/test_gpkg.py`, `test_rapprochement*.py`, `GeoPackageTest` |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | GeoPackage d'exemple `exemples/inventaire_video/`, `essences_bdforet_v2.json` |

**Temps de la main :** appuyer — tests seuls.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `testeur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** écrire le code (018, 019) ; modifier le schéma ; modifier
  `nemeton` ou `nemetonshiny`.

## Décisions de Pascal (2026-11-11)

1. **Groma saisit, QField reçoit** : on saisit dans Groma ; le bouton du plugin QField recopie les
   attributs dans la copie de QField **sans écraser une valeur déjà saisie dans QField** ; QField garde
   photos et notes.
2. **Attributs saisis dans Groma :** `essence_arbre` (BD Forêt v2), `dbh_cm` au ruban, `statut`, `qualite`.
3. **Diamètre au ruban prioritaire :** nouvelle colonne `dbh_source` (`estime`, `mesure`), sur le
   modèle de `h_source` ; un diamètre mesuré n'est jamais écrasé.
4. **Vue :** sur le plan de la vue « Cibles », qui devient « Placette » (mission 019).

## Harnais

- **Peut :** écrire des tests (rouges jusqu'à 018) :
  1. **Kotlin, `groma-core`** — un futur `groma.core.SaisieArbre` (API décrite en tête du test) :
     contrôle des valeurs (essence dans `essences_bdforet_v2`, ou vide ; diamètre dans les bornes du
     schéma ; statut et qualité dans leurs domaines) ; écriture dans `arbres` d'un GeoPackage d'essai
     (`GeoPackage.modifierArbre` ou nom fixé en tête du test) : seules les colonnes saisies changent,
     `dbh_source = mesure` quand le diamètre est saisi, une valeur vide efface la saisie de cet
     attribut seulement ;
  2. **déclencheurs** (Python `core/tests/test_gpkg.py` et Kotlin `GeoPackageTest`) : une détection
     mise à jour ne change pas `dbh_cm` d'un arbre `dbh_source = mesure`, ni ses attributs saisis ;
     sans saisie, comportement inchangé (`dbh_source = estime` à la création) ;
  3. **rapprochement** (Python, `core/tests/`) : un arbre `dbh_source = mesure` garde son diamètre
     après `rapprocher` (l'alerte `ecart_dbh` reste émise) ; `essence_arbre`, `statut`, `qualite` saisis
     dans Groma sont conservés (règle 5, test dédié étendu) ;
  4. **serveur local** (Kotlin, ou JVM sur `ServeurLocal` si testable) : `/inventaire/<projet_id>`
     renvoie aussi, par `uuid`, les attributs saisis dans Groma (`essence_arbre`, `dbh_cm`,
     `dbh_source`, `statut`, `qualite`) ;
  5. **règle du plugin QField**, écrite comme fonction pure testable (JS ou Kotlin, à fixer avec 018) :
     pour chaque arbre, un attribut n'est recopié dans QField que si la valeur de QField est vide
     (pour `statut`, vide ou `vivant` par défaut) ; jamais d'écrasement d'une saisie QField.
- **Ne doit pas :** lire ou modifier le code de 018 et 019 ; toucher à `main` ; modifier le schéma
  (l'agent 018 le fait) ; modifier les tests de numérotation.
- **Sources autorisées :** `core/`, `android/groma-core/`, `android/app/src/main/kotlin/groma/app/ServeurLocal.kt`,
  `qfield/inventaire_video.qml`, `docs/terrain.md` (lecture), `core/src/groma/donnees/` (lecture).

## Valeur provisoire (chiffre, réglage)

- Aucune nouvelle : alerte `ecart_dbh` inchangée (10 cm et 50 %).

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de la branche `feat/017-arbres-dans-groma` (fiches).
- **Ordre de fusion :** une PR avec 018 et 019.
- **PR en brouillon jusqu'à :** la fin de 019.
- **CI rouge attendue :** oui, jusqu'à la fin de 018.

## Tests

- **Comment saura-t-on que c'est juste :** chaque décision ci-dessus a au moins une assertion ; les
  valeurs attendues sont écrites à la main, jamais tirées de l'implémentation.

## Terrain

- **Échantillon que Pascal vérifiera :** aucun pour cette mission (voir 019).

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (avec 018 et 019, une seule PR), puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** au rendu de l'agent.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Les tests décrivent sans ambiguïté la priorité des saisies de Groma et la règle de recopie vers
QField ; 018 les fait passer sans les modifier.
