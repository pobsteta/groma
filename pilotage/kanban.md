# Kanban de Groma

> Cinq colonnes (fiche n° 8). Chaque carte porte l'Homme (qui), la Mission (quoi, quand,
> combien) et les Moyens (comment, où, avec qui), et renvoie à sa fiche dans
> `pilotage/missions/`. Elle note aussi depuis quel cycle de `/boucle` elle est dans sa
> colonne ; on change ce numéro chaque fois qu'elle change de colonne.
> **Une carte qui ne bouge pas est souvent la mauvaise nouvelle à dire tout de suite.**

> **PLAN.md reste la source maître de l'avancement et des décisions prises avant la méthode** (choix de Pascal, 2026-10-23). Ce kanban ne suit que les missions confiées par `/imperatrice:boucle`.

**Dernier cycle :** 3 _(augmenté de 1 à la fin de chaque `/boucle`, même quand elle n'a
rien lancé. Une carte est immobile quand dernier cycle − N ≥ 2.)_

_Modèle de carte :_

```
- [NNN-titre](missions/NNN-titre.md) · depuis le cycle N
  - Homme : qui
  - Mission : quoi, quand, combien
  - Moyens : comment, où, avec qui
```

## Bac à sable

- Couleurs écrites en dur hors du thème (19 `Color(0x…)` dans `EcranCarte.kt` et `Gnss.kt`) : essai au soleil positif le 2026-10-24, débloquée ; reste à décider le sens (badges GNSS, SNR) · depuis le cycle 0
- Bibliothèques `terrain` (`terrain-gnss`, `terrain-gpkg`, `terrain-carte`) à la place du code de `groma-core` (brief Marculus du 2026-10-17) · depuis le cycle 0

## Validé

- [008](missions/008-vue-kanban-projets.md) · depuis le cycle 3
  - Homme : dev (sonnet), appuyer
  - Mission : vue Kanban des projets, colonnes de Marculus et Surculus, minimum imposé par l'avancement
  - Moyens : `EtatKanban.kt` et `VueKanban.kt` de Surculus, `ResumeProjet` de Groma
- [009](missions/009-plein-ecran-sans-bande.md) · depuis le cycle 3
  - Homme : dev (sonnet), appuyer
  - Mission : en plein écran, plus de bande vide côté caméra, sauf pour les boutons du bandeau
  - Moyens : `BandeauCompact.kt`, `Scaffold` des écrans, capture adb du 2026-10-29

## Planifié

- [003](missions/003-garde-fou-colorscheme.md) · depuis le cycle 2
  - Homme : testeur (sonnet), appuyer
  - Mission : le test du thème échoue aussi si `ColorScheme` a un rôle que `Schema` n'a pas
  - Moyens : `ThemeTerrainStyleTest.kt` sur `main`, AAR `material3-android-1.4.0` · rendue (2d42339, branche `test/003-garde-fou-colorscheme`), sans PR : attend la réadoption de `terrain-style` (cap du 2026-10-24)



## Terminé

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
