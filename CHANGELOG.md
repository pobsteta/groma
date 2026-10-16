# Changelog

## [0.7.0](https://github.com/pobsteta/groma/compare/v0.6.0...v0.7.0) (2026-10-16)


### Nouveautés

* **android:** build, licence et code source dans « À propos » ([#37](https://github.com/pobsteta/groma/issues/37)) ([dcfe849](https://github.com/pobsteta/groma/commit/dcfe8499b042272de0402718850551fd9e9500d3))
* **qfield:** symbologie de Marculus pour les couches du projet QField ([#38](https://github.com/pobsteta/groma/issues/38)) ([2909744](https://github.com/pobsteta/groma/commit/2909744077286e40b7b06b5f8563ddd0750e8e90))

## [0.6.0](https://github.com/pobsteta/groma/compare/v0.5.0...v0.6.0) (2026-10-16)


### Nouveautés

* **android:** service de partage avec QField qui garde le serveur local en vie ([#34](https://github.com/pobsteta/groma/issues/34)) ([5886ab8](https://github.com/pobsteta/groma/commit/5886ab807edf8b8aa1607cc637888113f55fa551))


### Documentation

* **briefs:** annexe nemeton pour le plugin QField et la déclinaison WMM2025 ([#35](https://github.com/pobsteta/groma/issues/35)) ([aa885df](https://github.com/pobsteta/groma/commit/aa885df72d51b9e3af081e9bb581575e259ca042))

## [0.5.0](https://github.com/pobsteta/groma/compare/v0.4.1...v0.5.0) (2026-10-16)


### Nouveautés

* **core:** déclinaison magnétique WMM2025, calculée par groma-preparer et par Groma si absente ([#32](https://github.com/pobsteta/groma/issues/32)) ([272cd38](https://github.com/pobsteta/groma/commit/272cd384cb6cc0509a59037001a2434653fc6f09))
* **qfield:** prototype du plugin de projet QField pour les détections et les centres mesurés ([#31](https://github.com/pobsteta/groma/issues/31)) ([1cb170c](https://github.com/pobsteta/groma/commit/1cb170ca76d91804bdd1b07babf04af431c54eb0))

## [0.4.1](https://github.com/pobsteta/groma/compare/v0.4.0...v0.4.1) (2026-10-15)


### Corrections

* **carte:** flèche de navigation pour « Aller à la prochaine placette », distincte de « Ma position » ([#29](https://github.com/pobsteta/groma/issues/29)) ([076c5db](https://github.com/pobsteta/groma/commit/076c5db1dddac02cc1816eab401b95f5dba21369))

## [0.4.0](https://github.com/pobsteta/groma/compare/v0.3.0...v0.4.0) (2026-10-15)


### Nouveautés

* **android:** écran Carte des placettes, du parcours et des parcelles ([#21](https://github.com/pobsteta/groma/issues/21)) ([afc1b56](https://github.com/pobsteta/groma/commit/afc1b565cc849aae5bb8ccdee88778f9431fd8d7))
* **android:** GNSS et RTK sur la carte, récepteur et corrections NTRIP ([#22](https://github.com/pobsteta/groma/issues/22)) ([49c3dd8](https://github.com/pobsteta/groma/commit/49c3dd8bef8684d539c0700bde9ca7d44c68d70d))
* **carte:** « Mesurer le centre » au RTK, écrit dans centres_mesures ([#25](https://github.com/pobsteta/groma/issues/25)) ([f2779c8](https://github.com/pobsteta/groma/commit/f2779c839ab1136da7dd95e2d42e31e1b0ce2fbb))
* **carte:** « Y aller » vers une placette, distance, azimut magnétique et flèche ([#24](https://github.com/pobsteta/groma/issues/24)) ([0cc19f5](https://github.com/pobsteta/groma/commit/0cc19f56c5ea30dd70362202a9c6a884154711fe))
* **carte:** parcellaire des UGF du projet sur la carte ([#23](https://github.com/pobsteta/groma/issues/23)) ([af551dc](https://github.com/pobsteta/groma/commit/af551dc29e5e092ed71100aa117f8fc5e9e2096f))
* **carte:** téléchargement de la zone du projet pour la carte hors ligne ([#26](https://github.com/pobsteta/groma/issues/26)) ([73bc8ae](https://github.com/pobsteta/groma/commit/73bc8ae11412f42d65b4935da51321094984098a))
* **core:** couche parcelles et projections pour la carte du téléphone ([#19](https://github.com/pobsteta/groma/issues/19)) ([eca8c87](https://github.com/pobsteta/groma/commit/eca8c8734ad5c1020c68bcf6a67376c5e383f762))
* **projets:** tri des projets par distance au téléphone ([#27](https://github.com/pobsteta/groma/issues/27)) ([dbdfa6b](https://github.com/pobsteta/groma/commit/dbdfa6b6a988cc3896654a7b6434a8fd0b5f7828))


### Corrections

* **app:** plantage de la prise de vue avec OpenCV 5, bouton sous la barre système ([#18](https://github.com/pobsteta/groma/issues/18)) ([5b35299](https://github.com/pobsteta/groma/commit/5b352998d934194bcb32c43a1ec32da65a072975))


### Documentation

* **terrain:** carte du projet, guidage et mesure du centre au RTK ([#28](https://github.com/pobsteta/groma/issues/28)) ([91396d0](https://github.com/pobsteta/groma/commit/91396d079f505eaffdc40567bb01a84136bd128c))

## [0.3.0](https://github.com/pobsteta/groma/compare/v0.2.0...v0.3.0) (2026-10-14)


### Nouveautés

* **android:** écran Projets, plusieurs plans d'échantillonnage sur le téléphone ([#14](https://github.com/pobsteta/groma/issues/14)) ([ba44dcd](https://github.com/pobsteta/groma/commit/ba44dcd53891a9211dac04b519d530113b586d36))
* **core:** table projet dans le GeoPackage, remplie par groma-preparer ([#11](https://github.com/pobsteta/groma/issues/11)) ([7bffb59](https://github.com/pobsteta/groma/commit/7bffb59c1850495df1d05a48149a9d2d0ab49353))
* identifiant du projet dans les paquets, les envois et le serveur ([#13](https://github.com/pobsteta/groma/issues/13)) ([d699245](https://github.com/pobsteta/groma/commit/d699245465bd28fc6ad0db9c28c698fd7465cd26))


### Documentation

* briefs partage entre équipes et carte RTK ([#16](https://github.com/pobsteta/groma/issues/16)) ([1c95715](https://github.com/pobsteta/groma/commit/1c95715c1ec157a7955eaf1de1f275bff87ed87b))
* déposer les projets sur le téléphone et les mettre à jour ([#15](https://github.com/pobsteta/groma/issues/15)) ([50067a0](https://github.com/pobsteta/groma/commit/50067a05b8509aed6e7e330abf0c7da6594b3185))

## [0.2.0](https://github.com/pobsteta/groma/compare/v0.1.0...v0.2.0) (2026-10-13)


### Nouveautés

* **android:** interface et icône dans le style de Marculus ([#6](https://github.com/pobsteta/groma/issues/6)) ([0444499](https://github.com/pobsteta/groma/commit/044449946c9581af7ac4a526a1fdb105441e8b39))
* **core:** groma-preparer, projet Groma à partir du plan d'échantillonnage ([#7](https://github.com/pobsteta/groma/issues/7)) ([941a5a3](https://github.com/pobsteta/groma/commit/941a5a3b6f9e1758cf110abe79f1520f993c667b))


### Documentation

* clé de signature de l'APK de release ([#8](https://github.com/pobsteta/groma/issues/8)) ([2a1987b](https://github.com/pobsteta/groma/commit/2a1987b8a6fcc1a7fcb4dd9397ca0dd8300120b7))

## 0.1.0 (2026-10-12)


### Nouveautés

* Groma, inventaire forestier par vidéo (prototype) ([#1](https://github.com/pobsteta/groma/issues/1)) ([162aa0e](https://github.com/pobsteta/groma/commit/162aa0e8f5d29a473d7b0b128d3716c9ddaa582f))
