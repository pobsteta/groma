# Mission 035 — Placettes de 15 m de rayon par défaut

## Mission

**En une phrase :** passer le rayon par défaut des placettes de 20 m à 15 m (707 m²), comme les placettes
de calibration LiDAR de l'ONF, seuil de diamètre inchangé (7,5 cm), parce que Pascal trouve les placettes
de 20 m trop grandes après ses premiers tournages (décision du 2026-11-13).

**Temps de la main :** appuyer — réalisé par l'orchestrateur.

## Réalisé

- Schéma : `placettes.rayon_m` défaut 15 (version inchangée : aucune colonne ne change).
- Replis à 15 m : `groma.placette`, `groma_server.paquet.ParametresPlacette`, `ParametresPlacette`
  (Kotlin), `Projet.parametres`. Numérotation inchangée (rayon toujours passé, fixtures explicites).
- Contrôle de couverture du tournage sur le rayon de la placette : `ControleQualite.pourRayon`
  (anneaux à 35, 70, 100 % ; identique à l'ancien pour 20 m), utilisé par `CaptureActivity`.
- Scène synthétique du serveur : rayon 20 m explicite (ses arbres vont jusqu'à 20 m).
- Brief `briefs/vers-nemeton/2026-11-13-rayon-15-m.md` (défaut 15 dans `inventaire_video.R`), copie
  déposée.
- Les projets déjà préparés gardent leur rayon (écrit dans chaque placette) : le projet de Marsannay
  reste à 20 m tant qu'il n'est pas repréparé.

## Essais

- `ControleQualiteTest.placetteDe15m` ; `test_gpkg` (défaut 15) ; pytest complet et tests Kotlin verts.
