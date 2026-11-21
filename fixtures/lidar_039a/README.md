# Fixtures de la mission 039a

`cvat_925000_6700000.csv` : valeurs (0 à 255, 0 = nodata) de `exemples/inventaire_video/rasters/cvat.tif`
sur le carré 924950-925050 (est) × 6699950-6700050 (nord), soit 50 × 50 pixels de 2 m, une ligne du
fichier par ligne de pixels, nord en haut. Produit avec rasterio
(`src.read(1, window=from_bounds(924950, 6699950, 925050, 6700050, src.transform))`),
indépendamment du code de la mission 039. Sert d'attendu à `lidar/cvat.tif` pour un centre relevé en
(925000, 6700000). Ne pas régénérer sans régénérer l'exemple.
