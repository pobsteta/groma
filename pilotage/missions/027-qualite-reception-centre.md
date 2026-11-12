# Mission 027 — qualité de la réception enregistrée avec le centre mesuré

## Mission

**En une phrase (quoi, et pourquoi) :** enregistrer dans `centres_mesures`, avec chaque centre mesuré
dans Groma (RTK ou téléphone), la qualité de la réception pendant la mesure (positions retenues,
satellites, HDOP, âge des corrections, durée), parce que Pascal veut pouvoir juger après coup, dans
le fichier, de la fiabilité d'un centre (demande du 2026-11-12).

**Redite par l'agent :** Enregistrer dans `centres_mesures`, avec chaque centre mesuré (RTK ou téléphone), cinq valeurs de qualité de réception (positions retenues, satellites, HDOP, âge des corrections, durée), pour que Pascal juge après coup, dans le fichier, de la fiabilité d'un centre.

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | `MesureCentre.resultat()` / `CentreMesure`, `GeoPackage.ajouterCentreMesure` (`GeoPackage.kt:~109`), ajout de colonne à la volée comme `dbh_source`, `SourcePosition.fixDepuis` |
| A-t-il les moyens ? | oui | tests de 024/025, S23 Ultra de Pascal |

**Temps de la main :** appuyer — essai de Pascal.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** changer le géoréférencement du serveur ; modifier `nemeton` ;
  changer les seuils de 024 (30 s, 20 positions, 10 m).

## Harnais

- **Peut :**
  1. **schéma** (source unique, `inventaire_video.schema.json`) : ajouter à `centres_mesures`
     `nb_positions` (INTEGER, positions retenues dans la moyenne), `nb_satellites` (INTEGER, médiane
     des satellites utilisés, vide si inconnu), `hdop` (REAL, moyenne, vide si inconnu),
     `age_corrections_s` (REAL, maximum, vide hors RTK), `duree_mesure_s` (REAL, du premier au
     dernier fix retenu). Le schéma 1.6.0 n'est pas encore publié : rester en 1.6.0, remarque à jour ;
  2. **groma-core** : `CentreMesure` porte ces valeurs (calculées dans `MesureCentre.resultat()`
     à partir des `FixGnss` retenus ; un satellite à 0 compte comme inconnu) ; `ajouterCentreMesure`
     les écrit et ajoute les colonnes absentes des GeoPackages préparés avant (ALTER TABLE ADD COLUMN),
     sans rien perdre des lignes existantes ; tests unitaires ;
  3. **téléphone** : nombre de satellites utilisés depuis `GnssStatus` (`usedInFix`) si c'est simple
     et sûr, sinon laisser vide (pas l'extra `satellites`, peu fiable) ; dire ce qui a été fait ;
  4. **affichage** : la vue « Placette » et la carte montrent après la précision « N positions,
     S satellites, HDOP h » quand c'est connu ;
  5. **Python** : `core` lit et écrit ces colonnes (`gpkg.py`), test de schéma ; serveur inchangé
     sauf s'il recopie `centres_mesures` (alors ne rien perdre, règle 5) ;
  6. **brief** : compléter `briefs/vers-nemeton/2026-11-11-centre-telephone.md` (colonnes, sens,
     vides possibles) ; dépôt de la copie après fusion, par l'orchestrateur ;
  7. `docs/terrain.md` si l'écran change ; ligne de fin dans `pilotage/journal.md` (ajout seul, `>>`).
- **Ne peut pas :** installer un APK `fr.nemeton.groma` sur le téléphone, désinstaller, `pm clear` ;
  lire `~/.config/groma/` ; modifier `nemeton`/`nemetonshiny` ; changer un numéro de version.
- **Fini quand :** `gradle :groma-core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug`,
  `pytest core server benchmark` passent ; un centre écrit dans un GeoPackage ancien garde ses lignes
  et reçoit les colonnes ; commit Conventional Commits en français poussé sur
  `feat/024-centre-telephone-ajout-complet`.
