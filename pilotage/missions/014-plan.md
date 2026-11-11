# Mission 014 — plan : les couleurs d'état passent au thème

Rendu de l'`afficheur`, mission « tenir » : aucun code. Pascal choisit le sens (§ 4) avant la mission de code.

**Redite :** proposer, avant tout code, comment les 18 couleurs en dur de `Gnss.kt` et `EcranCarte.kt`
rejoignent les rôles du thème terrain-style, parce qu'elles ne suivent ni le mode sombre ni le style commun.

## 1. La question, et pour qui

« Ce fix est-il assez bon pour inventorier ici ? » — pour le forestier debout sur la placette, au soleil,
un gant, un coup d'œil sur le badge en haut de la carte. Une seule chose en grand : **la couleur du badge**.
La comparaison : **la même couleur dans Groma, dans Marculus, en clair et en sombre**.

## 2. Ce que le thème offre (et n'offre pas)

Source : `StyleTerrain.kt` (v0.7.1), `Schema.kt`, ADR 0002. Fonds : clair `#FEF7FF`, sombre `#141218`.

* Existent : `reussi` et `alerte` (4 rôles chacun : couleur, surCouleur, conteneur, surConteneur),
  `error`/`onError`, `outline`, `onSurfaceVariant`, `inverseSurface`, `surfaceVariant`.
* **N'existent pas : un bleu (DGPS), un orange, un olive.** Trois couleurs d'état pour cinq niveaux de fix
  et cinq niveaux SNR.
* `alerte.couleur` (`#E2AA00`) est **interdit en texte** (ADR 0002) ; il sert en fond, texte noir (9,98:1).
* En clair, `reussi.couleur` est `#00341F`, un vert presque noir (13,2:1 sur le fond) : pour un aplat qui doit
  se lire « vert », prendre `reussi.conteneur` (`#005436` clair, `#71D6A2` sombre).
* `Theme.kt` de Groma **ne transmet pas** `reussi`/`alerte` (`versColorScheme` ne copie que les 48 rôles de
  `ColorScheme`) : toute option « au thème » demande un petit ajout (CompositionLocal) dans Groma. À dire à Pascal :
  c'est du code nouveau, pas un simple remplacement.

## 3. Les 18 couleurs

Contraste = WCAG 2.2, formule de `Contraste.kt` (recalculée à l'identique, script hors dépôt), non arrondi puis arrondi à 0,01.
« Texte » exige 4,5:1, « aplat » 3:1. Les puces SNR (`Gnss.kt:72`, `147`) et le texte « Fini » (`EcranCarte.kt:633`, `665`) sont dans un `AlertDialog` :
leur fond réel est `surfaceContainerHigh` (Material 3, BOM 2026.09), soit **`#ECE6F0` en clair, `#2B2930` en sombre**
(valeurs de `StyleTerrain.kt`), et non `surface`. « Arrivé » (`EcranCarte.kt:533`) est sur une `Surface(tonalElevation = 3.dp)`,
légèrement teintée (mesuré sur `surfaceContainer`, `#F3EDF7` / `#211F26`, approximation). Le badge est sur la **carte** (fond d'image inconnu : le contraste
pastille/carte n'est pas mesurable, seul texte/pastille l'est).

### Badge de qualité de fix (`Gnss.kt`, texte blanc 12 sp sur pastille)

| # | Fichier:ligne | Usage | Actuel | Blanc sur actuel | Rôle proposé (option A) | Clair | Sombre |
|---|---|---|---|---|---|---|---|
| 1 | Gnss.kt:44 | RTK fixe | `#2E7D32` | 5,13 | `reussi.conteneur` + `surConteneur` | 9,05 | 11,12 |
| 2 | Gnss.kt:45 | RTK flottant | `#9E9D24` | **2,88 (échec)** | `alerte.couleur` + `surCouleur` | 9,98 | 9,98 |
| 3 | Gnss.kt:46 | DGPS | `#1565C0` | 5,75 | `alerte.couleur` (fusion) ou rôle nouveau | 9,98 | 9,98 |
| 4 | Gnss.kt:47 | Autonome, PPS | `#616161` | 6,19 | `surfaceVariant` + `onSurfaceVariant` | 7,24 | 5,48 |
| 5 | Gnss.kt:48 | Sans fix / autre | `#B71C1C` | 6,57 | `error` + `onError` | 6,54 | 7,66 |
| 6 | Gnss.kt:58 | Connexion RTK en cours | `#616161` | 6,19 | idem 4 | 7,24 | 5,48 |
| 7 | Gnss.kt:59 | Sans position | `#B71C1C` | 6,57 | idem 5 | 6,54 | 7,66 |
| 8 | Gnss.kt:60 | Position du téléphone | `#616161` | 6,19 | idem 4 | 7,24 | 5,48 |

Le blanc fixe du texte (Gnss.kt:64) devient la couleur « sur » du rôle (noir sur `alerte`, blanc ou quasi-noir ailleurs).
Seule la ligne 2 échoue aujourd'hui (texte blanc sur pastille). Le contraste pastille/fond n'est pas jugé : le badge est sur la carte.

### Barres/puces SNR (`Gnss.kt:118-122`, aplat de 6 % du rayon dans le dialogue, fond `surfaceContainerHigh`)

Fond : clair `#ECE6F0`, sombre `#2B2930`. Seuil aplat : 3:1.

| # | Ligne | Seuil | Actuel | Clair | Sombre | Rôle proposé (option A, 4 niveaux) | Clair | Sombre |
|---|---|---|---|---|---|---|---|---|
| 9 | 118 | absent / ≤ 0 | `#9E9E9E` | **2,19** | 5,36 | `outline` | 3,72 | 4,53 |
| 10 | 119 | < 25 dB | `#D32F2F` | 4,06 | **2,88** | `error` | 5,34 | 8,41 |
| 11 | 120 | 25–34 | `#F57C00` | **2,21** | 5,31 | `alerte.couleur` | **1,72 (échec)** | 6,83 |
| 12 | 121 | 35–44 | `#9E9D24` | **2,35** | 4,99 | fusionné avec 11 (perd un niveau) | idem 11 | idem 11 |
| 13 | 122 | ≥ 45 | `#2E7D32` | 4,19 | **2,80** | `reussi.conteneur` | 7,39 | 8,10 |

Aujourd'hui, sur le vrai fond, **5 puces sur 5 échouent à 3:1 dans l'un des deux modes** : 9, 11 et 12 en clair, 10 et 13 en sombre.
(Une première mesure sur `surface` donnait des chiffres plus favorables ; elle était faite sur le mauvais fond.)
L'option A répare 9, 10, 13 et le sombre de 11-12 ; des trois échecs du clair (9, 11, 12), elle ne répare **qu'une puce, l'absente (via `outline`)** :
le jaune d'alerte reste à 1,72 sur ce fond clair. Remède à tester : un liseré `outline` autour de la puce (3,72 sur le fond clair, 4,53 en sombre).

### Carte (`EcranCarte.kt`)

| # | Fichier:ligne | Usage | Actuel | Contraste actuel | Rôle proposé | Clair | Sombre |
|---|---|---|---|---|---|---|---|
| 14 | EcranCarte:399 | Fond de l'étiquette « tuiles » (alpha CC), texte blanc | `#CC37474F` | 9,65 (opaque) | `inverseSurface` + `inverseOnSurface` | 11,65 | 10,17 |
| 15 | EcranCarte:407 | Texte d'attribution carte | `#212121` | 16,10 sur blanc | **hors thème** (sur la carte) | — | — |
| 16 | EcranCarte:409 | Fond d'attribution (alpha CC) | `#CCFFFFFF` | idem 15 | **hors thème** (sur la carte) | — | — |
| 17 | EcranCarte:533 | « Arrivé » (titre, 22 sp gras = grand texte, seuil 3:1), sur `Surface(tonalElevation 3 dp)` | `#2E7D32` | 4,46 clair, 3,18 sombre (passe de justesse en grand texte) | `reussi.couleur` en texte | 12,09 | 14,28 |
| 18 | EcranCarte:665 | « Fini » (texte courant `bodyMedium`, seuil 4,5:1), dans un dialogue, fond `surfaceContainerHigh` | `#2E7D32` | **4,19 clair (échec)**, **2,80 sombre (échec)** | `reussi.couleur` en texte | 11,35 | 12,58 |

Vérification : `grep -n 'Color(0x' Gnss.kt EcranCarte.kt` donne bien 13 + 5 = 18 lignes. Marculus (`BadgeFix.kt:65-90`)
a les mêmes valeurs en dur pour les 5 fix ; seule la qualité « RTK flottant » y est nommée autrement (`RTK_FLOAT`).

## 4. Trois options de sens

**A. Tout au thème, en fusionnant des niveaux.** Fix : vert (RTK fixe) / jaune (flottant **et** DGPS) / gris (autonome,
téléphone, connexion) / rouge (sans position). SNR : 4 niveaux. Gain : suit le mode sombre, même vocabulaire que les autres
applis du forestier (but de l'ADR 0002). Coût : on perd le bleu DGPS et un niveau SNR ; en clair, le jaune d'alerte ne passe
pas 3:1 sur la surface (liseré nécessaire) ; code nouveau dans `Theme.kt`. Les 4 vers `reussi.conteneur` évitent le vert quasi noir.

**B. Rôles nouveaux demandés à terrain, par brief.** On propose à terrain un rôle d'état « information » (bleu, DGPS) et un
« avertissement fort » (orange, SNR), avec leurs contrastes générés et testés comme `reussi`. Gain : 5 niveaux conservés,
même couleur dans toutes les applis. Coût : délai (brief, ADR, publication, épinglage), Marculus doit suivre ; on invente ici
des rôles qui **n'existent pas** — rien n'est écrit dans terrain par cette mission.

**C. Couleurs de signal fixes, nommées dans `Theme.kt`.** On garde les 5 niveaux et le vocabulaire actuel, mais dans un objet
nommé (`SignalGnss`) avec une valeur claire et une valeur sombre, corrigées pour atteindre 4,5:1 en texte et 3:1 en aplat
(par exemple un olive plus sombre pour RTK flottant, 2,88:1 aujourd'hui). Gain : aucun sens perdu, rien à demander à terrain,
petit changement. Coût : Groma et Marculus gardent une palette d'état à part de celle de terrain.

## 5. Recommandation (partition ligne par ligne)

Chaque ligne a une seule décision. Mélange : **A** (rôle du thème) partout sauf deux pastilles du badge (2 et 3), en **C**
(couleur de signal nommée), pour ne pas fusionner RTK flottant et DGPS (deux précisions différentes, décimétrique et métrique,
donc une perte d'information, pas seulement de couleur). Les rôles `reussi`/`alerte` exigent le petit ajout (CompositionLocal) dans `Theme.kt`.

| # | Décision | Valeur / rôle | Condition |
|---|---|---|---|
| 1 | A | `reussi.conteneur` + `surConteneur` | essai au soleil |
| 2 | **C** | `SignalGnss.rtkFlottant`, olive foncé, candidat `#6D6C0B` (blanc dessus : 5,53) | essai au soleil ; valeur à mesurer au code |
| 3 | **C** | `SignalGnss.dgps`, bleu actuel `#1565C0` conservé (blanc : 5,75), nommé | aucune |
| 4 | A | `surfaceVariant` + `onSurfaceVariant` | essai au soleil |
| 5 | A | `error` + `onError` | essai au soleil |
| 6 | A | idem 4 | essai au soleil |
| 7 | A | idem 5 | essai au soleil |
| 8 | A | idem 4 (position du téléphone : gris) | essai au soleil |
| 9 | A | `outline` | aucune |
| 10 | A | `error` | aucune |
| 11 | A | `alerte.couleur` + liseré `outline` (11 et 12 fusionnées) | liseré à tester ; sinon 11-12 en C |
| 12 | A | idem 11 (fusion assumée : perd un niveau de SNR) | idem 11 |
| 13 | A | `reussi.conteneur` | aucune |
| 14 | A | `inverseSurface` + `inverseOnSurface` | aucune |
| 15 | hors thème | constante nommée dans `Theme.kt` (posée sur l'image de la carte) | aucune |
| 16 | hors thème | idem 15 | aucune |
| 17 | A | `reussi.couleur` en texte | aucune |
| 18 | A | `reussi.couleur` en texte | aucune |

Les lignes 1 à 8 (le badge, la « chose à montrer ») ne sont changées qu'après l'essai au soleil de Pascal : si deux états se
confondent, la ligne concernée passe en C. Si Pascal refuse toute fusion, 11-12 passent en C aussi. B (rôles nouveaux demandés à
terrain) reste pour plus tard.

## 6. Test des dix secondes (à faire par Pascal, à la mission de code)

Pascal, sur le S23 Ultra, au soleil, une capture claire et une sombre du badge à chacun des 4 ou 5 états. Après dix secondes,
il doit avoir compris : **quel état de fix est le bon, lequel est mauvais, et que le mauvais ne ressemble pas au passable.**
Si deux états se confondent, l'option est refusée pour ceux-là.

## 7. Limites

* Fond de « Arrivé » : `Surface(tonalElevation = 3.dp)`, teinte approchée par `surfaceContainer` ; le fond exact dépend du calcul de l'élévation tonale.
* Le candidat `#6D6C0B` (ligne 2) n'est mesuré que texte/pastille ; son rendu au soleil n'est pas jugé.
* Contraste pastille / carte non mesuré (fond d'image variable) ; texte / pastille seul est mesuré.
* Les `alpha CC` (lignes 14, 16) sont calculés opaques ; réel plus faible selon la carte.
* Source des valeurs de thème : `StyleTerrain.kt` relu aujourd'hui ; si terrain évolue, refaire les mesures.
