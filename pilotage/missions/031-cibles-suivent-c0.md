# Mission 031 — Les cibles suivent C0 relevé à nouveau

## Mission

**En une phrase (quoi, et pourquoi) :** quand C0 est relevé à nouveau, recalculer la position des
cibles C1–C3 déjà placées à partir du nouveau C0, parce qu'elles gardaient les coordonnées calculées
depuis l'ancien C0 (décision de Pascal du 2026-11-13 : « fais suivre les cibles au nouveau C0 »).

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Redire en une phrase ? | oui | |
| Sait-il comment faire ? | oui | `GeoPackage.releverC0` (cœur), `Projet.ajouterCentre`, `ReleveC0.kt` (message) |
| A-t-il les moyens ? | oui | tests JVM, essai de fumée sur émulateur |

**Temps de la main :** appuyer — réalisé par l'orchestrateur (petite mission), essai de Pascal.

## Réalisé

- `GeoPackage.releverC0(db, plot, x, y, qualite, precision, ecart, OrientationPlacette, date, reception)` :
  ajoute le centre (jamais réécrit) et, **dans la même transaction**, recalcule le point de chaque
  cible de la placette avec `PlacementCible.position` (azimut saisi `azimut_mesure`, magnétique, unité
  de la placette ; déclinaison et convergence comme `Projet.placerCible`). Azimut, distance, diamètre et
  source restent intacts (règle 5) ; une ligne sans azimut ou sans distance n'est pas touchée.
  Renvoie `CentreReleve(fid, ciblesRecalees)`.
- `Projet.ajouterCentre` renvoie `C0Enregistre(ecartM, ciblesRecalees)` ; le message de fin de relevé
  ajoute « Cibles C1, C2 recalées sur le nouveau C0 (azimut et distance inchangés). » ; la
  confirmation de remplacement d'un C0 annonce que les cibles suivront.
- Les arbres ajoutés à la main (`methode = 'terrain'`) **ne suivent pas** : hors du périmètre demandé.

## Essais

- `RecalageCiblesTest` (4 cas, positions écrites à la main : degrés, déclinaison 2°, grades, cible sans
  distance, autre placette, centres antérieurs intacts).
- Essai de fumée `releverC0_ciblesSuivent` sur le projet ancien (R*Tree de GDAL) : 13/13 sur l'émulateur
  (seuil de stockage bas abaissé le temps de l'essai, rétabli ensuite).
