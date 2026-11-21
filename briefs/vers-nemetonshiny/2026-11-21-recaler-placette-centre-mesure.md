# Brief vers nemetonshiny : recaler une placette du plan sur son centre mesuré

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemetonshiny/2026-11-21-recaler-placette-centre-mesure.md`).
* **Destinataire** : `nemetonshiny`, plan d'échantillonnage du projet
  (`data/samples.gpkg`, couche `plots`) et sous-onglet Terrain → « Inventaire
  vidéo ».
* **Type de changement** : `feat:`.
* **Décision** : Pascal, 2026-11-21 : « c'est le terrain qui fait foi ».
* **État** : le recalage de P067 (§ 2, points 1 et 2) a été **fait le
  2026-11-21 par Groma**, avec l'autorisation de Pascal, directement dans
  `data/samples.gpkg` (seule la géométrie de P067 a changé ; index R*Tree
  mis à jour par GDAL ; intégrité vérifiée). Copie d'avant :
  `~/groma-sauvegardes/2026-11-21_samples-avant-recalage-P067/` (avec un
  `LISEZMOI.md`). Restent pour nemetonshiny : vérifier que l'application
  relit le plan modifié (elle tournait pendant la modification ; une copie
  en mémoire ne doit pas réécrire l'ancien point), puis les points 3 et 4.

## 1. Besoin

Sur la forêt communale de Marsannay-la-Côte (projet
`20261015_194200_yzoh`), la placette **P067** a été installée et filmée à
**151,9 m** de son point prévu. Le centre mesuré sur le terrain fait foi :
le plan d'échantillonnage doit être corrigé pour que P067 soit à ce centre,
et que le parcours (ligne des placettes dans l'ordre `visit_order`) passe
par lui.

| | Lambert-93 (EPSG:2154), x ; y en m |
|---|---|
| Point prévu (`samples.gpkg`, `plots`, P067) | 849 508,41 ; 6 689 219,98 |
| Centre mesuré du 2026-11-20 15 h 16 (UTC), retenu | **849 659,22 ; 6 689 237,97** |
| Centre mesuré du 2026-11-13 (plus ancien, non retenu) | 849 653,33 ; 6 689 233,55 |

Les deux centres viennent du GNSS du téléphone (`qualite_gnss` =
`telephone`, précision annoncée 3,79 m) et figurent dans la table
`centres_mesures` du projet du téléphone. Comme partout dans Groma, c'est
le **plus récent** qui compte.

## 2. À faire

1. **Recaler P067** dans `samples.gpkg` (`plots`) : géométrie = centre
   mesuré retenu ci-dessus. Les autres attributs (`plot_id`, `type`,
   `visit_order` 67, `stratum`, `strat_*`, `wgt`, `ip`) sont **inchangés** ;
   le parcours garde son ordre et passe désormais par le nouveau point.
2. **Garder la trace** du point prévu d'origine et de la raison du
   déplacement (« recalée sur le centre mesuré le 2026-11-20 »). Fait pour
   P067 par la copie de sauvegarde et son `LISEZMOI.md`, sans toucher au
   schéma de `plots` ; pour l'action générale (point 3), à l'endroit que
   nemetonshiny juge le plus adapté. Rien n'est perdu.
3. **De façon générale** (souhaitable, pas indispensable pour P067) : une
   action « Recaler sur le centre mesuré » par placette, qui lit le dernier
   centre de `centres_mesures` dans les résultats récupérés du serveur Groma
   (« Récupérer les résultats ») et applique les points 1 et 2. Jamais
   automatique : c'est l'opérateur qui choisit les placettes.
4. Les cartes et exports de nemetonshiny qui montrent le plan ou le
   parcours (dont la carte « Parcours ») montrent P067 au nouveau point.

## 3. Points d'attention

* **Le centre mesuré est hors de la forêt du projet** : à 107 m de toute
  UGF et de toute parcelle (les plus proches : UGF 18, parcelle
  21390 A 21). Le recalage ne doit **pas** être refusé pour cette raison ;
  un avertissement suffit. La décision de garder les poids (`wgt`, `ip`) et
  la strate de la placette est celle de Pascal (terrain qui fait foi) ;
  s'il faut les revoir, le signaler plutôt que les changer.
* **Projet déjà sur le téléphone** : P067 y est commencée, donc Groma
  refuse la mise à jour du projet (`docs/terrain.md`, « Mettre à jour un
  projet »). Inutile de renvoyer le projet au téléphone pour ce recalage ; il
  vaudra pour les projets générés ensuite (`creer_projet_inventaire_video()`
  lit le plan recalé). Sur le téléphone, la carte relie déjà le point prévu
  au centre mesuré.
* **Analyse** : `importer_inventaire_video()` et `rapprocher_arbres()` de
  nemeton prennent déjà le dernier centre de `centres_mesures` comme centre
  de placette (brief nemeton `2026-10-15-inventaire-video-carte.md`, § 3) :
  rien ne change côté nemeton.

## 4. Critères d'acceptation

* Dans `samples.gpkg`, P067 est au point 849 659,22 ; 6 689 237,97
  (EPSG:2154, au centimètre), avec les mêmes attributs qu'avant.
* Les 105 autres placettes sont inchangées.
* Le point prévu d'origine de P067 est retrouvable.
* La carte « Parcours » passe par le nouveau point de P067, entre P066 et
  P068.
* Un projet inventaire vidéo généré ensuite a P067 au nouveau point, et
  son `ecart_prevu_m` est proche de 0 pour le centre du 2026-11-20.
