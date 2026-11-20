# Mission 039 — Le paquet emporte le MNT et les houppiers autour du centre relevé

## Mission

**En une phrase (quoi, et pourquoi) :** à « Terminer », écrire dans le paquet `lidar/mnt.tif` et
`lidar/houppiers.geojson` découpés autour du **centre relevé** de la placette (dalles MNT et
houppiers de toute la forêt, sinon rasters par placette), et le déclarer dans `manifest.json`
(`lidar`), pour que le serveur cale le sol et contrôle l'orientation même quand la placette a été
déplacée (P067 filmée à 152 m de son centre prévu le 2026-11-20 : ni sol MNT ni contrôle des houppiers).

**Redite par l'agent :** _à compléter par l'agent avant d'agir._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | **non** : Groma lit les GeoTIFF (`groma.core.GeoTiff.lire`) mais n'en écrit pas ; découper et réécrire une grille Float32, ou joindre les dalles entières, est un choix à faire | **donner la main** : l'agent rend d'abord un plan, soumis à Pascal |
| A-t-il les moyens ? | oui : exemple 1.7.0 (037), `groma.dalles` comme référence, serveur `paquet.py` / `chaine.py` pour la lecture, paquets réels P067 dans `~/groma-sauvegardes/2026-11-20_P067-film/` | |

**Temps de la main :** donner — plan d'abord. **Degré de la main :** sollicitation d'avis sur le
plan (deux voies possibles), puis le chef tranche.

## Ma réponse d'abord

_Rempli par Pascal seul._

## L'Homme

- **Agent :** `dev` ; tests d'acceptation par `testeur` (039a). **Modèle :** opus (format du
  paquet, justesse des coordonnées).
- **Ce qui ne lui est pas demandé :** l'affichage (040) ; un MNH (pas de dalles MNH, décision du
  2026-11-20 : `lidar/mnh.tif` seulement si le raster par placette couvre le centre relevé).

## Ce qui est attendu

1. **Plan d'abord** (une page, dans cette fiche) : écrire un GeoTIFF découpé (format de
   `rasters.format`, carré de rayon + marge autour du centre relevé) ou joindre les dalles entières
   touchées ; mémoire et temps sur le S23 ; taille ajoutée au paquet.
2. Centre utilisé : le dernier centre relevé de la placette (`centres_mesures`), sinon le centre prévu.
3. Houppiers : ceux dont le sommet (`x_sommet`, `y_sommet`) est dans le carré, par `BETWEEN` (pas
   de R*Tree sur le téléphone) ; projet 1.6.0 sans sommets : ceux de la placette, comme aujourd'hui.
4. Repli : sans dalles, raster par placette s'il couvre le centre relevé ; sinon pas de MNT dans le
   paquet et un message dans le manifeste (`lidar.remarque`) repris dans le rapport du serveur.
5. Le rejeu (version d'essai) fait de même.

## Harnais

- **Peut (périmètre) :** `android/groma-core/src/main/kotlin/groma/core/` (nouveaux fichiers de
  découpe et d'écriture GeoTIFF, `GeoTiff.kt`), `android/groma-core/src/test/`,
  `android/app/src/main/kotlin/groma/app/EnregistreurPaquet.kt`, `CaptureActivity.kt` (appel à
  « Terminer » seulement), `Projet.kt`, `server/src/groma_server/paquet.py` et `chaine.py` (lecture
  de `lidar.remarque` seulement), `server/tests/`, `docs/`, `PLAN.md`, journal (ajout seul).
- **Câblé :** pas de `main` ; pas de « terminé » sans tests verts ; journal en ajout seul.
- **Ne doit pas :** ralentir « Terminer » de plus de 2 s sur le S23 ; écrire un GeoTIFF dans un
  autre format que `rasters.format` ; perdre le paquet si la découpe échoue (paquet écrit sans
  `lidar`, message).

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** marge de découpe = rayon + 15 m ; 2 s au plus à « Terminer ».
- **Mesurée par :** cette mission, première tâche après le plan : temps de lecture d'une dalle de
  2 000 × 2 000 Float32 et mémoire sur le S23.
- **Gel :** avant la publication qui contient 039.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 037 et 038 fusionnées.
- **CI rouge attendue :** tests de 039a jusqu'au code de 039 (même branche).

## Tests

- 039a : centre relevé à 150 m du centre prévu → MNT découpé autour du centre relevé ; bord de
  dalle → deux dalles assemblées sans couture ; houppiers filtrés par sommet ; projet 1.6.0 → repli
  ; paquet relu par `groma_server.paquet` et `_charger_lidar` sans erreur ; le GeoTIFF écrit est
  relu identique par `GeoTiff.lire` et par `rasterio`.

## Terrain

- Repasser la chaîne serveur sur un paquet de la placette près du chemin à Marsannay : sol
  « mnt » et contrôle des houppiers présents dans le rapport.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Point de suivi :** au
  rendu du plan. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Le rapport serveur d'une placette déplacée n'affiche plus « MNT ne couvre pas la reconstruction ».
