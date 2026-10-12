# Cas de test communs : rapprochement provisoire → définitif

Contrat de `rapprocher_arbres()` (nemeton) et de `groma.rapprochement.rapprocher()`
(Python). Les résultats attendus sont écrits à la main (`generer.py`).

* `provisoires` : lignes de la couche `arbres` revenue du terrain
  (coordonnées Lambert-93 `x`, `y`).
* `definitifs` : détections du serveur.
* `attendu.par_uuid` : valeurs attendues (sous-ensemble des colonnes) pour
  chaque arbre provisoire, repéré par son `uuid`.
* `attendu.nouveaux` : arbres créés par le serveur, repérés par leur position
  (`az` en degrés, `d` en mètres depuis `centre`), à 1 mm près.
* `attendu.numeros_modifies` : triplets `[uuid, ancien, nouveau]`, à comparer
  comme un ensemble.
* `attendu.alertes` (facultatif) : alertes qui doivent figurer parmi les
  alertes rendues (`uuid` + `motif`).
* `attendu.nombre_arbres` (facultatif) : nombre total d'arbres rendus.

Règles : voir l'en-tête de `core/src/groma/rapprochement.py`, reprises dans
le brief nemeton. Critère d'acceptation : aucun attribut saisi sur le terrain
(`essence_arbre`, `statut`, `qualite`, `notes`, `photos`, `h_m` mesurée) n'est
perdu, sur tous les cas.
