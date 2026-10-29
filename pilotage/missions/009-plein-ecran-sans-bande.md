# Mission 009 — plein écran jusqu'au bord, sans la bande de la caméra

> Fiche écrite par l'orchestrateur le 2026-10-29. Demandée par Pascal le 2026-10-29 : « en mode
> plein écran, ça ne prend pas tout l'écran », puis « Oui, sauf les boutons du bandeau ».

## Mission

**En une phrase (quoi, et pourquoi) :** quand le plein écran est activé, ne plus réserver la zone
du trou de la caméra frontale (encoche) pour la liste, les écrans et le titre, et ne la garder que
pour les boutons du bandeau, pour que Groma occupe vraiment tout l'écran comme Pascal l'attend,
sans qu'un bouton passe sous la caméra.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Vérifié à la source

Constats vérifiés par l'orchestrateur le 2026-10-29.

| Constat | Lu à | Source du constat |
|---|---|---|
| Sur le S23 Ultra en paysage, plein écran activé, une bande vide d'environ 130 px reste à gauche (côté caméra) sur la liste des projets et sous le titre « Groma » | capture d'écran adb du 2026-10-29, Groma 0.12.0 (7dd2a00) | orchestrateur |
| `BandeauCompact` ajoute `displayCutout` en haut et sur les côtés, à tout le bandeau | `android/app/src/main/kotlin/groma/app/ui/BandeauCompact.kt` l. 43-46 | orchestrateur |
| Les écrans à `Scaffold` (Projets, Placettes, Paramètres, Projets du serveur) gardent les marges par défaut de Material 3, qui comprennent l'encoche | `ui/EcranProjets.kt` l. 82, `EcranPlacettes.kt` l. 55, `EcranParametres.kt` l. 60, `EcranProjetsServeur.kt` l. 85 | orchestrateur |
| `appliquerPleinEcran` masque les barres mais « the display cutout inset is unaffected » | `GromaApp.kt` l. 33-47 | orchestrateur |
| La surcouche de capture prend `systemBars ∪ displayCutout` | `CaptureActivity.kt` l. 74-78 | orchestrateur |
| Marculus n'ajoute pas de marge d'encoche (`statusBarsPadding` seul) | `marculus` `ui/BandeauCompact.kt` l. 42 | orchestrateur |

## Réponse de Pascal (2026-10-29)

« Oui, sauf les boutons du bandeau » : en plein écran, la liste, le contenu des écrans et le titre
vont jusqu'au bord ; les boutons (icônes d'action, menu « ⋮ », retour) gardent la marge de
l'encoche pour rester faciles à toucher. Plein écran désactivé : rien ne change.

Règles, avec leur auteur :
- **Sauf les boutons du bandeau** : Pascal, 2026-10-29.
- **Capture** : les boutons de la surcouche de capture sont des boutons ; ils gardent la marge.
  Orchestrateur, à confirmer par Pascal à la relecture.

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | `contentWindowInsets` des `Scaffold`, marges de `BandeauCompact` séparées entre titre et boutons, selon `Reglages.pleinEcran` |
| A-t-il les moyens ? | oui | réglage existant (mission 007) |

**Temps de la main :** appuyer.

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** la vue Kanban (mission 008) ; changer le réglage lui-même ou
  son défaut.

## Harnais

- **Peut :** `ui/BandeauCompact.kt`, les écrans à `Scaffold` de `ui/`, `GromaApp.kt` (commentaire),
  `MainActivity.kt` (faire descendre le réglage), `PLAN.md` ; branche
  `fix/009-plein-ecran-sans-bande` partie de `origin/main`.
- **Ne doit pas :** toucher à `main` ; modifier les tests existants.

## Tests

`.claude/test-command` vert, `:app:assembleDebug` vert.

## Terrain

Pascal, S23 Ultra, plein écran activé : en paysage (caméra à gauche puis à droite) et en portrait,
la liste et le titre vont jusqu'au bord ; les boutons du bandeau ne sont jamais sous la caméra ;
plein écran désactivé : comme la 0.12.0.

## Qui veille, qui tranche

`relecteur`, puis Pascal · 3 allers-retours · Pascal tranche.

## Aller-retour 1/3 (2026-10-29)

Relecture : prêt pour Pascal (PR #71). Décisions de Pascal du 2026-10-29 (« fais ce qu'il y a à
faire », sur les propositions de l'orchestrateur) :
1. **Boutons du contenu** : en plein écran, le menu ⋮ de chaque carte projet
   (`EcranProjets.kt` l. 239) et la puce « Terminés » au bout de la ligne de recherche gardent la
   marge de l'encoche du côté où ils sont (fin de ligne), comme les boutons du bandeau ; le texte des
   cartes va jusqu'au bord. Même règle pour tout autre bouton en bout de ligne des écrans à
   `Scaffold` (Placettes, Paramètres, Projets du serveur), s'il y en a.
2. **Capture** : les boutons de la surcouche gardent la marge (confirmé).
3. **Portrait** : la marge du haut reste pour tout le bandeau (confirmé).
4. Points mineurs du relecteur : réindenter le bloc `CompositionLocalProvider` de `MainActivity.kt`
   (l. 94) ; couper la ligne de KDoc de `GromaApp.kt` l. 37.
5. Commits ajoutés sur la branche `fix/009-plein-ecran-sans-bande` (PR #71), pas de nouvelle PR.

## Aller-retour 2/3 (2026-10-29)

Relecture de la reprise 1/3 (730d0f9, 9ab3145) : à reprendre. Règle tranchée par l'orchestrateur,
d'après la décision de Pascal « aucun bouton sous la caméra » : **tout bouton (ou interrupteur)
au bord gauche ou droit du contenu prend `margeBouton` du côté de ce bord**, qu'il soit en bout de
ligne ou aligné à gauche. Les champs de texte et les puces non cliquables vont jusqu'au bord.
1. `SectionRtk.kt` (affichée dans Paramètres, donc dans le harnais) : interrupteurs de
   `Interrupteur()` (l. 225, RTK actif, NTRIP), « Choisir » (l. 90), « Charger » (l. 112) →
   `margeBouton(End)` ; boutons alignés à gauche (Bluetooth/TCP, Tester/Libérer) →
   `margeBouton(Start)`.
2. « Télécharger » de Projets du serveur garde `margeBouton(Start)` (conforme à la règle).
3. Puce d'état des placettes (`AssistChip` sans action) : retirer la marge, ce n'est pas un bouton.
4. Commits ajoutés sur `fix/009-plein-ecran-sans-bande` (PR #71), pas de nouvelle PR.
