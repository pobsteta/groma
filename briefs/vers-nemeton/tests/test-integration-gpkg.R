# Integration test: the example inventaire_video.gpkg must go through the
# nemeton field-data path without error:
#   import_qgis_gpkg() -> validate_field_data() -> aggregate_plot_metrics(plot_radius = 20)
# with the plan columns untouched and `espece` derived from `essence_arbre`
# (decision 1) through map_bdforet_essence().
#
# Usage (from the groma_nemeton root):
#   Rscript briefs/vers-nemeton/tests/test-integration-gpkg.R
# with nemeton installed, or NEMETON_SRC=/path/to/nemeton (read-only source).

suppressPackageStartupMessages({
  library(sf)
  library(testthat)
})

racine <- normalizePath(Sys.getenv("GROMA_RACINE", "."))
gpkg <- file.path(racine, "exemples", "inventaire_video", "inventaire_video.gpkg")
definitif <- file.path(racine, "exemples", "resultats", "P001_definitif.gpkg")
referentiel <- file.path(racine, "core", "src", "groma", "donnees", "essences_bdforet_v2.json")

src <- Sys.getenv("NEMETON_SRC")
if (requireNamespace("nemeton", quietly = TRUE) && !nzchar(src)) {
  library(nemeton)
} else {
  if (!nzchar(src)) stop("Install nemeton or set NEMETON_SRC to its source directory.")
  env <- globalenv()
  assign("%||%", function(a, b) if (is.null(a)) b else a, envir = env)
  assign(".species_cache", new.env(), envir = env)
  for (f in c("species-config.R", "field_schema.R", "qgis_import.R")) {
    source(file.path(src, "R", f), local = env)
  }
  # Lecture seule des sources : la configuration d'essences est lue dans
  # inst/species au lieu de system.file() (paquet non installé).
  assign("get_species_config", function(region = "BFC") {
    jsonlite::read_json(file.path(src, "inst", "species", paste0(toupper(region), ".json")),
                        simplifyVector = FALSE)
  }, envir = env)
}

ess <- jsonlite::fromJSON(referentiel)$essences
vers_espece <- function(code) {
  lib <- ess$bdforet[match(code, ess$code)]
  vapply(lib, function(l) if (is.na(l)) NA_character_ else map_bdforet_essence(l, region = "BFC"), "")
}

test_that("the example GeoPackage passes the nemeton field-data path", {
  g <- import_qgis_gpkg(gpkg)
  expect_s3_class(g$placettes, "sf")
  expect_true(all(c("wgt", "ip", "stratum", "strat_height", "rayon_m", "seuil_diam_cm",
                    "unite_azimut", "declinaison_deg", "convergence_deg", "etat_traitement")
                  %in% names(g$placettes)))

  # Colonnes de plan et géométrie intactes.
  expect_equal(g$placettes$wgt, c(24.6, 24.6))
  expect_equal(g$placettes$ip, c(0.0407, 0.0407))
  expect_equal(unname(sf::st_coordinates(g$placettes)[1, ]), c(925000, 6700000))

  arbres <- g$arbres[g$arbres$etat_detection != "faux_positif" | is.na(g$arbres$etat_detection), ]
  expect_gt(nrow(arbres), 10)
  arbres$espece <- vers_espece(arbres$essence_arbre)
  expect_true(all(arbres$espece[!is.na(arbres$essence_arbre)] %in%
                  list_species_classes("BFC")$code))

  v <- validate_field_data(g$placettes, arbres)
  expect_true(v$ok, info = paste(capture.output(print(v$errors)), collapse = "\n"))

  agg <- aggregate_plot_metrics(g$placettes, arbres, plot_radius = 20)
  p1 <- agg[agg$plot_id == "P001", ]
  expect_equal(p1$field_n_trees, sum(arbres$plot_id == "P001"))
  expect_true(is.finite(p1$field_g_ha) && p1$field_g_ha > 0)
  expect_equal(agg$field_n_trees[agg$plot_id == "P002"], 0L)
})

test_that("the definitive server result is a readable detections layer", {
  d <- sf::st_read(definitif, layer = "detections", quiet = TRUE)
  expect_true(all(c("uuid", "plot_id", "num_provisoire", "dbh_cm", "etat_detection", "methode") %in% names(d)))
  expect_equal(sf::st_crs(d)$epsg, 2154L)
  expect_true(all(d$etat_detection == "definitif"))
})
