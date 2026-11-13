# Changelog

## [2026.11.7](https://github.com/pobsteta/groma/compare/v2026.11.6...v2026.11.7) (2026-11-13)


### Nouveautés

* **app:** bouton boussole sur la carte, nord en haut ou cap en haut (mission 034) ([#107](https://github.com/pobsteta/groma/issues/107)) ([4af49c9](https://github.com/pobsteta/groma/commit/4af49c9f08fd7b12e0539e12e7210715e3a42e54))
* **schema:** placettes de 15 m de rayon par défaut (mission 035) ([#109](https://github.com/pobsteta/groma/issues/109)) ([ac93cb4](https://github.com/pobsteta/groma/commit/ac93cb43475c8644047ccc81ebc9e0497242287b))


### Corrections

* **app:** images de capture illisibles, première ligne du capteur répétée (mission 033) ([#106](https://github.com/pobsteta/groma/issues/106)) ([468f689](https://github.com/pobsteta/groma/commit/468f68904faf41b59fdc9bea3b1f162f4ff1c0f0))

## [2026.11.6](https://github.com/pobsteta/groma/compare/v2026.11.5...v2026.11.6) (2026-11-13)


### Nouveautés

* **app:** les arbres ajoutés à la main suivent C0 relevé à nouveau (mission 032) ([#104](https://github.com/pobsteta/groma/issues/104)) ([e2516a3](https://github.com/pobsteta/groma/commit/e2516a344155d1beca830b023b95685fb2c7ad31))

## [2026.11.5](https://github.com/pobsteta/groma/compare/v2026.11.4...v2026.11.5) (2026-11-13)


### Nouveautés

* **app:** les cibles suivent C0 relevé à nouveau (mission 031) ([#102](https://github.com/pobsteta/groma/issues/102)) ([6244f14](https://github.com/pobsteta/groma/commit/6244f14fe47dd495d25bfecbc0351804d0bb1a1d))

## [2026.11.4](https://github.com/pobsteta/groma/compare/v2026.11.3...v2026.11.4) (2026-11-12)


### Nouveautés

* **app:** « Relever C0 » partout et depuis la vue Placette ([#100](https://github.com/pobsteta/groma/issues/100)) ([858fb11](https://github.com/pobsteta/groma/commit/858fb11edb5b9fa94725dd7b0bf3c3049600a037))

## [2026.11.3](https://github.com/pobsteta/groma/compare/v2026.11.2...v2026.11.3) (2026-11-12)


### Nouveautés

* **app:** centre C0 toujours visible dans la vue Placette et mesurable depuis la fiche de placette ([#98](https://github.com/pobsteta/groma/issues/98)) ([e551c57](https://github.com/pobsteta/groma/commit/e551c57eaf319917bab500ded213a725ce4d311b))

## [2026.11.2](https://github.com/pobsteta/groma/compare/v2026.11.1...v2026.11.2) (2026-11-12)


### Nouveautés

* **arbres:** saisie des arbres dans Groma, vue « Placette », schéma 1.4.0 ([#91](https://github.com/pobsteta/groma/issues/91)) ([e4ee318](https://github.com/pobsteta/groma/commit/e4ee318137fd8e0b5b69b598b92625891c251856))
* **centre:** centre au GNSS du téléphone, fiche complète d'un arbre ajouté, qualité de la réception, sans index spatial ([#96](https://github.com/pobsteta/groma/issues/96)) ([b6c8f5e](https://github.com/pobsteta/groma/commit/b6c8f5ee8918829d2c83ebae81f71fa9adb10166))
* **cibles:** vue « Cibles » dans Groma, schéma 1.3.0 ([#87](https://github.com/pobsteta/groma/issues/87)) ([32401b2](https://github.com/pobsteta/groma/commit/32401b23d3a837dd3860fc7ef4d20fd1c9173167))
* **terrain:** se passer de QField (envoi de terrain, notes et photos, arbre oublié) ([#94](https://github.com/pobsteta/groma/issues/94)) ([828d266](https://github.com/pobsteta/groma/commit/828d266c0e0c4903af6939c17fdfb1dcf823a646))


### Corrections

* **app:** bouton « Filmer » au lieu de « Filmer la placette » ([#92](https://github.com/pobsteta/groma/issues/92)) ([bf76182](https://github.com/pobsteta/groma/commit/bf76182889667f581f4007d4a412acf68b8c205c))


### Documentation

* **pilotage:** cycle 7 (version 2026.11.1, missions 015-016 en PR [#87](https://github.com/pobsteta/groma/issues/87)) ([#88](https://github.com/pobsteta/groma/issues/88)) ([f7e0379](https://github.com/pobsteta/groma/commit/f7e0379ec75ab0d434061aef6362055878bb7030))
* **pilotage:** missions 015-016 terminées (PR [#87](https://github.com/pobsteta/groma/issues/87) fusionnée, APK installée) ([#90](https://github.com/pobsteta/groma/issues/90)) ([56ef408](https://github.com/pobsteta/groma/commit/56ef408e85a620fcf31b5c573a5bf5b6e4a70783))
* **pilotage:** missions 020-023 terminées (PR [#94](https://github.com/pobsteta/groma/issues/94) fusionnée) ([#95](https://github.com/pobsteta/groma/issues/95)) ([73b789b](https://github.com/pobsteta/groma/commit/73b789ba62a2d53b250897454de8205eed5adf9a))

## [2026.11.1](https://github.com/pobsteta/groma/compare/v0.14.0...v2026.11.1) (2026-11-11)


### Nouveautés

* **server:** planche A4 des cibles ArUco à imprimer ([#83](https://github.com/pobsteta/groma/issues/83)) ([c11153c](https://github.com/pobsteta/groma/commit/c11153ca509fa739c893cb558146b5da2cc2ca83))


### Corrections

* **ci:** passer la version calendaire par la ligne de commande de release-please ([#86](https://github.com/pobsteta/groma/issues/86)) ([83ec53d](https://github.com/pobsteta/groma/commit/83ec53dbcefa6ea3de031268194f831b4d1959e5))


### Documentation

* **pilotage:** cycle 5 (thème terrain-style terminé, deux cartes du bac à sable validées) ([#78](https://github.com/pobsteta/groma/issues/78)) ([be768f1](https://github.com/pobsteta/groma/commit/be768f1f4984e68cb4796900ac977b22ff9eccb6))
* **pilotage:** cycles 6 et 7 (plan 014, missions 015-016 vue « Cibles ») ([#84](https://github.com/pobsteta/groma/issues/84)) ([5a8d398](https://github.com/pobsteta/groma/commit/5a8d398eca0c681bdc0cb45e5ed2e62a27725efa))

## [0.14.0](https://github.com/pobsteta/groma/compare/v0.13.0...v0.14.0) (2026-10-30)


### Nouveautés

* **android:** thème Groma = terrain-style v0.7.1 (couleurs, formes, typographie) ([#77](https://github.com/pobsteta/groma/issues/77)) ([b421a12](https://github.com/pobsteta/groma/commit/b421a12eafab4388b5b648afe01c3cd8b76ed808))
* **benchmark:** banc d'essai de la segmentation des troncs (SAM 2, SAM 3, WeVisOne-VL) ([#69](https://github.com/pobsteta/groma/issues/69)) ([83a5840](https://github.com/pobsteta/groma/commit/83a5840cd55593a34e59edba2a886efdb3505d8a))

## [0.13.0](https://github.com/pobsteta/groma/compare/v0.12.0...v0.13.0) (2026-10-30)


### Nouveautés

* **android:** icône G en New Rocker, comme Surculus et Ramulus ([#70](https://github.com/pobsteta/groma/issues/70)) ([7dd2a00](https://github.com/pobsteta/groma/commit/7dd2a008b6130c3c2dd4329affd9f68270adb694))
* **android:** vue Kanban des projets, colonnes de Marculus et Surculus ([#72](https://github.com/pobsteta/groma/issues/72)) ([d93791b](https://github.com/pobsteta/groma/commit/d93791ba4d44b80f82f3cffb07672ada038b28a0))


### Corrections

* **android:** en plein écran, le bandeau monte jusqu'en haut en portrait ([#74](https://github.com/pobsteta/groma/issues/74)) ([d6d819e](https://github.com/pobsteta/groma/commit/d6d819eb668d64d64675db160b60ce722760ecd0))
* **android:** plein écran jusqu'au bord, sans la bande de la caméra ([#71](https://github.com/pobsteta/groma/issues/71)) ([46f4c1b](https://github.com/pobsteta/groma/commit/46f4c1bd39986a3f07b1ab102e4822672067d17c))


### Documentation

* **pilotage:** cycle 4 (Kanban, plein écran sans bande) ; ANDROID_HOME par défaut dans test-command ([#73](https://github.com/pobsteta/groma/issues/73)) ([b6dd303](https://github.com/pobsteta/groma/commit/b6dd303f91be051045124fb9da2b2a4fb37a86f1))
* **pilotage:** cycles 2 et 3 (anciennes couleurs, choix du thème, plein écran) ([#65](https://github.com/pobsteta/groma/issues/65)) ([0458303](https://github.com/pobsteta/groma/commit/0458303e62bccb183a6c72311bf852e79970556b))
* **pilotage:** fusion de la [#74](https://github.com/pobsteta/groma/issues/74) et essai sur le terrain concluant ([#75](https://github.com/pobsteta/groma/issues/75)) ([a9b9ba3](https://github.com/pobsteta/groma/commit/a9b9ba3e02163ba17e56c9200037fbd681b5e217))

## [0.12.0](https://github.com/pobsteta/groma/compare/v0.11.0...v0.12.0) (2026-10-24)


### Nouveautés

* **android:** réglage Plein écran dans Paramètres ([#62](https://github.com/pobsteta/groma/issues/62)) ([29feaec](https://github.com/pobsteta/groma/commit/29feaec4a30f2b50cee36b43f7354d8ace4ab12c))

## [0.11.0](https://github.com/pobsteta/groma/compare/v0.10.0...v0.11.0) (2026-10-24)


### Nouveautés

* **android:** choix du thème Système, Clair, Sombre ; retour aux anciennes couleurs ([#61](https://github.com/pobsteta/groma/issues/61)) ([14dc589](https://github.com/pobsteta/groma/commit/14dc589a8f701ba0738690d8578d7177543196fd))


### Documentation

* **pilotage:** installer la méthode de l'Impératrice ; thème validé au soleil ([#59](https://github.com/pobsteta/groma/issues/59)) ([9ad811e](https://github.com/pobsteta/groma/commit/9ad811e5100c1b64c9ac830452c1fe82bfb3399f))

## [0.10.0](https://github.com/pobsteta/groma/compare/v0.9.0...v0.10.0) (2026-10-24)


### Nouveautés

* **android:** thème Groma = jetons terrain-style v0.6.0 ([#58](https://github.com/pobsteta/groma/issues/58)) ([6aaf042](https://github.com/pobsteta/groma/commit/6aaf0421e150ba42ea1198b292e39025e9b1eec8))
* **server:** worker distant qui prend les tâches par l'API en HTTP ([#56](https://github.com/pobsteta/groma/issues/56)) ([205960e](https://github.com/pobsteta/groma/commit/205960eeedd142bbf22fb10bd126d19ce25813e1))


### Documentation

* briefs nemeton du 2026-10-17 traités ; identifiants de couches uniques ([#53](https://github.com/pobsteta/groma/issues/53)) ([8883a09](https://github.com/pobsteta/groma/commit/8883a09cda6beef857cbc945ca52983e572d9616))
* **briefs:** worker GPU à la demande sur Scaleway ([#55](https://github.com/pobsteta/groma/issues/55)) ([6948444](https://github.com/pobsteta/groma/commit/69484441b371214a25df7097c29e158a99ab6aeb))

## [0.9.0](https://github.com/pobsteta/groma/compare/v0.8.0...v0.9.0) (2026-10-17)


### Nouveautés

* **android:** afficher et coller le jeton d'accès dans les paramètres ([#51](https://github.com/pobsteta/groma/issues/51)) ([dce918a](https://github.com/pobsteta/groma/commit/dce918a60ce605e94ef1ebeb235b577dc8ac6758))
* **android:** ouvrir le projet du serveur dans QField, plugin compris ([#48](https://github.com/pobsteta/groma/issues/48)) ([ad51eda](https://github.com/pobsteta/groma/commit/ad51edaba6beb1fc5f1d3242ad5290c8936f4f16))
* **android:** projets du serveur, téléchargement reprenable et installation dans le dossier des projets ([#47](https://github.com/pobsteta/groma/issues/47)) ([0747fa6](https://github.com/pobsteta/groma/commit/0747fa634799020a27ff81fcdbc75da2d159cfd2))
* **core:** versions et extraction sûre des projets publiés sur le serveur ([#46](https://github.com/pobsteta/groma/issues/46)) ([baecba3](https://github.com/pobsteta/groma/commit/baecba307339c3deb4fcf30f6824028d65339fa1))
* **server:** dépôt, publication et téléchargement des projets QField pour les téléphones ([#45](https://github.com/pobsteta/groma/issues/45)) ([229ee25](https://github.com/pobsteta/groma/commit/229ee2545851bb9e727bd438b4fa43c598cea408))


### Corrections

* **server:** migration de la file d'attente sûre au démarrage simultané de l'API et du worker ([#50](https://github.com/pobsteta/groma/issues/50)) ([8546c09](https://github.com/pobsteta/groma/commit/8546c099fca24932c85db0bc3eedfe1a1db55666))


### Documentation

* **briefs:** envoi du projet de nemetonshiny au téléphone par le serveur Groma ([#43](https://github.com/pobsteta/groma/issues/43)) ([af6d5a0](https://github.com/pobsteta/groma/commit/af6d5a044e9f2f1a9c0ad814dbe429fa0eeb5d41))
* projet QField complet généré par nemetonshiny, projet de test en dépannage ([#42](https://github.com/pobsteta/groma/issues/42)) ([8b7e10a](https://github.com/pobsteta/groma/commit/8b7e10ad6d9e7b9a04f2b175a87bb8dd9c096aa7))
* recevoir un projet par le serveur et l'ouvrir dans QField ([#52](https://github.com/pobsteta/groma/issues/52)) ([e87bf44](https://github.com/pobsteta/groma/commit/e87bf44aea442cd28498d858a6abb19b395a96d9))

## [0.8.0](https://github.com/pobsteta/groma/compare/v0.7.0...v0.8.0) (2026-10-16)


### Nouveautés

* **qfield:** fonds IGN et OSM, cadastre et houppiers comme Marculus dans le projet QField ([#40](https://github.com/pobsteta/groma/issues/40)) ([4d5f4aa](https://github.com/pobsteta/groma/commit/4d5f4aad5ddf345023ef68b6d6e570e3b5b0acba))

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
