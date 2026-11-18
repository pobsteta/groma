# Bibliothèques `terrain` : ce que Groma adopte (mission 020)

Brief : `2026-10-17-bibliotheques-terrain.md` (v0.5.0 annoncée, v0.7.1 épinglée ici). `terrain` étant un autre
dépôt, il n'est pas modifié d'ici. `terrain-style` et `terrain-carte` : missions 010-013.

## Adopté (mission 020)

Les alias de `groma/core/Terrain.kt` gardent les noms `groma.core.*` : aucun appelant ne change.

| Chez Groma | Vient de | Note |
|---|---|---|
| `Position` | `terrain-gnss` `geo.Position` | même `data class` (latitude, longitude) |
| `Projections` | `terrain-gnss` `geo.Projections` | code identique au package près ; `ProjectionsTest` (PROJ) reste le filet |
| `Geodesie.decalage/distanceM/azimutDeg` | `terrain-gnss` `geo.Geodesie` | mêmes formules ; l'azimut de terrain (`mod 360`) donne celui de `Azimut.azimut` (test) ; `GeodesieTest` vert |
| `BaseSqlite` | `terrain-gpkg` `BaseSqlite` | mêmes trois membres |
| `GeoPackage.encoderPoint/decoderPoint/decoderAnneau/decoderPolygones` | `terrain-gpkg` `FormatGpkg` | couche format seulement ; multipolygones testés |

Le **métier** du GeoPackage (détections, centres, cibles, arbres, `retirerIndexSpatial`, étendues, colonnes ajoutées
à la volée) reste chez Groma, comme le dit le brief : ce n'est pas un manque de `terrain`.

## Pas adopté

| Module | Raison |
|---|---|
| `terrain-gnss` NMEA / NTRIP | v0.7.1 n'a pas `heureUtcMs`, `recuMonotoneMs`, l'instant RMC ni `LecteurNmea` (mission 019) ; noms différents (`QualiteFix`, `OrigineFix`, `parseGga`…) : les renommer est une question réservée à Pascal. Brief envoyé à `terrain` : `vers-terrain/2026-11-18-groma-fix-horodate-et-lecteur-nmea.md`. En attendant, `EquivalenceTerrainBibliothequesTest` épingle les parties identiques (GGA, somme de contrôle, découpeur, NTRIP). |
| `terrain-journal` | aucun usage chez Groma (pas de fusion multi-appareils) |

Aucune rupture de contrat public : les noms `groma.core` sont inchangés (alias).
