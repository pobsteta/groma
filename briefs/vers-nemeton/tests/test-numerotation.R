# testthat (edition 3) — shared numbering cases (fixtures/numerotation/*.json).
# In nemeton, copy the JSON files into tests/testthat/fixtures/numerotation/.
# Vecteur nommé -> liste triée par nom (vide compris), pour comparer sans
# dépendre de l'ordre ni des attributs des vecteurs vides.
en_table <- function(v) {
  v <- unlist(v)
  if (!length(v)) return(list())
  as.list(v[sort(names(v))])
}

dossier <- Sys.getenv("GROMA_FIXTURES",
                      testthat::test_path("fixtures", "numerotation"))

for (chemin in sort(Sys.glob(file.path(dossier, "*.json")))) {
  cas <- jsonlite::fromJSON(chemin, simplifyVector = FALSE)
  testthat::test_that(paste("numerotation :", cas$id), {
    a <- cas$arbres
    p <- cas$parametres
    res <- numeroter_arbres(
      x = vapply(a, `[[`, numeric(1), "x"),
      y = vapply(a, `[[`, numeric(1), "y"),
      dbh_cm = vapply(a, function(t) as.numeric(t$dbh_cm), numeric(1)),
      id = vapply(a, `[[`, character(1), "id"),
      rayon_m = p$rayon_m, seuil_diam_cm = p$seuil_diam_cm,
      unite_azimut = p$unite_azimut, tolerance_deg = p$tolerance_deg
    )
    att <- cas$attendu
    num <- !is.na(res$tree_id)
    testthat::expect_identical(en_table(stats::setNames(res$tree_id[num], res$id[num])),
                               en_table(att$numeros))
    exc <- !is.na(res$exclusion)
    testthat::expect_identical(en_table(stats::setNames(res$exclusion[exc], res$id[exc])),
                               en_table(att$exclus))
    if (!is.null(att$visibles)) {
      testthat::expect_setequal(res$id[res$visible], unlist(att$visibles))
    }
    for (k in names(att$azimuts)) {
      testthat::expect_equal(res$azimut[res$id == k], att$azimuts[[k]], tolerance = 1e-4)
    }
  })
}
