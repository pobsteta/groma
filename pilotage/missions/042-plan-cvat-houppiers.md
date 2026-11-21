# Mission 042 — Relief CVAT, contours et sommets des houppiers sur le plan de la placette

## Mission

**En une phrase (quoi, et pourquoi) :** dessiner sur le plan de la vue Placette (cibles et ajout
d'arbre) trois couches à cocher — relief `cvat.tif` en gris, contours des houppiers, sommets des
houppiers — parce que c'est là que l'opérateur positionne les arbres et qu'il doit pouvoir les
rapprocher des houppiers vus par le LiDAR (demande de Pascal du 2026-11-21 : « c'est là qu'ils ont
tout leur intérêt »).

**Redite par l'agent :** _à compléter par l'agent avant d'agir._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui : `ui/PlanPlacette.kt` (Canvas, nord en haut, mètres autour du centre), `groma-core` `Houppiers.kt` (anneaux déjà lus), `GeoTiff` (lecture des rasters), schéma 1.7.0 (`rasters.cvat`, `x_sommet`/`y_sommet`) | |
| A-t-il les moyens ? | oui : exemple 1.7.0 (`exemples/inventaire_video` : `cvat.tif`, houppiers avec sommets) ; projet Marsannay 1.6.0 (houppiers sans sommets) | confier après 041b |

**Temps de la main :** donner. **Degré de la main :** le chef tranche.

## Ma réponse d'abord

_Rempli par Pascal seul._

## L'Homme

- **Agent :** `afficheur`. **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** la carte osmdroid (040) ; MNT ou MNH affichés ; calcul du CVAT.

## Ce qui est attendu

1. **Relief CVAT** sous tout le reste : la partie de `rasters/cvat.tif` qui couvre le plan (étendue
   affichée, avec le zoom), en niveaux de gris, 0 = transparent, opacité provisoire 50 %. Lecture en
   tâche de fond, une fois par placette (fenêtre autour du centre, pas tout le fichier).
2. **Houppiers** : contours `#2e7d32` (symbologie du brief du 2026-10-16), au-dessus du relief, sous
   les arbres et les cibles. Tous les houppiers qui touchent l'étendue du plan, pas seulement ceux de
   la placette (`plot_id` NULL compris, pour une placette déplacée), filtrés par `BETWEEN` sur
   `x_sommet`/`y_sommet` en 1.7.0, par l'emprise des anneaux en 1.6.0.
3. **Sommets** : un petit point (croix ou disque de 3 dp) de même couleur ; seulement en 1.7.0.
4. **Trois cases** sous le plan (« Relief », « Houppiers », « Sommets »), cochées par défaut quand
   la donnée existe, absentes sinon ; état retenu pendant la session. Les arbres et cibles restent
   lisibles et touchables par-dessus.
5. Mêmes couches dans les deux modes de la vue Placette (cibles et « Ajouter un arbre ») : c'est le
   même plan.

## Harnais

- **Peut (périmètre) :** `ui/PlanPlacette.kt`, `ui/EcranPlacette.kt` (passage des couches au plan,
  après 041b), `android/app/src/main/kotlin/groma/app/Projet.kt` (lecture des houppiers de
  l'étendue), `android/groma-core/src/main/` (lecture d'une fenêtre de `cvat.tif` en Byte,
  requête des houppiers par emprise) et ses tests, `res/values/strings.xml`, `docs/terrain.md`,
  `PLAN.md`, journal (ajout seul).
- **Câblé :** pas de `main` ; pas de « terminé » sans tests verts ; journal en ajout seul.
- **Ne doit pas :** bloquer le fil principal ; changer les couleurs des arbres ou des cibles ; écrire
  dans le GeoPackage.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** opacité du CVAT 50 % ; couches cochées par défaut ; point de sommet 3 dp.
- **Mesurée par :** essai de Pascal sur le S23 (exemple 1.7.0, puis premier projet 1.7.0 de nemeton).
- **Gel :** après cet essai ; 040 reprend les mêmes réglages pour la carte.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 041b fusionnée (même `EcranPlacette.kt`). **Avant :** 040, qui réutilise la lecture
  de `cvat.tif` et la requête des houppiers écrites ici.
- **CI rouge attendue :** aucune.

## Tests

- groma-core : fenêtre de `cvat.tif` (0 → transparent, bords, fenêtre hors du raster → vide) ;
  houppiers d'une emprise (bornes incluses ; `plot_id` NULL repris ; projet 1.6.0 sans colonnes de
  sommet → contours sans sommets).
- Conversion Lambert-93 → plan (décalage au centre, nord en haut) testée en fonction pure.

## Terrain

- Sur le S23 : P067 de Marsannay (1.6.0) montre les contours ; l'exemple 1.7.0 montre relief,
  contours et sommets ; les arbres filmés tombent dans ou près de leurs houppiers.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

En ajoutant un arbre oublié, Pascal voit dans quel houppier il tombe et le sommet le plus proche.
