# Architecture

```
nemetonshiny (onglet Terrain > Inventaire vidéo)
  │  Préparer : nemeton::creer_projet_inventaire_video() ──► projet QField (.qgz)
  │                                                         inventaire_video.gpkg + rasters/
  ▼
Téléphone ── QField (facultatif) : notes, photos ───────────┐
   │          (corrections NTRIP Centipède)                  │ même GeoPackage
   └─ Groma : vidéo ARCore + profondeur + ArUco              │ (couche detections,
        ├─ contrôle qualité en direct, verdict               │  déclencheurs → arbres)
        ├─ calcul embarqué ──► detections provisoires ───────┘
        ├─ saisies (centre, cibles, arbres) ──► <plot>.terrain.zip
        │     envoi reprenable après chaque enregistrement ──────┐
        └─ paquet <plot>.groma.zip ──► envoi reprenable (Wi-Fi)  │
                                            │                    │
Serveur Groma (FastAPI + worker GPU) ◄──────┘
   segmentation → reconstructions (COLMAP | VGGT | profondeur) → cibles → sol (MNT)
   → tranche 1,30 m → cercles → géoréférencement → numérotation → houppiers → rapport
   └─► <plot>_definitif.gpkg + rapport JSON/HTML
   └─► <plot>.terrain.zip (sans tâche GPU, remplacé à chaque envoi) ◄┘
                                            │
nemetonshiny  Suivre : nemeton::groma_etat_placettes()
              Intégrer : nemeton::importer_inventaire_video()
                 (terrain.zip du serveur, ou copie de QField à défaut ;
                  le serveur gagne, avec une alerte : brief 2026-11-11)
                 rapprocher_arbres() → valider → aggregate_plot_metrics(20) → NDP 3
                 fusion par plot_id dans data/field_data.gpkg
```

## Une règle, trois langages

La numérotation est implémentée en Python (`core/src/groma/numerotation.py`),
en Kotlin (`android/groma-core/.../Numerotation.kt`) et en R
(`briefs/vers-nemeton/R/numerotation.R`). Les trois passent les cas de
`fixtures/numerotation/`, dont les résultats attendus sont écrits à la main.
La CI rejoue les trois.

## Repères de coordonnées

* **Local** (reconstruction) : métrique, z vers le haut, cap arbitraire.
  Il dérive du repère monde d'ARCore (Y vers le haut) :
  `x = X, y = −Z, z = Y`.
* **Lambert-93** (EPSG:2154) : toutes les couches et tous les rasters.
* **Nord géographique** : référence des azimuts et de la numérotation
  (`az_geo = az_grille + convergence_deg`).

## GeoPackage et rasters du projet (schéma 1.7.0)

Source unique : `core/src/groma/donnees/inventaire_video.schema.json`
(clés `couches` et `rasters`). Le dossier d'un projet contient
`inventaire_video.gpkg` et `rasters/`, tous les fichiers **directement**
sous `rasters/` (le téléphone n'extrait du `.qgz` que `rasters/<nom>.tif`).
Brief : `briefs/vers-nemeton/2026-11-20-relief-cvat-houppiers-foret.md`.

| Fichier | Emprise | Format |
|---|---|---|
| `rasters/{plot_id}_mnt.tif`, `rasters/{plot_id}_mnh.tif` | placette prévue + `marge_m` (10 m) | `rasters.format` : GeoTIFF Float32, une bande, en bandes, DEFLATE sans prédicteur, nodata -9999, EPSG:2154, 0,5 m |
| `rasters/mnt_{x}_{y}.tif` (1.7.0) | dalles de `cote_m` (1 km) qui touchent la forêt | même format ; 2 000 × 2 000 pixels alignés sur le kilomètre ; `{x}`, `{y}` = coin sud-ouest en km, quatre chiffres ; nodata hors de l'emprise ; **pas de dalle MNH** |
| `rasters/cvat.tif` (1.7.0, facultatif) | forêt | GeoTIFF Byte, une bande, 2 m, EPSG:2154, DEFLATE ; `1 + round(v * 254)`, 0 = nodata |

* **Emprise de la forêt** : union des `ugf` + `marge_foret_m` (50 m) ; faute
  d'`ugf`, union des `parcelles` ; faute de parcelles, placettes + `marge_m`.
* **Dalles** : un point pile sur une ligne du kilomètre appartient à la
  dalle au nord-est (intervalles semi-ouverts). Nommage de référence :
  `groma.dalles` (`nom_dalle`, `dalle_du_point`, `dalles_du_rectangle`),
  repris par les tests Kotlin de la mission 039. Une interpolation à moins
  d'un demi-pixel d'un bord de dalle a besoin de la dalle voisine.
* **Houppiers** (1.7.0) : toute l'emprise de la forêt, `plot_id` NULL hors
  placette ; `x_sommet`, `y_sommet` (Lambert-93, m, requis) = sommet du
  houppier ; index B-tree `houppiers_sommet` sur ces deux colonnes (le
  téléphone, sans module R*Tree, filtre par `BETWEEN`).
* **Vérification** : `groma.dalles.verifier_projet(dossier)` renvoie, en
  français, les écarts des dalles et de `cvat.tif` au schéma (type, système,
  pas, taille, coin d'après le nom, nodata, compression). Un projet 1.6.0
  (sans dalles ni CVAT) est conforme. Les rasters sont écrits par nemeton,
  pas par `groma-preparer`.
* **Projets 1.6.0** : relus tels quels (`houppiers` sans sommets, rasters
  par placette seuls) ; Groma se replie sur les rasters par placette.
* **Exemples 1.7.0** : `exemples/inventaire_video/` (synthétique, deux
  placettes, coin de quatre dalles : cas limites) et
  `exemples/marsannay_p067/` (réel, mission 044 : P067 de Marsannay filmée
  le 2026-11-20, posée sur son centre prévu avec le relevé de terrain déplacé
  sur ce point, UGF et parcelle réelles, une dalle `mnt_0849_6689.tif`
  rééchantillonnée du MNT LiDAR HD à 1 m, houppiers calculés par Groma, paquet
  de terrain réduit dans `paquets/`). Le second est construit par
  `exemples/creer_marsannay_p067.py` depuis les sources réelles, lues sans
  écriture ; son `README.md` dit ce qui est réel et ce qui est calculé.
