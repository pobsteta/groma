# Mission 024 — tests du centre mesuré au GNSS du téléphone

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, avant le code, les tests qui fixent la mesure du centre
de la placette avec le GNSS du téléphone (sans récepteur RTK, ou sans fix RTK), sa qualité notée à part
et ses garde-fous, parce que Pascal veut pouvoir prendre le centre sans le récepteur (demande du
2026-11-11), alors que C0 posée sur ce centre place toute la placette : quelques mètres d'erreur
décalent tous les arbres d'autant.

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | `groma.core.MesureCentre` et ses tests, `QualiteFix.depuisPrecision`, `OrigineFix.TELEPHONE`, `GeoPackage.ajouterCentreMesure` |
| A-t-il les moyens ? | oui | tests existants de `MesureCentre` |

**Temps de la main :** appuyer — tests seuls.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `testeur` · **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** le code (025) ; modifier `nemeton`.

## Harnais

- **Peut :** écrire des tests (rouges jusqu'à 025), API fixée en tête :
  1. **acceptation** : un fix d'origine `TELEPHONE` est accepté seulement quand l'opérateur a confirmé
     (même logique que le flottant : `Autorisation.CONFIRMER_TELEPHONE`, ou nom fixé en tête) ; sans
     confirmation, comportement actuel (refus) ; récepteur présent mais sans fix RTK : la confirmation
     « téléphone » ne s'applique pas aux fixes du récepteur (on ne mélange pas les origines) ;
  2. **moyenne** : centre = moyenne des fixes du téléphone sur la durée de mesure (valeur provisoire
     **30 s**, au moins **20 fixes**), précision = max(dispersion RMS, précision annoncée RMS) comme
     aujourd'hui ; fixes dont la précision annoncée dépasse **10 m** écartés ;
  3. **qualité** : `qualite_gnss = telephone` (nouvelle valeur de domaine, ajoutée par 025) ;
     `precision_h_m` et `ecart_prevu_m` renseignés ;
  4. **priorité** : un centre RTK (`fix` ou `float`) mesuré après un centre `telephone` le remplace
     (règle actuelle : le plus récent compte) ; un centre `telephone` mesuré après un centre RTK : il
     faut une confirmation explicite (« remplacer un centre RTK par la position du téléphone ? »),
     testée comme une fonction pure (`MesureCentre.remplacementAConfirmer(ancienne, nouvelle)` ou nom
     fixé en tête) ;
  5. **schéma** (Python) : valeur `telephone` dans le domaine `qualite_gnss`, version mineure suivante.
- **Ne doit pas :** lire le code de 025 ; toucher à `main` ; modifier les tests existants.
- **Sources autorisées :** `android/groma-core/`, `core/`, `android/app/.../ui/EcranCarte.kt` (lecture).

## Valeur provisoire (chiffre, réglage)

- **Durée de mesure :** 30 s, au moins 20 fixes ; **précision annoncée maximale :** 10 m.
- **Mesurée par :** écart entre centre téléphone et centre RTK sur les mêmes placettes.
- **Gel :** après trois placettes test.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `feat/024-centre-telephone-ajout-complet`.
- **Ordre de fusion :** une PR avec 025. **CI rouge attendue :** oui, jusqu'à la fin de 025.

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (avec 025), puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Les tests fixent sans ambiguïté quand la position du téléphone est acceptée, comment elle est notée,
et qu'elle n'écrase jamais un centre RTK sans confirmation.
