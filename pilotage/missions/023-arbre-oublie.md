# Mission 023 — ajouter dans Groma un arbre oublié par la vidéo

## Mission

**En une phrase (quoi, et pourquoi) :** permettre, depuis la vue « Placette », d'ajouter un arbre que
la vidéo n'a pas détecté (azimut et distance depuis le centre, diamètre au ruban obligatoire, essence,
statut, qualité), parce que le diamètre est désormais en lecture seule dans QField et que Pascal veut
ajouter les arbres oubliés dans Groma (décision du 2026-11-11).

**Redite par l'agent :** dans la vue « Placette », ajouter un arbre que la vidéo a oublié, placé par son
azimut et sa distance depuis le centre mesuré, avec son diamètre au ruban obligatoire (essence, statut,
qualité facultatifs), écrit directement dans `arbres` (`methode = terrain`, numéro `A<n>`), et supprimable
seulement s'il a été ajouté à la main, pour que Pascal puisse compléter l'inventaire dans Groma puisque le
diamètre est en lecture seule dans QField.

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | placement des cibles (`PlacementCible.position`, saisie azimut et distance de la vue) ; `FicheArbre` ; `modifierArbre` |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | tests de 020 (point 5) |

**Temps de la main :** appuyer — essai de Pascal.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** changer la numérotation définitive (règle 4) ; modifier `nemeton`.

## Harnais

- **Peut :**
  1. **schéma** : valeur `terrain` du domaine `methode` (version mineure suivante), exemples
     régénérés, brief nemeton (ajout au brief de 021 ou brief propre) ;
  2. `groma-core` : `ajouterArbre` (API de 020), écriture directe dans `arbres` en transaction courte
     (pas par `detections`), numéro provisoire `A<n>` ;
  3. `rapprochement.py` : comportement fixé par 020 (apparié : ruban et saisies gardés ; non apparié :
     `a_verifier`, conservé) ;
  4. **vue** : bouton « Ajouter un arbre » dans la vue « Placette » ; saisie azimut (unité de la
     placette, boussole de visée ou du téléphone comme pour les cibles) et distance, aperçu sur le
     plan, puis la fiche (ruban obligatoire) ; pastille distincte (ex. contour pointillé) pour un arbre
     ajouté ; suppression possible d'un arbre ajouté par erreur (seulement `methode = terrain`) ;
  5. **docs** : `docs/terrain.md`, `PLAN.md`.
- **Ne doit pas :** modifier les tests de 020 sans le dire ; toucher à `main` ; supprimer un arbre
  venu de la vidéo.
- **Sources autorisées :** `android/`, `core/`, `docs/`, `briefs/vers-nemeton/`.

## Valeur provisoire (chiffre, réglage)

- Aucune.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 020 (tests), 021 (l'arbre ajouté part avec l'archive de terrain).
- **Ordre de fusion :** une PR avec 020-022.
- **PR en brouillon jusqu'à :** l'essai de Pascal.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** tests de 020 (point 5) verts ; `pytest`, `gradle` verts.

## Terrain

- **Échantillon que Pascal vérifiera :** un arbre ajouté à la main sur une placette filmée, à sa place
  sur le plan, présent dans l'archive de terrain.

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (une PR avec 020-022), puis Pascal. **Arrêt :** 3 allers-retours.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Un arbre oublié par la vidéo s'ajoute en quelques secondes dans Groma et arrive à nemetonshiny avec
son diamètre au ruban.
