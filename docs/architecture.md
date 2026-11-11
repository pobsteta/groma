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
