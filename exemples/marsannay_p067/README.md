# Exemple réel : P067 de Marsannay-la-Côte (schéma 1.7.0)

Projet d'exemple construit par `exemples/creer_marsannay_p067.py` (mission 044) à partir des
données réelles de la placette P067, filmée le 2026-11-20. Il sert aux tests de la mission 039
(paquet LiDAR autour du centre relevé) et aux vérifications d'écran (042, 043) sur un vrai
terrain. L'exemple synthétique `exemples/inventaire_video/` reste pour les cas limites (coin de
quatre dalles, deux placettes).

Identifiant du projet : `exemple_marsannay_p067`, distinct du projet réel
`20261015_194200_yzoh`, pour qu'un import sur le téléphone n'écrase jamais le vrai projet.

Coordonnées en Lambert-93 (EPSG:2154), x = est, y = nord, en mètres.

## Sources (lues sans écriture)

| Donnée | Fichier source | Date |
|---|---|---|
| MNT LiDAR HD 1 m | `~/.local/share/nemeton/projects/20261015_194200_yzoh/cache/layers/lidar_mnt/dtm.tif` | cache nemeton du 2026-10-15 |
| MNH LiDAR HD 1 m | `…/lidar_mnh/chm.tif` | idem |
| Projet réel | `~/groma-sauvegardes/2026-11-20_P067-film/projets/20261015_194200_yzoh/inventaire_video.gpkg` | copie du téléphone du 2026-11-20 |
| Paquet de terrain | `~/groma-sauvegardes/2026-11-20_P067-film/P067.groma.zip` | 2026-11-20, 15 h 23 (UTC) |
| Relief CVAT | `…/cache/accessibility/emprise_250m/mnt_highres_1m_CVAT_8bit_foretaccess.tif` (`--cvat`) | nemetonshiny (`build_cvat_precomputed`), 2026-11-21 |

GeoPackage ouvert en `mode=ro`, rasters et paquet en lecture ; le script refuse un dossier de
sortie qui contiendrait une source.

**Licence des données :** LiDAR HD de l'IGN, Licence Ouverte 2.0 (Etalab) ; données de terrain
(centre, cibles, images) : Pascal Obstétar, 2026.

## Ce qui est réel

* **Centre prévu** de P067 dans le plan du projet réel : (849 508,41 ; 6 689 219,98). La placette de
  l'exemple est **posée sur ce point**.
* **C0 mesuré** le 2026-11-20 au GNSS du téléphone (qualité `telephone`, précision annoncée 3,79 m) :
  (849 659,22 ; 6 689 237,97), à 151,9 m à l'est du centre prévu et à 107 m hors de toute UGF et
  parcelle du projet. Décision de Pascal du 2026-11-21 : **tout le relevé de terrain est déplacé
  sur le centre prévu** (C0, cibles et centre du paquet translatés de −150,81 m en x et −17,99 m
  en y). Le relevé garde sa géométrie propre, mais il ne correspond plus aux arbres du LiDAR à cet
  endroit : les cibles filmées ne tombent pas sur les houppiers de l'exemple.
* Attributs du plan de P067 repris du projet réel : `visit_order`, `type`, `wgt`, `ip`, `stratum`,
  `strat_*`, `rayon_m` (15 m), `seuil_diam_cm`, `unite_azimut`, `convergence_deg`.
* `declinaison_deg` : 2,811°, valeur employée sur le terrain (manifeste du paquet ; vide dans le
  projet réel).
* Centre relevé (`centres_mesures`) : tous ses attributs, sauf sa position (centre prévu) et
  `ecart_prevu_m` (voir plus bas).
* Cibles C1 à C3 : azimut magnétique, distance, diamètre et source du paquet ; leur position est
  recalculée depuis le C0 de l'exemple et retombe, à la translation près, à moins d'un centimètre
  de celle écrite par le téléphone (test).
* MNT et MNH : valeurs LiDAR HD ; `controle/mnt_1m.tif` est la fenêtre du MNT à 1 m employée,
  recopiée telle quelle (référence des tests).
* Paquet `paquets/P067.groma.zip` : manifeste complet (les 525 images avec leurs poses et
  intrinsèques, les 25 détections de cibles, placette, centre, cibles) et les fichiers (image JPEG,
  profondeur, confiance) d'**une image sur 25** (21 images), tels qu'enregistrés. Seul `centre`
  est réécrit (centre prévu) ; les poses, locales à la session ARCore, sont inchangées.

## Ce qui est calculé (pour l'exemple, par Groma)

* **Dalle MNT** `rasters/mnt_0849_6689.tif` (2 000 × 2 000 pixels de 0,5 m, format
  `rasters.format`) : rééchantillonnage **bilinéaire** du MNT à 1 m sur l'emprise, arrondi au 1/128 m
  (écart < 4 mm), nodata ailleurs. Aux nœuds du MNT à 1 m, la dalle s'écarte du LiDAR de 1,0 cm en
  médiane, 4,6 cm au 95e centile et jusqu'à 30,5 cm sur une rupture de pente à 64 m au sud-ouest
  du centre (lissage bilinéaire) ; dans la placette, 7,9 cm au 99e centile et 10,8 cm au plus
  (pente de 42 %).
* **Rasters de placette** `rasters/P067_mnt.tif`, `P067_mnh.tif` : 0,5 m, carré de 2 × 25 m
  (rayon + `marge_m`) autour du centre, bilinéaires.
* **Pente, exposition, recouvrement** de P067 recalculés au centre (`groma.mnt`) : 41,6 %, NE,
  88,2 %.
* **Houppiers** sur toute l'emprise : maxima locaux du MNH lissé (gaussienne 0,5 m, fenêtre 3 m,
  5 m de haut au moins) et ligne de partage des eaux (`scipy.ndimage.watershed_ift`) sur le MNH
  de 2 m et plus ; sommet = pixel de `h_max` ; `plot_id` = P067 si le houppier touche le disque de
  15 m (770 houppiers, dont 13 sur P067). **Segmentation de Groma pour l'exemple, pas celle de nemeton** ; valeurs provisoires, à
  remplacer par le premier projet 1.7.0 livré par nemeton.
* `ecart_prevu_m` = 0 : le C0 de l'exemple est posé sur le centre prévu (151,9 m dans le projet réel).
* `projet_id`, `projet_nom`, `description` propres à l'exemple ; `prepare_le` fixé
  (2026-11-21 12:00:00) pour que le script soit reproductible.

## Emprise de la forêt, UGF et parcelles

Le centre prévu se trouve dans l'**UGF 18** et la **parcelle 21390 A 21** du projet réel. Elles sont
découpées sur le carré de 100 m centré sur la placette (UGF : 7 806 m², 78 % du carré ; parcelle :
4 939 m² découpés, `contenance_m2` cadastrale gardée, 42 450 m²). L'emprise est l'UGF découpée,
augmentée de `marge_foret_m` (50 m). Aucun contour n'est inventé : une couche sans entité au centre
resterait vide.

## Relief CVAT

Calculé le 2026-11-21 par nemetonshiny (`build_cvat_precomputed`, foretaccess, MNT LiDAR HD
réacquis, 2 m, parcelles du projet, zone tampon ramenée à 188 m) et passé tel quel :

```bash
python exemples/creer_marsannay_p067.py \
  --cvat ~/.local/share/nemeton/projects/20261015_194200_yzoh/cache/accessibility/emprise_250m/mnt_highres_1m_CVAT_8bit_foretaccess.tif
```

Deux codages sont acceptés (Byte, 2 m, EPSG:2154) : celui de `rasters.cvat` (nodata 0, relief 1
à 255), gardé tel quel, et celui de foretaccess (nodata 255, relief 0 à 254), recodé en valeur + 1,
nodata 0, soit `1 + round(v * 254)`. Tout autre format est refusé. Le CVAT est découpé sur
l'emprise, sans rééchantillonnage, pixels hors emprise à 0 ; `rasters/cvat.tif` fait 7 Ko.

## Reconstruire

```bash
python exemples/creer_marsannay_p067.py            # sources à leur place par défaut
python exemples/creer_marsannay_p067.py --help     # chemins des sources
```

Tests : `pytest core/tests/test_exemple_marsannay.py`.
