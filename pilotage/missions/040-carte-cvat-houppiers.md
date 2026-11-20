# Mission 040 — Relief CVAT, contours et sommets des houppiers sur la carte

## Mission

**En une phrase (quoi, et pourquoi) :** ajouter à la carte de Groma trois couches à cocher —
relief `rasters/cvat.tif` en gris semi-transparent, contours des houppiers, sommets des houppiers —
pour que l'opérateur se repère sur le relief et les arbres dominants, y compris pour une placette
déplacée (décision de Pascal du 2026-11-20 ; pas de MNT ni de MNH affichés).

**Redite par l'agent :** _à compléter par l'agent avant d'agir._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui : `ui/EcranCarte.kt` (menu « Couches », `Case`), `carte/` (couches osmdroid existantes : parcelles, UGF), symbologie des houppiers du brief du 2026-10-16 (contour `#2e7d32`, 0,4 mm, masqué par défaut) | |
| A-t-il les moyens ? | oui après 037 et 038 (exemple 1.7.0) | confier après 038 |

**Temps de la main :** donner. **Degré de la main :** le chef tranche.

## Ma réponse d'abord

_Rempli par Pascal seul._

## L'Homme

- **Agent :** `afficheur`. **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** MNT ou MNH en couche ; calcul du CVAT.

## Ce qui est attendu

1. Couche **Relief CVAT** : `cvat.tif` (Byte, 0 = transparent) en niveaux de gris, opacité réglée
   pour garder le fond lisible ; décochée par défaut ; absente du menu si le projet n'a pas de
   `cvat.tif`.
2. Couche **Houppiers** : contours `#2e7d32`, 0,4 mm ; **Sommets** : un petit point ; décochées par
   défaut ; chargées seulement dans l'emprise visible, par `BETWEEN` sur `x_sommet`/`y_sommet`
   (pas de R*Tree), et seulement au-delà d'un zoom minimal (des dizaines de milliers d'objets).
3. Projet 1.6.0 : houppiers de la table comme aujourd'hui (sans sommets), pas de couche sommets.

## Harnais

- **Peut (périmètre) :** `android/app/src/main/kotlin/groma/app/carte/` (nouvelles couches),
  `ui/EcranCarte.kt` (menu « Couches »), `res/values/strings.xml`, `android/groma-core/src/` (lecture
  de `cvat.tif` en Byte si `GeoTiff` ne la sait pas), tests associés, `docs/terrain.md`, `PLAN.md`,
  journal (ajout seul).
- **Câblé :** pas de `main` ; pas de « terminé » sans tests verts ; journal en ajout seul.
- **Ne doit pas :** bloquer le fil principal au chargement (lecture en tâche de fond) ; changer les
  couleurs existantes.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** opacité du CVAT 50 % ; zoom minimal 17 pour houppiers et sommets.
- **Mesurée par :** essai de Pascal sur le S23 avec le premier projet 1.7.0 de nemeton.
- **Gel :** après cet essai.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 037 et 038 fusionnées. Aucun fichier commun avec 039.
- **CI rouge attendue :** aucune.

## Tests

- Lecture d'un `cvat.tif` Byte (0 → transparent) ; requête des houppiers d'une emprise (bornes
  incluses, projet 1.6.0 sans colonnes) ; menu sans « Relief CVAT » quand le fichier manque.

## Terrain

- Sur le S23, Marsannay 1.7.0 : relief visible sous les placettes, contours et sommets alignés sur
  l'ortho IGN.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Pascal retrouve sur la carte, au chemin de Marsannay, le talus et les gros houppiers qu'il voit sur place.
