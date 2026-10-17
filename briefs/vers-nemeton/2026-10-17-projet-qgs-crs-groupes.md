# Brief nemeton — projection du projet, emprise d'ouverture et groupes de couches dans le `.qgs`

Date : 2026-10-17. Émetteur : Groma. Destinataire : nemeton
(`.build_qgs_xml()`, `R/qgis_export.R` ; appelée par `.iv_empaqueter()` de
`creer_projet_inventaire_video()` et par l'export QField du plan
d'échantillonnage).

## Constat

Essai du 2026-10-17 sur un Galaxy S23 Ultra, QField 4.3.6, projet
`20261015_194200_yzoh` (Marsannay-la-Côte) produit par
`creer_projet_inventaire_video()` :

1. **Les fonds de carte ne s'affichent pas** (Plan IGN coché, réseau
   disponible). La barre d'échelle indique « 50000 km » pour une emprise
   d'environ 1 km.
2. **La légende est illisible** : 225 couches à plat, dont 212 rasters
   `Pxxx_mnt` / `Pxxx_mnh` (106 placettes), entre les couches de
   l'inventaire et les fonds.
3. Le projet s'ouvre sans emprise définie.

## Cause du point 1 (vérifiée)

`.build_qgs_xml()` écrit `<properties><Option type="Map"/></properties>`.
QGIS ne lit le nœud `<projectCrs>` que si la propriété
`SpatialRefSys/ProjectionsEnabled` vaut 1. Sans elle, **la projection du
projet est invalide** : le canevas n'a pas de SCR, les couches en
EPSG:2154 s'affichent telles quelles (pas de transformation), mais les fonds
XYZ en EPSG:3857 ne peuvent pas être reprojetés, et les distances (barre
d'échelle) sont fausses.

Vérifié avec PyQGIS (QGIS 3.4x) sur le `.qgs` du projet :

| `.qgs` | `QgsProject.crs()` | unités |
|---|---|---|
| tel que livré | invalide (vide) | inconnues |
| avec `ProjectionsEnabled = 1` | EPSG:2154 RGF93 v1 / Lambert-93 | mètres |

Le même défaut touche l'export QField du plan d'échantillonnage, qui passe
par la même fonction.

## Demande

### 1. Projection et mesures (indispensable)

Dans `<properties>` :

```xml
<properties>
  <SpatialRefSys>
    <ProjectionsEnabled type="int">1</ProjectionsEnabled>
  </SpatialRefSys>
  <Measure>
    <Ellipsoid type="QString">EPSG:7019</Ellipsoid>
  </Measure>
</properties>
```

`EPSG:7019` (GRS 1980) donne des mesures de distance et de surface
ellipsoïdales dans QField ; sans lui, l'ellipsoïde vaut `NONE` (mesures
planes en Lambert-93, acceptables mais à choisir sciemment).

### 2. Emprise d'ouverture (souhaitable)

Après `</properties>`, l'emprise des placettes (ou de la zone d'étude) avec
une marge, dans le SCR du projet :

```xml
<ProjectViewSettings UseProjectScales="0" rotation="0">
  <Scales/>
  <DefaultViewExtent xmin="…" ymin="…" xmax="…" ymax="…">
    <spatialrefsys nativeFormat="Wkt">…même contenu que projectCrs…</spatialrefsys>
  </DefaultViewExtent>
</ProjectViewSettings>
```

### 3. Groupes de couches (souhaitable)

Remplacer l'arbre à plat par trois groupes dans `<layer-tree-group>` :

| Groupe | Contenu | `checked` | `expanded` |
|---|---|---|---|
| Inventaire | les 9 couches du GeoPackage, dans l'ordre actuel | `Qt::Checked` | `1` |
| Rasters des placettes | les `Pxxx_mnt` / `Pxxx_mnh` | `Qt::Unchecked` | `0` |
| Fonds de carte | Cadastre, Plan IGN, Ortho IGN, OSM | `Qt::Checked` | `0` |

```xml
<layer-tree-group name="Rasters des placettes" checked="Qt::Unchecked" expanded="0">
  <customproperties><Option type="Map"/></customproperties>
  <layer-tree-layer … />
  …
</layer-tree-group>
```

Les `<maplayer>` de `<projectlayers>` ne changent pas ; seul l'arbre de
légende est regroupé. Un groupe décoché masque ses couches sans changer leur
propre état coché.

Variante, au choix de nemeton : une mosaïque VRT par type (`mnt.vrt`,
`mnh.vrt`) au lieu de 212 couches, si QField la lit bien depuis le dossier du
projet (à vérifier). Plus légère à l'ouverture, mais un changement de
contenu, pas seulement de présentation : prévenir Groma (l'extraction du
`.qgz` dans l'application ne garde que `rasters/*.tif`, voir
`ExtractionQgz`).

## Test proposé

Ouvrir le `.qgs` produit avec PyQGIS :

```python
p = QgsProject.instance(); p.read("inventaire_video.qgs")
assert p.crs().authid() == "EPSG:2154"
assert not p.viewSettings().defaultViewExtent().isNull()
assert [g.name() for g in p.layerTreeRoot().children()] == [
    "Inventaire", "Rasters des placettes", "Fonds de carte"]
```

Les trois assertions passent sur une copie du `.qgs` du projet
`20261015_194200_yzoh` modifiée à la main par Groma (essai du 2026-10-17).
Puis, dans QField : fond Plan IGN visible, barre d'échelle cohérente,
légende en trois groupes.

## Rien à changer côté Groma

L'application et le serveur ne lisent ni la projection du projet, ni
l'arbre des couches. Le zip envoyé par « Ouvrir dans QField » reste le même.
