# Mission 030 — « Relever C0 » partout, et depuis la vue « Placette »

## Mission

**En une phrase (quoi, et pourquoi) :** renommer « Mesurer le centre » en « Relever C0 » dans toute
l'application et ajouter ce bouton dans la vue « Placette », sur la ligne d'état de C0, avec le même
relevé que la carte, parce que Pascal regarde l'état de C0 dans la vue « Placette » et n'y trouve pas
le bouton, et veut un verbe de relevé GNSS (RTK ou téléphone) (demande du 2026-11-12, libellé choisi
par Pascal : « Relever C0 »).

**Redite par l'agent :** _à remplir par l'agent._

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Redire en une phrase ? | oui | |
| Sait-il comment faire ? | oui | `EcranCarte.kt` (`demanderMesure`, `demanderMesureDepuisFiche`, `EtapeCentre`, dialogues, `FeuillePlacette`, bandeau « Arrivé »), `EcranPlacette.kt` (ligne `EtatCentre`), `MainActivity.kt` (navigation, `EtatCibles`), `strings.xml` |
| A-t-il les moyens ? | oui | tests existants, S23 Ultra de Pascal |

**Temps de la main :** appuyer — essai de Pascal.

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet

## Harnais

- **Peut :**
  1. libellés : « Relever C0 » pour tous les boutons (bandeau « Arrivé », fiche de la placette, vue
     « Placette »), dialogues et messages au même vocabulaire (« Relevé de C0 », « C0 sera relevé là
     où tu es », « C0 relevé au RTK fixe, précision 0,02 m, à 1,3 m du point prévu (moyenne de 24
     positions) », « C0 relevé avec le GNSS du téléphone … ») ; garder les avertissements du téléphone ;
  2. vue « Placette » : bouton « Relever C0 » sur la ligne d'état de C0 (en haut) ; il lance le MÊME
     relevé que la carte (avertissement au-delà de 30 m, confirmations flottant / téléphone /
     remplacement d'un RTK, qualité de la réception) : extraire le flux de relevé d'`EcranCarte` dans
     un composant ou un état partagé, sans deuxième implémentation ; à la fin, la ligne d'état, le
     plan et « Ajouter un arbre » se mettent à jour sans quitter la vue ;
  3. l'écoute GNSS (récepteur et téléphone) doit être active dans la vue « Placette » pendant le
     relevé (comme sur la carte) et arrêtée en sortant ;
  4. tests JVM pour la logique nouvelle ou déplacée ; `docs/terrain.md` (vocabulaire « relever ») ;
     `PLAN.md` ; ligne de fin dans `pilotage/journal.md` (`>>`).
- **Ne peut pas :** changer le schéma ; changer la mesure elle-même (seuils, qualité, écriture) ;
  installer un APK `fr.nemeton.groma` ; désinstaller ; `pm clear` ; lire `~/.config/groma/` ;
  toucher `nemeton`/`nemetonshiny` ; changer une version.
- **Fini quand :** `gradle :groma-core:test :app:assembleDebug` verts ; commit poussé sur
  `feat/030-relever-c0`.
