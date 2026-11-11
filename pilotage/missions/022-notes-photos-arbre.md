# Mission 022 — notes et photos d'arbre dans la fiche de Groma

## Mission

**En une phrase (quoi, et pourquoi) :** ajouter à la fiche d'arbre de la vue « Placette » une note
(texte libre) et des photos prises avec l'appareil du téléphone, écrites dans `arbres.notes` et
`arbres.photos` de la copie de Groma selon la convention de QField et parties avec l'envoi de terrain,
parce qu'elles ne se saisissaient que dans QField et que Pascal veut s'en passer (décision du 2026-11-11).

**Redite par l'agent :** Ajouter à la fiche d'arbre de la vue « Placette » une note et des photos prises avec le téléphone, écrites dans `arbres.notes` et `arbres.photos` (chemins relatifs au projet, `;` entre plusieurs) de la copie de Groma sans jamais perdre ce que QField ou Groma y ont mis, pour qu'elles partent avec l'envoi de terrain et que QField devienne facultatif.

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | widget photo de QField (chemin relatif au projet dans `photos`) ; `FicheArbre.kt`, `modifierArbre` de 018 ; prise de vue par `ActivityResultContracts.TakePicture` (pas de nouvelle dépendance) |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | 020-021, S23 Ultra de Pascal |

**Temps de la main :** appuyer — essai de Pascal.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** retoucher les photos ; modifier `nemeton` ou `nemetonshiny`.

## Harnais

- **Peut :**
  1. `groma-core` : `SaisieArbre` accepte `notes` (texte, longueur bornée) et `photos` ;
     `modifierArbre` les écrit (règle 5 : une note ou une photo déjà présente, venue de QField ou de
     Groma, n'est jamais perdue sans action explicite) ; tests JVM ;
  2. **fiche** : champ « Note » multiligne ; « Prendre une photo » (appareil, fichier JPEG dans
     `DCIM/` du projet, nom `<plot_id>_<tree_id>_<horodatage>.jpg`), vignettes, suppression avec
     confirmation ; plusieurs photos par arbre selon le format de `photos` (vérifier la convention
     QField/nemeton : un chemin ou plusieurs, séparateur) ;
  3. **envoi** : les photos entrent dans l'archive de terrain de 021 ; une modification relance
     l'envoi ;
  4. **docs** : `docs/terrain.md`, `PLAN.md` ; schéma seulement si la convention de `photos` l'exige
     (le dire, brief nemeton si oui).
- **Ne doit pas :** ajouter de dépendance sans la signaler (`docs/dependances.md`) ; toucher à `main` ;
  modifier les tests de 017 et 020.
- **Sources autorisées :** `android/`, `core/src/groma/donnees/`, `qfield/`, `docs/`.

## Valeur provisoire (chiffre, réglage)

- **Taille des photos :** JPEG de l'appareil réduit à 2048 px de grand côté (envoi en 4G) ; à juger.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 021.
- **Ordre de fusion :** une PR avec 020 et 021.
- **PR en brouillon jusqu'à :** l'essai de Pascal sur le S23 Ultra.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** tests JVM de `notes`/`photos` ; tests de 020 verts ;
  `assembleDebug` vert.

## Terrain

- **Échantillon que Pascal vérifiera :** deux arbres avec une note et une photo chacun, archive de
  terrain contenant les photos.

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (une PR avec 020 et 021), puis Pascal. **Arrêt :** 3 allers-retours.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Sur le terrain, toute la placette (centre, cibles, arbres, notes, photos) se fait dans Groma, sans
ouvrir QField.
