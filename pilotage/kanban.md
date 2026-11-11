# Kanban de Groma

> Cinq colonnes (fiche n° 8). Chaque carte porte l'Homme (qui), la Mission (quoi, quand,
> combien) et les Moyens (comment, où, avec qui), et renvoie à sa fiche dans
> `pilotage/missions/`. Elle note aussi depuis quel cycle de `/boucle` elle est dans sa
> colonne ; on change ce numéro chaque fois qu'elle change de colonne.
> **Une carte qui ne bouge pas est souvent la mauvaise nouvelle à dire tout de suite.**

> **PLAN.md reste la source maître de l'avancement et des décisions prises avant la méthode** (choix de Pascal, 2026-10-23). Ce kanban ne suit que les missions confiées par `/imperatrice:boucle`.

**Dernier cycle :** 7 _(augmenté de 1 à la fin de chaque `/boucle`, même quand elle n'a
rien lancé. Une carte est immobile quand dernier cycle − N ≥ 2.)_

_Modèle de carte :_

```
- [NNN-titre](missions/NNN-titre.md) · depuis le cycle N
  - Homme : qui
  - Mission : quoi, quand, combien
  - Moyens : comment, où, avec qui
```

## Bac à sable

- `.claude/test-command` : `ANDROID_HOME` par défaut (`~/Android/Sdk`) ajouté au cycle 4 ; reste à vérifier que le hook de fin passe sous son délai de 630 s · depuis le cycle 4

## Validé

- [026](missions/026-essai-fumee.md) · depuis le cycle 7
  - Homme : dev (sonnet), appuyer
  - Mission : essai de fumée instrumenté comme Surculus (type `fumee`, androidTest sur SQLite d'Android, émulateur et téléphone, étape CI)
  - Moyens : Surculus (lecture), `emulator-5554`, S23 Ultra
## Planifié


## Terminé

- [020](missions/020-test-envoi-terrain.md) · depuis le cycle 7
  - Homme : testeur (sonnet), appuyer
  - Mission : tests de l'archive « terrain » d'une placette (extraction, photos, dépôt au serveur, renvoi, empreinte)
  - Moyens : `/televersements`, `EnvoiWorker.kt`, schéma unique, exemples · PR #94 fusionnée (828d266) après essai de Pascal sur le S23 Ultra
- [021](missions/021-envoi-terrain.md) · depuis le cycle 7
  - Homme : dev (sonnet), appuyer
  - Mission : envoi de terrain au serveur (reprenable, renvoyé à chaque modification), servi à nemetonshiny ; brief nemeton « terrain sans QField »
  - Moyens : tests de 020, `EnvoiWorker.kt`, API du serveur · PR #94 fusionnée (828d266) après essai de Pascal sur le S23 Ultra
- [022](missions/022-notes-photos-arbre.md) · depuis le cycle 7
  - Homme : dev (sonnet), appuyer
  - Mission : notes et photos d'arbre dans la fiche de Groma, parties avec l'envoi de terrain
  - Moyens : `FicheArbre.kt`, `modifierArbre`, convention photo de QField ; essai de Pascal · PR #94 fusionnée (828d266) après essai de Pascal sur le S23 Ultra
- [023](missions/023-arbre-oublie.md) · depuis le cycle 7
  - Homme : dev (sonnet), appuyer
  - Mission : ajouter dans Groma un arbre oublié par la vidéo (azimut, distance, ruban obligatoire), `methode = terrain`
  - Moyens : tests de 020 (point 5), `PlacementCible`, `FicheArbre` ; essai de Pascal · PR #94 fusionnée (828d266) après essai de Pascal sur le S23 Ultra
- [017](missions/017-test-arbres-dans-groma.md) · depuis le cycle 7
  - Homme : testeur (sonnet), appuyer
  - Mission : tests de la saisie des arbres dans Groma (contrôles, priorité du ruban `dbh_source`, rapprochement, serveur local, recopie vers QField sans écrasement)
  - Moyens : `h_source = mesuree` comme modèle, `test_gpkg.py`, `GeoPackageTest`, tests du rapprochement · PR #91 fusionnée (e4ee318) après essai de Pascal sur le S23 Ultra
- [018](missions/018-arbres-dans-groma-donnees.md) · depuis le cycle 7
  - Homme : dev (sonnet), appuyer
  - Mission : schéma `dbh_source`, déclencheurs, écriture Groma dans `arbres`, rapprochement, serveur local, plugin QField, brief nemeton
  - Moyens : tests de 017, `ecrireCible`, `qfield/inventaire_video.qml` · PR #91 fusionnée (e4ee318) après essai de Pascal sur le S23 Ultra
- [019](missions/019-vue-placette-arbres.md) · depuis le cycle 7
  - Homme : dev (sonnet), appuyer
  - Mission : vue « Placette » : arbres sur le plan (couleur d'essence de Marculus), fiche de saisie essence, ruban, statut, qualité
  - Moyens : `EcranCibles.kt`, `qfield/symbologie.py`, 018 ; essai de Pascal sur le S23 Ultra · PR #91 fusionnée (e4ee318) après essai de Pascal sur le S23 Ultra
- [015](missions/015-test-vue-cibles.md) · depuis le cycle 7
  - Homme : testeur (sonnet), appuyer
  - Mission : tests de `PlacementCible` (position depuis le centre, source de l'azimut, alertes de répartition)
  - Moyens : `groma.core.Declinaison`, `georeferencement.py`, `SaisiePointage.kt` de Surculus (lecture) · rendue (61 tests, reprise 1/3), relue « prêt » · PR #87 fusionnée (32401b2)
- [016](missions/016-vue-cibles.md) · depuis le cycle 7
  - Homme : dev (sonnet), appuyer
  - Mission : vue « Cibles » : C1-C3 placées dans Groma (plus dans QField), plan nord en haut, boussole de visée par défaut ou du téléphone
  - Moyens : tests de 015, vue Pointer de Surculus, écriture GeoPackage de `centres_mesures` ; schéma `source_azimut`, brief nemeton ; essai de Pascal sur le S23 Ultra · relue « prêt » (5 points non bloquants) ; réponses de Pascal intégrées (0 accepté, bouton « Cibles » dans la liste, `source_azimut` au serveur) · PR #87 fusionnée (32401b2) avant l'essai, à la demande de Pascal ; installée sur le S23 Ultra le 2026-11-11, essai sur le terrain à faire
- [012](missions/012-test-terrain-carte.md) · depuis le cycle 6
  - Homme : testeur (sonnet), appuyer
  - Mission : tests d'équivalence de `terrain-carte` v0.7.1 avec le code carte de Groma (tuiles, emprise, URL WMTS)
  - Moyens : `ZoneTuilesTest.kt`, `~/dev/terrain/terrain-carte`, brief Marculus du 2026-10-17 · PR #80 fusionnée (65ce940)
- [013](missions/013-terrain-carte.md) · depuis le cycle 6
  - Homme : dev (sonnet), appuyer
  - Mission : la carte de Groma utilise `terrain-carte` v0.7.1, sans changer son comportement
  - Moyens : tests de 012, brief Marculus du 2026-10-17 · PR #80 fusionnée (65ce940) après essai de Pascal sur le S23 Ultra (4 tuiles avant et après, fonds, hors ligne)
- [014](missions/014-couleurs-etat-theme.md) · depuis le cycle 6
  - Homme : afficheur (sonnet), **tenir**
  - Mission : plan des 18 couleurs d'état en dur (`Gnss.kt`, `EcranCarte.kt`) vers le thème, avec contrastes et options de sens
  - Moyens : `Contraste.kt`, `Schema.kt` de terrain-style, `BadgeFix.kt` de Marculus · plan `014-plan.md` prêt après aller-retour 1/3 ; sens tranché par Pascal (A + C, SNR à 4 niveaux, liseré) ; mission de code à écrire au cycle 7
- [010](missions/010-test-theme-terrain-style-071.md) · depuis le cycle 5
  - Homme : testeur (sonnet), appuyer
  - Mission : test d'égalité du thème de Groma avec `terrain-style` v0.7.1 (couleurs, formes, typographie), absorbe 003
  - Moyens : `ThemeTerrainStyleTest.kt` (7 tests verts) · PR #77 fusionnée (b421a12)
- [011](missions/011-theme-terrain-style-071.md) · depuis le cycle 5
  - Homme : dev (sonnet), appuyer
  - Mission : `Theme.kt` lit couleurs, formes et typographie dans `terrain-style` v0.7.1 ; rien ne change à l'écran
  - Moyens : `terrain-style` v0.7.1 (JitPack, GPL-3.0-or-later), tests de 010 · PR #77 fusionnée (b421a12) après essai de Pascal sur le S23 Ultra, 0.14.0
- [003](missions/003-garde-fou-colorscheme.md) · depuis le cycle 4
  - Homme : testeur (sonnet), appuyer
  - Mission : le test du thème échoue aussi si `ColorScheme` a un rôle que `Schema` n'a pas
  - Moyens : `ThemeTerrainStyleTest.kt` sur `main`, AAR `material3-android-1.4.0` · rendue (2d42339, branche `test/003-garde-fou-colorscheme`), absorbée par 010
- [008](missions/008-vue-kanban-projets.md) · depuis le cycle 4
  - Homme : dev (sonnet), appuyer
  - Mission : vue Kanban des projets, colonnes de Marculus et Surculus, minimum imposé par l'avancement
  - Moyens : `EtatKanban.kt` et `VueKanban.kt` de Surculus, `ResumeProjet` · PR #72 fusionnée (d93791b), aller-retour 1/3
- [009](missions/009-plein-ecran-sans-bande.md) · depuis le cycle 4
  - Homme : dev (sonnet), appuyer
  - Mission : en plein écran, plus de bande vide côté caméra, sauf pour les boutons
  - Moyens : `BandeauCompact.kt`, `Scaffold`, `margeBouton` · PR #71 fusionnée (46f4c1b), aller-retour 2/3

- [007](missions/007-plein-ecran.md) · depuis le cycle 3
  - Homme : dev (sonnet), appuyer
  - Mission : réglage « Plein écran » dans Paramètres, appliqué à l'accueil et à la capture, comme Marculus
  - Moyens : `MainActivity.kt` de Marculus l. 51-61, réglages de Groma · PR #62 fusionnée (29feaec), 0.12.0
- [004](missions/004-retour-anciennes-couleurs.md) · depuis le cycle 2
  - Homme : dev (sonnet), appuyer
  - Mission : retour aux anciennes couleurs (bandeau vert), comme Marculus, jusqu'à `terrain-style` v0.6.1
  - Moyens : `Theme.kt` d'avant 6aaf042, PR marculus #12 · PR #61 fusionnée (14dc589), 0.11.0
- [005](missions/005-test-choix-theme.md) · depuis le cycle 2
  - Homme : testeur (sonnet), appuyer
  - Mission : tests de `ChoixTheme` (Système, Clair, Sombre) dans `groma-core`
  - Moyens : Marculus 042797a · PR #61 fusionnée (14dc589)
- [006](missions/006-choix-theme-parametres.md) · depuis le cycle 2
  - Homme : dev (sonnet), appuyer
  - Mission : choix du thème en liste déroulante dans Paramètres, comme Marculus ; une PR avec 004 et 005
  - Moyens : Marculus 042797a et c73c9a0, tests de 005 · PR #61 fusionnée (14dc589), aller-retour 1/3, 0.11.0
- [001](missions/001-test-theme-terrain-style.md) · depuis le cycle 1
  - Homme : testeur (sonnet), appuyer
  - Mission : test d'égalité du `ColorScheme` (48 rôles × 2) avec `terrain-style` v0.6.0, issue #57
  - Moyens : JitPack, `~/dev/terrain/terrain-style/`, test de la 005 de Marculus ; une PR commune avec 002 · PR #58 fusionnée (6aaf042)
- [002](missions/002-theme-terrain-style.md) · depuis le cycle 1
  - Homme : dev (sonnet), appuyer
  - Mission : thème = jetons de `terrain-style` (clair et sombre), issue #57
  - Moyens : `Theme.kt`, test de 001, `Theme.kt` de Marculus ; essai au soleil de Pascal le 2026-10-30 · PR #58 fusionnée (6aaf042), aller-retour 1/3


## Abandonné

_Avec la raison, en une ligne._

## Livraisons

_Une ligne par cycle, ajoutée sous la précédente à la fin de chaque `/boucle`, et une
par `/bilan` : la livraison que Pascal a retenue, pour qui, et ce qu'elle apporte de plus
que la précédente du même rythme. Un cycle sans livraison neuve livre sa mauvaise
nouvelle, car
« une mauvaise nouvelle est une livraison sur laquelle on apprend » (Pascal) : on
l'inscrit avec ce qu'elle nous apprend._

```
- cycle N · livraison · pour qui · ce qu'elle apporte de plus que la précédente
- cycle N · mauvaise nouvelle : … · pour qui · ce qu'elle nous apprend
- bilan du AAAA-MM-JJ · livraison · pour qui · ce qu'elle apporte de plus que la précédente
- bilan du AAAA-MM-JJ · mauvaise nouvelle : … · pour qui · ce qu'elle nous apprend
```
- cycle 1 · APK de la PR #58 (thème = jetons `terrain-style` v0.6.0) · pour Pascal, opérateur de terrain, essai au soleil le 2026-10-30 avec Marculus · texte des boutons à 7:1 au lieu de 5,17:1 et même vert que Marculus et Surculus (première livraison inscrite)
- cycle 2 · Groma 0.11.0 (bandeau vert retrouvé, choix du thème Système / Clair / Sombre dans Paramètres, appliqué à toute l'appli) · pour Pascal, opérateur de terrain, sur le S23 Ultra · le vert revient (la livraison du cycle 1 l'avait perdu) et le thème se choisit dans l'appli au lieu de suivre le téléphone
- cycle 3 · Groma 0.12.0 (réglage Plein écran dans Paramètres, accueil, carte et capture) · pour Pascal, opérateur de terrain, sur le S23 Ultra · plus de place à l'écran : barres de statut et de navigation masquées, rappelées au glissement
- cycle 4 · Groma `main` d93791b (vue Kanban des projets, plein écran sans la bande de la caméra), APK de publication installé sur le S23 Ultra · pour Pascal, opérateur de terrain · l'avancement de chaque forêt d'un coup d'œil, et toute la largeur de l'écran en paysage
- cycle 5 · Groma 0.14.0 publiée (thème lu dans `terrain-style` v0.7.1) · pour Pascal, mainteneur de Groma, Marculus et Surculus · un changement de style fait dans terrain arrive tout seul dans Groma, sans recopie ; rien ne change à l'écran pour l'opérateur
- cycle 6 · la carte de Groma utilise `terrain-carte` v0.7.1 (#80, 65ce940), essayée sur le S23 Ultra · pour Pascal, mainteneur de Groma, Marculus et Surculus · les tuiles et fonds IGN viennent de la bibliothèque commune au lieu d'une copie (ligne ajoutée au cycle 7, oubliée à la clôture du cycle 6)
- cycle 7 · Groma 0.15.0 avec la planche A4 des cibles C0-C3 (`docs/cibles_groma_a4.pdf`, #83) · pour Pascal, opérateur de terrain · pour la première fois des cibles prêtes à imprimer et plastifier, conformes au détecteur (motif 15 cm)
