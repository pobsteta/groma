# Brief vers nemeton : complément à l'annexe « plugin QField » — fonds et houppiers

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemeton/2026-10-16-inventaire-video-fonds-houppiers.md`).
* **Destinataire** : `nemeton`. Complète le § 2 bis (symbologie) de
  l'annexe `2026-10-16-inventaire-video-plugin-qfield.md`, déjà traitée.
* **Type de changement** : `feat:` (sans effet tant que
  `creer_projet_inventaire_video()` n'existe pas).

## Contexte

Pascal veut, dans QField, la même présentation que dans Marculus, y compris
pour les fonds de carte, le cadastre et les houppiers. Groma l'applique à son
projet QField de test (`qfield/symbologie.py`, seul endroit des couleurs).
`creer_projet_inventaire_video()` doit faire de même.

## Ajouts au § 2 bis

* **`houppiers`** :
  * contour seul `#2e7d32` de 0,4 mm, sans remplissage ;
  * couche **masquée** par défaut, comme dans Marculus, où l'on compte des
    milliers d'objets.
* **Fonds**, sous toutes les couches du projet, du haut vers le bas :

  | Couche | Source | Par défaut |
  |---|---|---|
  | Cadastre IGN | `CADASTRALPARCELS.PARCELLAIRE_EXPRESS`, style `PCI vecteur`, PNG transparent | masqué |
  | Plan IGN | `GEOGRAPHICALGRIDSYSTEMS.PLANIGNV2`, style `normal`, PNG | **affiché** |
  | Ortho IGN | `ORTHOIMAGERY.ORTHOPHOTOS`, style `normal`, JPEG | masqué |
  | OpenStreetMap | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | masqué |

  Les couches IGN sont des tuiles WMTS de la Géoplateforme
  (`https://data.geopf.fr/wmts`, grille `PM_0_19`, sans clé). Les déclarer en
  **couche XYZ** avec l'URL `GetTile` (`TILEMATRIX={z}&TILEROW={y}&TILECOL={x}`)
  plutôt qu'en WMTS : le GetCapabilities de la Géoplateforme est très lourd
  et bloque l'ouverture du projet. Exemple exact : `_tuiles_ign()` dans
  `qfield/symbologie.py`.

  Attributions, dans les métadonnées des couches :
  * « © IGN Géoplateforme, Licence Ouverte Etalab 2.0 » pour les fonds IGN ;
  * « cadastre © IGN » pour le cadastre ;
  * « © contributeurs OpenStreetMap » pour OSM.
* **Épaisseur des contours des `ugf`** : 0,9 mm au lieu de 0,8 mm, pour
  correspondre aux 4 px de Marculus.

## Tests (testthat)

* Le `.qgs` contient, sous les couches vectorielles et dans cet ordre, les
  quatre fonds ci-dessus.
* Seul le Plan IGN est coché ; `houppiers` est décochée.
