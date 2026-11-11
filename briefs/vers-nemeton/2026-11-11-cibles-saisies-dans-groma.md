# Brief vers nemeton : la couche `cibles` du projet QField passe en lecture seule (schéma 1.3.0)

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemeton/2026-11-11-cibles-saisies-dans-groma.md`).
* **Destinataire** : `nemeton` (`creer_projet_inventaire_video()` et sa
  symbologie).
* **Type de changement** : `feat:`. À traiter après la fusion de la mission 016
  de Groma ; Groma n'en dépend pas pour fonctionner.

## 1. Contexte

Décision de Pascal du 2026-11-11 : les cibles C1 à C3 ne se saisissent plus
dans QField mais dans la vue « Cibles » de l'application Groma, qui montre la
placette et son plan pendant la saisie (l'azimut des cibles oriente toute la
placette). Groma écrit la couche `cibles` du GeoPackage du projet.

## 2. Changements du schéma (source : `core/src/groma/donnees/inventaire_video.schema.json`, version 1.3.0)

* Colonne `source_azimut` (TEXT) sur `cibles`, domaine `source_azimut` :
  `boussole_visee`, `boussole_telephone`.
* Couche `cibles` : `lecture_seule: true` (était `false`).
* Colonnes inchangées : `plot_id`, `id_cible` (C1, C2, C3), `azimut_mesure`
  (azimut magnétique, unité de la placette), `distance_mesuree_m`,
  `dbh_mesure_cm`. La géométrie (Lambert 93) est calculée par Groma.

## 3. À faire dans nemeton

1. Créer la couche `cibles` avec la colonne `source_azimut` et le domaine
   (liste de valeurs) ci-dessus.
2. Couche `cibles` **lecture seule** dans le projet QField : plus de
   formulaire de saisie, couche affichée avec la symbologie existante.
3. Mettre à jour la version du schéma lue (1.3.0) et l'exemple de référence
   `exemples/inventaire_video/` (régénéré dans ce dépôt).

## 4. Projets déjà générés

La couche `cibles` y reste éditable dans QField, mais **Groma ne relit pas la
copie de QField** : une cible saisie ou modifiée dans QField n'est pas reprise
(dit dans `docs/terrain.md`). Les cibles déjà présentes dans le projet de
Groma restent lues et affichées, et peuvent être replacées. Groma ajoute la colonne `source_azimut` à la
couche d'un projet plus ancien à la première cible placée.
