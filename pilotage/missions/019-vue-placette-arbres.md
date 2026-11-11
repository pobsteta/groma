# Mission 019 — vue « Placette » : les arbres sur le plan, fiche de saisie

## Mission

**En une phrase (quoi, et pourquoi) :** afficher sur le plan de la vue « Cibles », renommée
« Placette », les arbres de la placette après la capture (pastilles couleur du groupe d'essence,
grises tant que l'essence n'est pas saisie, comme Marculus) et ouvrir au toucher une fiche où l'on
saisit essence, diamètre au ruban, statut et qualité, parce que Pascal veut tout saisir dans Groma
(décision du 2026-11-11).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | `EcranCibles.kt` (plan existant) ; symbologie de Marculus (`qfield/symbologie.py` : couleurs des groupes d'essence, taille selon le diamètre, étiquette « numéro – diamètre ») |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | 018 (écriture), projets Couchey et Reconfort, S23 Ultra de Pascal |

**Temps de la main :** appuyer — essai de Pascal sur le S23 Ultra.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** changer le calcul des positions ou la capture ; modifier
  `nemeton`, `nemetonshiny`, Marculus.

## Harnais

- **Peut :**
  1. **plan** (`ui/EcranCibles.kt`, renommage libre) : arbres en pastilles (couleur du groupe
     d'essence, gris sans essence, taille selon le diamètre, contour indiquant `dbh_source = mesure`),
     étiquette « numéro – diamètre » si la place le permet, C0-C3 inchangés ; zoom et déplacement si
     besoin pour 30 arbres dans 15 m ; titre « Placette » ; boutons d'accès (liste, carte) renommés ;
  2. **fiche d'arbre** au toucher : numéro, diamètre estimé ; essence (recherche dans BD Forêt v2,
     code et libellé), diamètre au ruban (cm), statut, qualité ; « Enregistrer » écrit par 018 ;
     « arbre suivant » dans l'ordre des numéros, pour enchaîner au ruban ;
  3. **ressources** : textes en français, couleurs des groupes d'essence en couleurs nommées (comme le
     plan 014), pas de couleur en dur hors de ce fichier ; `docs/terrain.md` (étapes 4-5), `PLAN.md`.
- **Ne doit pas :** écrire autrement que par l'API de 018 ; toucher à `main` ; perdre une cible.
- **Sources autorisées :** `android/`, `qfield/symbologie.py`, `~/dev/marculus` (lecture),
  `docs/terrain.md`.

## Valeur provisoire (chiffre, réglage)

- **Taille des pastilles :** proportionnelle au diamètre, bornée (lisible au soleil) ; à juger à l'essai.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 018.
- **Ordre de fusion :** une PR avec 017 et 018.
- **PR en brouillon jusqu'à :** l'essai de Pascal sur le S23 Ultra.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** tests de 017 verts ; `assembleDebug` vert ; test JVM de la
  correspondance essence → groupe → couleur si elle est en `groma-core`.

## Terrain

- **Échantillon que Pascal vérifiera :** une placette filmée : arbres visibles sur le plan, trois
  arbres saisis (essence, ruban, statut, qualité), bouton du plugin dans QField, valeurs reçues.

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (une seule PR avec 017 et 018), puis Pascal.
- **Critère d'arrêt de la relecture :** 3 allers-retours.
- **Point de suivi :** au rendu de l'agent, puis après l'essai.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Sur le terrain, après le film, on saisit essence et ruban arbre par arbre sur le plan de Groma, sans
ouvrir QField pour ces attributs.
