# ADR 0001 — Pas d'index spatial R*Tree sur `centres_mesures` et `cibles`

Statut : acceptée (Pascal, 2026-11-12) · Mission 028

## Contexte

Le SQLite d'Android n'a pas le module `rtree`. Mesuré sur un S23 Ultra (essai de fumée) :
« no such module: rtree » à l'ajout d'un centre, au placement d'une cible, et à l'envoi de terrain
(les déclencheurs `rtree_*` de GDAL sont compilés avec toute instruction qui touche la table, même
un `DELETE`). Les projets préparés jusqu'ici ont l'index sur ces deux couches.

## Décision

1. `centres_mesures` et `cibles` sont créées sans index spatial (`index_spatial: false` dans
   `inventaire_video.schema.json`, version 1.6.0 inchangée). Les autres couches écrites par le
   téléphone (`placettes`, `arbres`, `detections`) n'en avaient déjà pas ; `houppiers`, écrite par le
   serveur, garde le sien.
2. Les projets anciens continuent de fonctionner : `GeoPackage.retirerIndexSpatial` retire l'index à
   la première écriture, **sans jamais charger le module** : déclencheurs `rtree_*` supprimés,
   ligne de la table virtuelle supprimée de `sqlite_master` sous `writable_schema` (une table
   virtuelle ne possède aucune page) avec incrément de `schema_version`, tables d'ombre
   `_node/_rowid/_parent` supprimées normalement, ligne `gpkg_rtree_index` de `gpkg_extensions`
   retirée. Une transaction : annulée si une ligne de la table est perdue (règle 5) ou si
   `integrity_check` n'est pas « ok ». Idempotente.
3. `ExportTerrain.extraire` l'appelle sur la copie à envoyer pour toutes les tables qu'elle vide :
   l'archive de terrain n'a donc plus d'index.
4. Le serveur n'exige pas l'index à la réception et le recrée seulement si un traitement en a
   besoin (rien ne l'exige aujourd'hui : `core` lit avec pyogrio, sans index).

## Conséquences

* Pas de requête spatiale accélérée sur ces deux couches côté téléphone (quelques lignes par
  placette : sans effet).
* nemeton ne lit pas ces index (échanges par GeoPackage, lecture par GDAL/sf, qui fonctionne sans
  index) : pas de brief.
* Test : le SQLite de la JVM a le module `rtree` ; le téléphone est simulé en renommant le module
  dans `sqlite_master` (`rtree_absent`), ce qui reproduit « no such module » (`IndexSpatialTest`).
  Seul un essai sur téléphone prouve le comportement du SQLite d'Android lui-même
  (en particulier `writable_schema`).
