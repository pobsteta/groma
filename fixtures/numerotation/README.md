# Cas de test communs : numérotation des arbres

Ces fichiers JSON sont le **contrat** de la règle de numérotation. Trois
implémentations doivent rendre exactement le même résultat :

| Langage | Implémentation | Test |
|---|---|---|
| Python | `core/src/groma/numerotation.py` | `core/tests/test_numerotation.py` |
| Kotlin | `android/groma-core/.../Numerotation.kt` | `android/groma-core/src/test/.../NumerotationTest.kt` |
| R (nemeton) | `numeroter_arbres()` (brief nemeton) | `briefs/vers-nemeton/numerotation.R` + `test-numerotation.R` |

Les résultats attendus sont écrits à la main à partir de la règle (voir
`generer.py`), jamais recopiés depuis une implémentation.

## Format

```json
{
  "id": "02_masque_fin_de_tour",
  "description": "…",
  "parametres": {"rayon_m": 20, "seuil_diam_cm": 7.5, "unite_azimut": "deg", "tolerance_deg": 1},
  "arbres": [{"id": "A", "x": 1.045, "y": 11.954, "dbh_cm": 30}],
  "attendu": {
    "numeros": {"B": "1", "A": "5"},
    "exclus": {"X": "hors_rayon"},
    "visibles": ["B"],
    "azimuts": {"A": 5.0}
  }
}
```

* `x` vers l'est, `y` vers le **nord géographique**, en mètres depuis le
  centre, distances horizontales. Les coordonnées Lambert-93 doivent
  d'abord être tournées de la convergence des méridiens
  (`convergence_deg` de la couche `placettes`).
* `numeros` : `tree_id` attendu (chaîne) de chaque arbre retenu.
* `exclus` : motif d'exclusion (`hors_rayon` ou `sous_seuil`).
* `visibles` (facultatif) : arbres retenus visibles depuis le centre.
* `azimuts` (facultatif) : azimut attendu dans l'unité de la placette
  (tolérance 1e-4, les coordonnées étant arrondies au micromètre).

## Règle (normative)

1. **Arbres retenus** : distance horizontale ≤ `rayon_m` et
   `dbh_cm` ≥ `seuil_diam_cm` (bornes incluses, marge 1e-9).
2. **Visibilité** : un arbre retenu est caché lorsqu'un autre arbre retenu,
   plus proche du centre, a un tronc (disque de diamètre `dbh`) traversé
   par la ligne de vue du centre au centre de l'arbre : distance du centre
   de l'arbre masquant au segment strictement inférieure à son rayon. Un
   tronc qui contient le centre est ignoré.
3. **N° 1** : `az_min` = plus petit azimut des arbres visibles ;
   `C` = arbres visibles avec `az − az_min < tolérance` (pas de bouclage
   par 360°). Le n° 1 est l'arbre de `C` le plus proche (puis le plus petit
   azimut, puis l'identifiant).
4. **Azimut relatif** des autres arbres : `rel = (az − az_1) mod 360`, sauf
   les autres membres de `C`, à `rel = 0`. Un arbre caché d'azimut
   inférieur à celui du n° 1 arrive donc en fin de tour.
5. **Ordre** : tri par `(rel, distance, id)`, puis balayage : un groupe
   commence au premier arbre restant et prend les suivants tant que
   `rel − rel_premier < tolérance` ; dans un groupe, tri par
   `(distance, rel, id)`. Numéros consécutifs à partir de 2.

La tolérance d'égalité est toujours exprimée en degrés (1° par défaut),
même pour une placette en grades. Les comparaisons d'identifiants sont
lexicographiques (octets UTF-8).
