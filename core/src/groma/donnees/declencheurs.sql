-- Déclencheurs du GeoPackage inventaire_video.gpkg (décision 3 : couche
-- `detections` séparée, écrite par la seule application Groma).
--
-- Règles :
--   * Groma n'écrit QUE dans `detections`, en transaction courte
--     (BEGIN IMMEDIATE + busy_timeout), jamais directement dans `arbres`.
--   * Une détection nouvelle (uuid inconnu) crée la ligne `arbres`
--     correspondante. Une ligne `arbres` existante n'est jamais recréée.
--   * Une détection mise à jour ne touche que les colonnes de mesure d'un
--     arbre encore provisoire (ou à vérifier). Les attributs saisis dans
--     QField (essence_arbre, statut, qualite, notes, photos) ne sont jamais
--     écrits ici, et une hauteur mesurée (h_source = 'mesuree') est conservée.
--   * Un faux positif n'est jamais promu en arbre.
--
-- Pas de fonction spatiale (ST_*) : ces déclencheurs s'exécutent avec le
-- SQLite d'Android, sans SpatiaLite ni GDAL. C'est aussi pourquoi `placettes`,
-- `arbres` et `detections` sont créées sans index spatial (SPATIAL_INDEX=NO) :
-- les déclencheurs R*Tree de GDAL se préparent sur tout UPDATE et appellent
-- ST_IsEmpty, même quand la géométrie ne change pas.
-- Colonne géométrique : `geom` (GEOMETRY_NAME=geom).

CREATE TRIGGER IF NOT EXISTS trg_detections_insert
AFTER INSERT ON detections
WHEN NEW.etat_detection IS NOT 'faux_positif'
 AND NOT EXISTS (SELECT 1 FROM arbres WHERE uuid = NEW.uuid)
BEGIN
  INSERT INTO arbres (
    geom, plot_id, tree_id, dbh_cm, h_m, statut, uuid, num_provisoire,
    azimut, distance_m, h_source, houppier_id, etat_detection, methode
  ) VALUES (
    NEW.geom, NEW.plot_id, NEW.num_provisoire, NEW.dbh_cm, NEW.h_m, 'vivant',
    NEW.uuid, NEW.num_provisoire, NEW.azimut, NEW.distance_m,
    COALESCE(NEW.h_source, 'aucune'), NEW.houppier_id,
    COALESCE(NEW.etat_detection, 'provisoire'), NEW.methode
  );
END;

CREATE TRIGGER IF NOT EXISTS trg_detections_update
AFTER UPDATE ON detections
BEGIN
  UPDATE arbres SET
    geom = NEW.geom,
    dbh_cm = NEW.dbh_cm,
    azimut = NEW.azimut,
    distance_m = NEW.distance_m,
    num_provisoire = NEW.num_provisoire,
    tree_id = NEW.num_provisoire,
    houppier_id = NEW.houppier_id,
    h_m = CASE WHEN h_source = 'mesuree' THEN h_m ELSE NEW.h_m END,
    h_source = CASE WHEN h_source = 'mesuree' THEN h_source
                    ELSE COALESCE(NEW.h_source, 'aucune') END,
    etat_detection = CASE WHEN NEW.etat_detection = 'faux_positif' THEN etat_detection
                          ELSE NEW.etat_detection END
  WHERE uuid = NEW.uuid
    AND etat_detection IN ('provisoire', 'a_verifier');
END;
