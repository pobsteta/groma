# Mission 039a — Tests d'acceptation du paquet LiDAR autour du centre relevé

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, sans lire le code de 039, les tests d'acceptation du
paquet qui emporte le MNT, le relief CVAT et les houppiers découpés à 50 m autour du centre relevé,
pour que 039 ne s'auto-évalue pas (plan validé par Pascal le 2026-11-21, fiche 039, « Plan validé »).

**Redite par l'agent :** _à compléter par l'agent avant d'agir._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui : fiche 039 (plan validé), API fixée ci-dessous, schéma 1.7.0, `groma.dalles` (Python) comme référence de nommage | |
| A-t-il les moyens ? | oui : `exemples/inventaire_video` (4 dalles, `cvat.tif`, houppiers avec sommets), `fixtures/schema_1_6_0/`, `rasterio`, paquet réel `~/groma-sauvegardes/2026-11-20_P067-film/P067.groma.zip` | confier |

**Temps de la main :** donner. **Degré de la main :** le chef tranche.

## L'Homme

- **Agent :** `testeur`. **Modèle :** sonnet.
- **Ne lit pas :** le code écrit pour 039 (il n'existe pas encore sur la branche).

## API fixée par l'orchestrateur (contrat entre 039a et 039)

Kotlin, `groma-core`, paquet `groma.core` :

* `GeoTiff.ecrire(grille: Grille): ByteArray` — Float32, en bandes, DEFLATE sans prédicteur, nodata
  -9999 (NaN de la grille → -9999), EPSG:2154 ; `GeoTiff.lire(ecrire(g))` redonne `g`.
* `object LidarPaquet` :
  * `const val DEMI_COTE_M = 50.0` ;
  * `fun decouper(dossierProjet: File, db: BaseSqlite, plotId: String, centreX: Double, centreY: Double, rayonM: Double): ResultatLidar`
    (`BaseSqlite` : l’interface d’accès SQLite déjà utilisée par `Houppiers.dansEmprise`) ;
  * `data class ResultatLidar(val fichiers: Map<String, ByteArray>, val manifeste: Map<String, Any?>)` :
    `fichiers` a pour clés les chemins du paquet (`lidar/mnt.tif`, `lidar/mnh.tif`, `lidar/cvat.tif`,
    `lidar/houppiers.geojson`), présents seulement s'ils sont écrits ; `manifeste` est l'objet `lidar`
    du manifeste (clés `mnt`, `mnh`, `cvat`, `houppiers`, `decoupe` {`x`, `y`, `demi_cote_m`,
    `source_mnt`}, `remarque`), chaque clé de fichier présente seulement si le fichier l'est.
  * Ne lève jamais : en échec, `fichiers` vide et `manifeste["remarque"]` non vide.

Python, serveur : `groma_server.paquet.Lidar` gagne `remarque: str | None = None` ; la chaîne ajoute
aux alertes du rapport une ligne qui commence par « LiDAR : » quand `remarque` est présente.

## Ce qui est attendu (tests)

Kotlin (`android/groma-core/src/test/kotlin/groma/core/LidarPaquetTest.kt`) :
1. `GeoTiff.ecrire` puis `GeoTiff.lire` : valeurs, `x0`, `y0`, `res` identiques, NaN ↔ -9999 ; balises
   (bandes, DEFLATE 8 ou 32946, prédicteur absent ou 1, Float32, GeoKey 3072 = 2154).
2. Centre relevé à 150 m du centre prévu : `lidar/mnt.tif` de 200 × 200 pixels à 0,5 m, centré sur le
   centre relevé (± un demi-pixel), ne contient pas le centre prévu.
3. Coin des quatre dalles de l'exemple : chaque pixel de la sortie égal au pixel de sa dalle d'origine,
   aucune ligne ni colonne de nodata à la couture.
4. Un point (x = est, y = nord) prend dans la sortie la valeur qu'il a dans sa dalle.
5. `lidar/cvat.tif` : Byte, 2 m, 50 × 50, mêmes valeurs que `rasters/cvat.tif` sur la zone ; absent
   (et pas d'erreur) sans `rasters/cvat.tif`.
6. Houppiers 1.7.0 : sommet juste dedans gardé, juste dehors exclu même si le contour touche le carré,
   `plot_id` NULL gardé ; GeoJSON : FeatureCollection, champs `houppier_id`, `h_max`, `surface_m2`,
   `x_sommet`, `y_sommet`, coordonnées (est, nord).
7. Repli 1.6.0 (`fixtures/schema_1_6_0/`) : houppiers de la placette qui touchent le carré, sans
   champs de sommet ; aucun → remarque.
8. Sans dalles : raster de placette joint tel quel (octets identiques) s'il couvre centre ± rayon ;
   sinon pas de `lidar/mnt.tif` et remarque contenant « centre relevé ».
9. Dalle corrompue : ne lève pas, pas de `lidar/mnt.tif`, remarque.
10. MNT partiel : remarque « MNT partiel ».

Python (`server/tests/test_paquet_lidar_039.py`) :
11. Un paquet dont `lidar/` vient du Kotlin (fichiers de référence que le test Kotlin écrit, ou qu'un
    test génère ; pas de gros fichiers commités) est relu par `Paquet.ouvrir` et `_charger_lidar` ;
    `rasterio` relit `lidar/mnt.tif` avec les mêmes valeurs que la fenêtre `rasterio` de la dalle.
12. `lidar.remarque` apparaît dans les alertes du rapport (« LiDAR : … »).
13. Manifeste sans `lidar`, ou `lidar` sans `remarque` : accepté comme avant.

## Harnais

- **Peut (périmètre) :** `android/groma-core/src/test/`, `server/tests/`, `fixtures/` (petits
  fichiers seulement), cette fiche (« Redite », compte rendu), journal (ajout seul).
- **Câblé :** pas de `main` ; journal en ajout seul.
- **Ne doit pas :** lire ou écrire le code de production de 039 ; changer l'API ci-dessus (s'il la
  juge fausse : le dire à l'orchestrateur) ; commiter plus de 1 Mo de données.

## Dépend de / rouge attendu jusqu'à

- **Branche :** `feat/039-paquet-lidar` (avec 039). **Rouge attendu :** les tests de 039a jusqu'au
  code de 039 (compilation Kotlin comprise : l'API n'existe pas encore).

## Qui veille, qui tranche

- **Relecteur :** `relecteur` avec 039. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.
