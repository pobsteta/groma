# Mission 029 — centre C0 toujours visible dans la vue « Placette », mesurable depuis la fiche de la placette

## Mission

**En une phrase (quoi, et pourquoi) :** afficher en permanence dans la vue « Placette » l'état du
centre C0 (non mesuré, ou qualité, précision, date de la mesure) et ajouter « Mesurer le centre » à
la fiche de la placette sur la carte, utilisable quelle que soit la distance au point prévu (même à
plus de 30 m), parce que Pascal ne voit pas qu'un centre RTK est mesuré et ne peut lancer la mesure
qu'une fois « Arrivé » à moins de 5 m (demande du 2026-11-12).

**Redite par l'agent :** _à remplir par l'agent._

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Redire en une phrase ? | oui | |
| Sait-il comment faire ? | oui | `EcranPlacette.kt` (~l. 196-207 : `centreMesure`, `centreQualite`, `centrePrecisionM`, `centreReception`), `EcranCarte.kt` (fiche de placette ~l. 510-545, bouton du bandeau `carte_mesurer_centre` ~l. 575, `EtapeCentre`), `GeoPackage.dernierCentreParPlacette` (`date_mesure`) |
| A-t-il les moyens ? | oui | tests existants, S23 Ultra de Pascal |

**Temps de la main :** appuyer — essai de Pascal.

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet

## Harnais

- **Peut :**
  1. vue « Placette » : une ligne toujours visible sous le titre, par exemple
     « Centre C0 : RTK fixe, précision 0,02 m, mesuré le 12/11 à 10:05 » (qualités `fix`, `float`,
     `telephone` en clair ; date locale du téléphone) ; « Centre C0 non mesuré » sinon (le message
     rouge actuel reste) ; les avertissements téléphone et la ligne de réception restent ;
  2. fiche de la placette (carte) : bouton « Mesurer le centre » qui lance la même mesure que le
     bandeau (`EtapeCentre`, mêmes confirmations : flottant, téléphone, remplacement d'un RTK),
     sans exiger « Arrivé » ni aucune distance maximale ; si la position est à plus de 30 m du point
     prévu, avertissement non bloquant avant la mesure (« Tu es à X m du point prévu : le centre sera
     enregistré là où tu es. Continuer ? ») ; le bouton du bandeau reste ;
  3. disposition : la fiche a déjà 3 boutons (« Y aller », « Placette », « Filmer ») ; tout doit rester
     lisible sur le S23 (passer sur deux lignes si besoin) ;
  4. tests JVM pour toute logique nouvelle (format de la ligne, seuil de 30 m) dans `groma-core`
     ou un test d'app ; `docs/terrain.md` ; `PLAN.md` ; ligne de fin dans `pilotage/journal.md` (`>>`).
- **Ne peut pas :** changer le schéma ; installer un APK `fr.nemeton.groma` ; désinstaller ;
  `pm clear` ; lire `~/.config/groma/` ; toucher `nemeton`/`nemetonshiny` ; changer une version.
- **Fini quand :** `gradle :groma-core:test :app:testDebugUnitTest :app:assembleDebug` verts ;
  commit poussé sur `feat/029-centre-visible-et-mesurable`.
