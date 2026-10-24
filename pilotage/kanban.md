# Kanban de Groma

> Cinq colonnes (fiche n° 8). Chaque carte porte l'Homme (qui), la Mission (quoi, quand,
> combien) et les Moyens (comment, où, avec qui), et renvoie à sa fiche dans
> `pilotage/missions/`. Elle note aussi depuis quel cycle de `/boucle` elle est dans sa
> colonne ; on change ce numéro chaque fois qu'elle change de colonne.
> **Une carte qui ne bouge pas est souvent la mauvaise nouvelle à dire tout de suite.**

> **PLAN.md reste la source maître de l'avancement et des décisions prises avant la méthode** (choix de Pascal, 2026-10-23). Ce kanban ne suit que les missions confiées par `/imperatrice:boucle`.

**Dernier cycle :** 1 _(augmenté de 1 à la fin de chaque `/boucle`, même quand elle n'a
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
- Garde-fou inverse du test du thème : compter aussi les rôles de `ColorScheme` (une mise à jour de Compose qui ajoute un rôle le laisserait au défaut clair en sombre) ; même angle mort chez Marculus · depuis le cycle 1
- Bibliothèques `terrain` (`terrain-gnss`, `terrain-gpkg`, `terrain-carte`) à la place du code de `groma-core` (brief Marculus du 2026-10-17) · depuis le cycle 0

## Validé

## Planifié


## Terminé

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
