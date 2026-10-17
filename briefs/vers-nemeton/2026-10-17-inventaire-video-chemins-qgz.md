# Brief nemeton — chemins du GeoPackage et des rasters dans le `.qgz` de l'inventaire vidéo

Date : 2026-10-17. Émetteur : Groma. Destinataire : nemeton
(`creer_projet_inventaire_video()`, `R/inventaire_video.R`, `.iv_empaqueter()`).

## Constat

Le `.qgz` contient le `.qgs`, `inventaire_video.gpkg` et `rasters/*.tif`. Le
`.qgs` les désigne par des chemins relatifs :

* `./inventaire_video.gpkg|layername=…` (`.ogr_source()`) ;
* `./rasters/<plot_id>_mnt.tif` (couches `gdal`).

QGIS (et donc QField) résout un chemin relatif **à partir du dossier où se
trouve le `.qgz`**, pas à l'intérieur de l'archive. `QgsProject::pathResolver()`
renvoie `QgsPathResolver(fileName(), mArchive->dir())` : le dossier de
l'archive décompressée ne sert qu'aux chemins `attachment:///…`.

Conséquence : un `.qgz` ouvert seul, sans `inventaire_video.gpkg` ni
`rasters/` à côté de lui, a toutes ses couches introuvables. C'est le cas
quand on ouvre directement le `.qgz` livré par `creer_projet_inventaire_video()`,
et quand QField l'importe seul.

## Ce que fait Groma en attendant

« Ouvrir dans QField » (application Groma) envoie à QField un zip qui met
`inventaire_video.gpkg` et `rasters/` **à côté** du `.qgz` et du `.qml`. Les
chemins `./…` se résolvent donc. Rien ne casse côté Groma, quelle que soit
l'option retenue ci-dessous.

## Demande (au choix de nemeton)

1. **Chemins `attachment:`** : écrire `attachment:///inventaire_video.gpkg|layername=…`
   et `attachment:///rasters/<nom>.tif` dans le `.qgs`. QGIS lit alors les
   fichiers dans l'archive, et le `.qgz` se suffit à lui-même.
   **À vérifier** : QGIS réécrit-il le GeoPackage dans le `.qgz` à chaque
   enregistrement ? Les saisies QField vont dans la copie décompressée : il
   faut s'assurer qu'elles ne sont pas perdues à la fermeture.
2. **Fichiers à côté** (recommandé pour une couche éditée) : livrer en plus
   `inventaire_video.gpkg` et `rasters/` à côté du `.qgz`, ou un dossier
   `<projet>/` (`.qgs` + GeoPackage + rasters + `.qml`), sans rien embarquer.

Dans les deux cas, garder ce que lisent le serveur Groma (contrôle de
`projet_id`) et l'application (extraction) : `inventaire_video.gpkg` dans le
`.qgz`, ou bien prévenir Groma pour adapter `ProjetsQfield._verifier_qgz` et
`ExtractionQgz`.

## Test proposé

Ouvrir le `.qgz` produit, copié **seul** dans un dossier vide, avec QGIS 3.44 :
toutes les couches doivent s'afficher (option 1). Ou bien ouvrir le dossier
livré tel quel (option 2).
