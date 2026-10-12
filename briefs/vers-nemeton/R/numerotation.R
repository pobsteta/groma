# numerotation.R — reference implementation of the Groma tree numbering rule,
# proposed for nemeton (`numeroter_arbres()`). Same algorithm as Python
# (core/src/groma/numerotation.py) and Kotlin (Numerotation.kt); the shared
# cases are in fixtures/numerotation/. Roxygen in English, comments in French
# (nemeton conventions).

#' Number the trees of a circular plot (Groma rule)
#'
#' @param x,y Numeric. Horizontal offsets from the plot centre (m), x towards
#'   east, y towards geographic north (rotate Lambert-93 offsets by the
#'   meridian convergence first).
#' @param dbh_cm Numeric. Diameter at 1.30 m (cm).
#' @param id Character. Stable tree identifiers (uuid).
#' @param rayon_m Plot radius (m). Default 20.
#' @param seuil_diam_cm Minimal diameter (cm). Default 7.5.
#' @param unite_azimut `"deg"` or `"gr"`.
#' @param tolerance_deg Azimuth tie tolerance, always in degrees. Default 1.
#'
#' @return A data.frame with `id`, `tree_id` (character, NA when excluded),
#'   `exclusion` (`NA`, `"hors_rayon"`, `"sous_seuil"`), `visible`,
#'   `azimut` (plot unit) and `distance_m`, in the input order.
#' @export
numeroter_arbres <- function(x, y, dbh_cm, id,
                             rayon_m = 20, seuil_diam_cm = 7.5,
                             unite_azimut = c("deg", "gr"),
                             tolerance_deg = 1) {
  unite_azimut <- match.arg(unite_azimut)
  n <- length(id)
  stopifnot(length(x) == n, length(y) == n, length(dbh_cm) == n)
  if (anyDuplicated(id)) stop("Duplicated tree identifiers.")
  eps <- 1e-9
  id <- as.character(id)
  d <- sqrt(x^2 + y^2)
  az <- (atan2(x, y) * 180 / pi) %% 360
  exclusion <- ifelse(d > rayon_m + eps, "hors_rayon",
                      ifelse(dbh_cm < seuil_diam_cm - eps, "sous_seuil", NA_character_))
  eligible <- is.na(exclusion)

  # Visibilité : un tronc plus proche, traversé par la ligne de vue, masque.
  masque <- function(i) {
    for (j in which(eligible)) {
      if (j == i) next
      r_j <- dbh_cm[j] / 200
      if (d[j] >= d[i] || d[j] <= r_j) next
      l2 <- x[i]^2 + y[i]^2
      t <- if (l2 == 0) 0 else max(0, min(1, (x[j] * x[i] + y[j] * y[i]) / l2))
      if (sqrt((x[j] - t * x[i])^2 + (y[j] - t * y[i])^2) < r_j) return(TRUE)
    }
    FALSE
  }
  visible <- rep(FALSE, n)
  for (i in which(eligible)) visible[i] <- !masque(i)

  tree_id <- rep(NA_character_, n)
  if (any(eligible)) {
    # Comparaisons d'identifiants en octets (même ordre que Python et Kotlin).
    rang_id <- rank(vapply(id, function(s) paste(sprintf("%03d", as.integer(charToRaw(enc2utf8(s)))), collapse = ""), ""), ties.method = "first")
    vis <- which(visible)
    az_min <- min(az[vis])
    cand <- vis[az[vis] - az_min < tolerance_deg]
    premier <- cand[order(d[cand], az[cand], rang_id[cand])][1]
    rel <- (az - az[premier]) %% 360
    rel[cand] <- 0
    autres <- setdiff(which(eligible), premier)
    autres <- autres[order(rel[autres], d[autres], rang_id[autres])]
    ordre <- premier
    k <- 1
    while (k <= length(autres)) {
      ancre <- rel[autres[k]]
      j <- k
      while (j <= length(autres) && rel[autres[j]] - ancre < tolerance_deg) j <- j + 1
      groupe <- autres[k:(j - 1)]
      ordre <- c(ordre, groupe[order(d[groupe], rel[groupe], rang_id[groupe])])
      k <- j
    }
    tree_id[ordre] <- as.character(seq_along(ordre))
  }

  azimut <- if (unite_azimut == "gr") az / 0.9 else az
  data.frame(id = id, tree_id = tree_id, exclusion = exclusion,
             visible = visible, azimut = azimut, distance_m = d,
             stringsAsFactors = FALSE)
}
