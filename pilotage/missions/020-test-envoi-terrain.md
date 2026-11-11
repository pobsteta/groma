# Mission 020 — tests de l'envoi des saisies de terrain au serveur (se passer de QField)

## Mission

**En une phrase (quoi, et pourquoi) :** écrire, avant le code, les tests qui fixent le GeoPackage
« terrain » d'une placette (centre mesuré, cibles, arbres et leurs saisies, notes, photos), son envoi
au serveur Groma, renvoyable à chaque modification, et sa mise à disposition pour nemetonshiny, parce
qu'aujourd'hui les saisies ne retournent à nemetonshiny que par la copie de QField et que Pascal veut
s'en passer (décision du 2026-11-11 : QField devient facultatif).

**Redite par l'agent :** _à compléter par l'agent avant d'agir — s'il n'y arrive pas, il s'arrête._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | téléversement reprenable existant (`/televersements`, `EnvoiWorker.kt`) ; résultats servis par `/projets/{projet_id}/placettes/{plot_id}/{fichier}` ; schéma unique |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | `exemples/inventaire_video/`, tests `server/tests/test_api.py`, `GeoPackage` de `groma-core` |

**Temps de la main :** appuyer — tests seuls.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `testeur`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** le code (021, 022) ; modifier `nemeton` ou `nemetonshiny`.

## Décisions de Pascal (2026-11-11)

1. **Envoi de terrain séparé** du paquet vidéo : un GeoPackage « terrain » par placette, renvoyable à
   chaque modification (les arbres se saisissent après le film, le paquet est souvent déjà parti).
2. **QField facultatif** : « Ouvrir dans QField » et le plugin restent, nemetonshiny n'en dépend plus.
3. **Notes et photos d'arbre dans Groma** (mission 022), transportées par cet envoi.

## Harnais

- **Peut :** écrire des tests (rouges jusqu'à 021) :
  1. **extraction** (Kotlin, `groma-core`, API fixée en tête du test, ex. `groma.core.ExportTerrain`) :
     depuis la copie de Groma, un GeoPackage au **même schéma** (`inventaire_video.schema.json`, même
     version) ne contenant que la placette demandée : sa ligne `placettes`, ses `centres_mesures`, ses
     `cibles`, ses `arbres` (toutes colonnes, saisies comprises), ses `detections` ; table `projet` ;
     aucune autre placette ; valeurs identiques à la source (règle 5) ; fichier valide pour GDAL
     (`gpkg_contents`, `gpkg_geometry_columns`, SRS 2154) — vérifiable en Python avec `fiona`/`pyogrio`
     ou `sqlite3` en lecture seule ;
  2. **photos** : les chemins de `arbres.photos` (relatifs au projet, convention QField) sont joints
     dans une archive `<plot_id>.terrain.zip` (`terrain.gpkg` + `photos/…`), chemins réécrits relatifs
     à l'archive ; une photo manquante est signalée, pas bloquante ;
  3. **serveur** (Python, `server/tests/`) : dépôt reprenable de l'archive (réutilise `/televersements`
     avec un `type` « terrain », ou route dédiée : à fixer en tête du test), **pas de tâche GPU créée** ;
     un renvoi remplace le précédent (dernier reçu gagne, l'ancien n'est pas servi) ; servi par
     `GET /projets/{projet_id}/placettes/{plot_id}/terrain.zip` ; 404 avant tout envoi ; jeton exigé ;
     `/placettes` indique la date du dernier envoi de terrain ;
  4. **idempotence côté téléphone** (Kotlin) : une empreinte (SHA-256 du contenu extrait, hors dates de
     fichier) ne change pas sans modification ; elle change après `modifierArbre`, `ecrireCible`,
     `ajouterCentreMesure`.
- **Ne doit pas :** lire le code de 021-022 ; toucher à `main` ; modifier le schéma ; modifier les
  tests existants du téléversement vidéo.
- **Sources autorisées :** `server/`, `core/`, `android/groma-core/`, `android/app/.../EnvoiWorker.kt`
  (lecture), `docs/serveur.md`, `docs/terrain.md`, `qfield/` (convention des photos).

## Valeur provisoire (chiffre, réglage)

- **Taille maximale de l'archive :** celle du paquet (`cfg.taille_max`), à revoir avec les photos.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** PR #91 (vue « Placette », `modifierArbre`) ; part de `feat/020-terrain-sans-qfield`.
- **Ordre de fusion :** une PR avec 021 et 022, après la #91.
- **PR en brouillon jusqu'à :** la fin de 022.
- **CI rouge attendue :** oui, jusqu'à la fin de 021.

## Tests

- **Comment saura-t-on que c'est juste :** chaque décision a une assertion ; valeurs écrites à la main.

## Terrain

- **Échantillon que Pascal vérifiera :** aucun pour cette mission (voir 021).

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (une PR avec 021 et 022), puis Pascal. **Arrêt :** 3 allers-retours.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Les tests décrivent sans ambiguïté le contenu de l'archive de terrain, son dépôt et son renvoi ; 021
les fait passer sans les modifier.
