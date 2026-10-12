# Brief vers nemetonshiny : sous-onglet « Inventaire vidéo »

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemetonshiny/2026-10-12-inventaire-video.md`).
* **Prérequis** : la version de nemeton qui livre le brief
  `briefs/vers-nemeton/2026-10-12-inventaire-video.md`. Toutes les
  fonctions appelées ci-dessous en viennent. **Aucune logique métier dans
  ce module** (règles 1 à 3).
* **Type** : `feat:`.

## 1. Emplacement

Nouveau `bslib::nav_panel` dans le `navset_card_underline` d'id
`terrain_nav` (`R/app_ui.R`), après « Import » :

```r
bslib::nav_panel(
  title = i18n$t("tab_terrain_video"),
  value = "inventaire_video",
  icon = bsicons::bs_icon("camera-video"),
  mod_inventaire_video_ui("inventaire_video")
)
```

Module `R/mod_inventaire_video.R` : `mod_inventaire_video_ui(id)` et
`mod_inventaire_video_server(id, project, i18n)`. La signature suit celle de
`mod_field_ingest_server()` (projet courant en réactif).

## 2. Bloc « Préparer »

* Sélection des placettes du plan d'échantillonnage du projet : même source
  que `mod_sampling.R`, tableau à cocher. Toutes sont cochées par défaut.
* Réglages : rayon (`numericInput`, 20 m), seuil de diamètre (7,5 cm), unité
  d'azimut (`radioButtons` degrés / grades), déclinaison magnétique (degrés,
  positive vers l'est, saisie obligatoire, avec un lien d'aide vers le
  calculateur NOAA ou IGN), case « inclure le LiDAR (MNT, MNH,
  houppiers) ». MNT et MNH sont résolus comme dans `mod_sampling.R`
  (`resolve_project_dem()`, `.project_chm()`). Sans MNH, la case est
  grisée et un message l'explique.
* Bouton « Générer le projet QField » : `nemeton::creer_projet_inventaire_video(...)`
  dans un `ExtendedTask` (comme `service_compute.R`), puis
  `downloadButton` du `.qgz`.

## 3. Bloc « Suivre »

* Paramètres du serveur Groma (URL et jeton), lus dans la configuration du
  projet ou de l'application, jamais en clair dans l'interface.
* Tableau par placette : état (à faire / provisoire / envoyée / définitive),
  date, verdict du rapport (conforme / à vérifier), nombre d'arbres, alertes.
  Source : `nemeton::groma_etat_placettes(url, jeton)`, croisé avec
  `etat_traitement` du dernier GeoPackage intégré.
* Clic sur une placette : rapport HTML du serveur dans un `modalDialog`
  (iframe sur le fichier téléchargé par `groma_telecharger_resultats()`),
  avec les alertes en tête.
* Bouton « Actualiser ». Pas de scrutation automatique.

## 4. Bloc « Intégrer »

* `fileInput` du `inventaire_video.gpkg` revenu du terrain (export QField).
* Bouton « Récupérer les résultats définitifs » (téléchargement serveur des
  placettes concernées), ou `fileInput` multiple des `*_definitif.gpkg`.
* Bouton « Intégrer » : `nemeton::importer_inventaire_video(gpkg,
  resultats, field_data_gpkg = <projet>/data/field_data.gpkg)`. Il fait le
  rapprochement, la validation, les agrégats, le marquage de source et la
  fusion par `plot_id`. Il est appelé exactement comme
  `mod_field_ingest.R` enchaîne aujourd'hui `import_qgis_gpkg()` →
  `validate_field_data()` → `aggregate_plot_metrics()` →
  `attach_field_data_to_units()` → `detect_ndp()`. **Ne jamais écraser** un
  inventaire classique présent dans `data/field_data.gpkg` : c'est garanti
  par nemeton, et un test le vérifie ici.
* Affichage : erreurs et avertissements de validation (même rendu que
  l'Import), arbres à compléter et à vérifier, numéros modifiés
  (`ancien → nouveau`), nouveau NDP. Bouton facultatif « Renvoyer sur le
  terrain les arbres à compléter » (nouveau `.qgz` filtré).

## 5. Clés i18n (FR / EN, `TRANSLATIONS` de `R/utils_i18n.R`)

| Clé | FR | EN |
|---|---|---|
| `tab_terrain_video` | Inventaire vidéo | Video inventory |
| `video_bloc_preparer` | Préparer | Prepare |
| `video_bloc_suivre` | Suivre | Track |
| `video_bloc_integrer` | Intégrer | Integrate |
| `video_placettes` | Placettes du plan | Sampling plots |
| `video_rayon` | Rayon de placette (m) | Plot radius (m) |
| `video_seuil_diam` | Diamètre minimal (cm) | Minimum diameter (cm) |
| `video_unite_azimut` | Unité d'azimut | Azimuth unit |
| `video_degres` | Degrés | Degrees |
| `video_grades` | Grades | Gradians |
| `video_declinaison` | Déclinaison magnétique (°, est positive) | Magnetic declination (°, east positive) |
| `video_avec_lidar` | Inclure MNT, MNH et houppiers | Include DTM, CHM and crowns |
| `video_lidar_absent` | Aucun MNH dans le projet : hauteurs non pré-remplies | No CHM in the project: heights not pre-filled |
| `video_generer` | Générer le projet QField | Build the QField project |
| `video_telecharger` | Télécharger le projet (.qgz) | Download the project (.qgz) |
| `video_serveur` | Serveur Groma | Groma server |
| `video_actualiser` | Actualiser | Refresh |
| `video_etat_a_faire` | À faire | To do |
| `video_etat_provisoire` | Provisoire | Provisional |
| `video_etat_envoye` | Envoyée | Uploaded |
| `video_etat_definitif` | Définitive | Final |
| `video_verdict_conforme` | Conforme | Compliant |
| `video_verdict_a_verifier` | À vérifier | To check |
| `video_rapport` | Rapport de qualité | Quality report |
| `video_gpkg_terrain` | GeoPackage revenu du terrain | GeoPackage back from the field |
| `video_recuperer_resultats` | Récupérer les résultats définitifs | Fetch the final results |
| `video_resultats_serveur` | Résultats du serveur (.gpkg) | Server results (.gpkg) |
| `video_integrer` | Intégrer | Integrate |
| `video_a_completer` | Arbres à compléter | Trees to complete |
| `video_a_verifier` | Arbres à vérifier | Trees to check |
| `video_numeros_modifies` | Numéros modifiés | Changed numbers |
| `video_renvoyer_terrain` | Renvoyer sur le terrain | Send back to the field |
| `video_fusion_classique` | Inventaire classique conservé pour {n} placette(s) | Classic inventory kept for {n} plot(s) |
| `video_integration_ok` | Inventaire vidéo intégré | Video inventory integrated |

Toutes les clés font au plus 30 caractères (convention NMT).

## 6. Tests (`testServer()`, contribuent à covr)

* `test-mod_inventaire_video.R` :
  * Préparer : avec `local_mocked_bindings(creer_projet_inventaire_video = …)`,
    vérifier que les réglages saisis (rayon, seuil, unité, déclinaison,
    LiDAR) sont transmis tels quels, et que le téléchargement sert le
    `.qgz` rendu.
  * Suivre : `groma_etat_placettes` simulée ; le tableau reflète les quatre
    états, et une erreur réseau donne un message i18n, pas un crash.
  * Intégrer : sur le GeoPackage d'exemple de groma_nemeton
    (`exemples/inventaire_video/inventaire_video.gpkg`, à copier en
    fixture), avec un `field_data.gpkg` contenant déjà un inventaire
    classique de P002. Vérifier que P002 classique est conservé, que P001
    vidéo est ajouté, et que le NDP passe à 3.
  * Aucun texte littéral : toutes les étiquettes passent par `i18n$t()`.

Copie à déposer dans `/home/pascal/dev/briefs/vers-nemetonshiny/2026-10-12-inventaire-video.md`.
