# Mission 032 — Les arbres ajoutés à la main suivent C0 relevé à nouveau

## Mission

**En une phrase (quoi, et pourquoi) :** quand C0 est relevé à nouveau, recalculer aussi la position des
arbres ajoutés à la main, placés comme les cibles depuis C0 (décision de Pascal du 2026-11-13 : « fais
suivre aussi les arbres ajoutés à la main »), suite de la mission 031.

**Temps de la main :** appuyer — réalisé par l'orchestrateur, essai de Pascal.

## Réalisé

- `GeoPackage.releverC0` recalcule, dans la même transaction que le centre et les cibles, le point des
  lignes `arbres` de la placette avec `methode = 'terrain'`, depuis `azimut` (géographique, unité de la
  placette, tel qu'écrit par `ajouterArbre`) et `distance_m`, avec la convergence de la placette. Aucun
  autre attribut ne change (règle 5) ; les arbres de la vidéo ne bougent jamais.
- `CentreReleve.arbresRecales`, `C0Enregistre.arbresRecales` ; message « Arbres ajoutés A1, A2 recalés
  sur le nouveau C0 (azimut et distance inchangés). » ; la confirmation de remplacement l'annonce.

## Essais

- `RecalageCiblesTest.arbresAjoutesSuivent` (déclinaison et convergence 0 puis 2°, positions écrites à
  la main) et `arbresDeLaVideoImmobiles`.
- Essai de fumée `releverC0_ciblesSuivent` étendu à un arbre ajouté : 13/13 sur l'émulateur.
