# Mission 026 — essai de fumée de l'APK sur émulateur et sur téléphone, comme Surculus

## Mission

**En une phrase (quoi, et pourquoi) :** ajouter à Groma un essai de fumée instrumenté, calqué sur
celui de Surculus (ADR 0011 de Surculus) : un type de build `fumee` copié de la release
(`applicationIdSuffix = ".fumee"`), des tests `androidTest` qui écrivent puis relisent un projet
avec le SQLite **d'Android**, joués sur l'émulateur et sur le téléphone branché sans toucher à
l'application installée, et une étape d'émulateur dans `release.yml`, parce que les tests de Groma
tournent tous sur la JVM (sqlite-jdbc) alors que le SQLite d'Android diffère (version, pas de
`ST_*`), et que Pascal veut que l'application soit essayée comme Surculus sait le faire (demande du
2026-11-11).

**Redite par l'agent :** Ajouter un type de build `fumee` (copie de la release, identifiant `fr.nemeton.groma.fumee`, donc à côté de
l'application de Pascal et jamais à sa place) et des tests instrumentés qui jouent le code d'écriture de `groma-core`
sur le SQLite d'Android (et lancent `MainActivity`), sur émulateur puis téléphone, avec une vérification qui échoue si
aucun test ne tourne et une étape d'émulateur avant la publication de l'APK, pour qu'une release soit essayée sur un
vrai SQLite d'Android et que Pascal puisse rejouer l'essai sans risque pour ses données de terrain.

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | Surculus (lecture) : `app/build.gradle.kts` (type `fumee`, tâches `effacerRapportFumee`/`verifierFumee`), `scripts/verifier-fumee.sh`, `app/src/androidTest/…/Fumee*.kt`, `.github/workflows/release.yml` (étape émulateur) |
| A-t-il les moyens ? | oui | émulateur `emulator-5554` (API x86_64) déjà lancé sur le PC ; S23 Ultra de Pascal quand il est branché (`R5CW51BZA2W`) ; `exemples/inventaire_video/` |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** tests d'interface complets ; modifier `~/dev/surculus`.

## Harnais

- **Peut :**
  1. **type de build `fumee`** dans `android/app/build.gradle.kts` : `initWith(release)`,
     `matchingFallbacks += "release"`, `applicationIdSuffix = ".fumee"`, `versionNameSuffix = "-fumee"`,
     même minification que la release (s'il y en a une), `testBuildType = "fumee"` ; signature : celle de
     la release si le keystore est fourni, sinon la clé de debug (le suffixe évite tout conflit) ;
     dépendances `androidTestImplementation` (androidx.test ext junit, runner ; Compose ui-test si utile),
     signalées dans `docs/dependances.md` (licences) ;
  2. **tests `androidTest`** (`android/app/src/androidTest/kotlin/groma/app/Fumee*.kt`), sur le SQLite
     d'Android (`SqliteAndroid`) et une copie de `exemples/inventaire_video/inventaire_video.gpkg`
     (assets de test) : ouverture, déclencheurs (`detections` → `arbres`, ruban conservé), `ecrireCible`,
     `ajouterCentreMesure`, `modifierArbre`, `ajouterArbre`/`supprimerArbreAjoute`, migration d'un projet
     ancien (`dbh_source` absent), `ExportTerrain.archiver` (instantané sous `BEGIN IMMEDIATE`, archive
     relisible), relecture finale ; et un test de lancement de `MainActivity` (l'écran des projets
     s'affiche sans plantage) ;
  3. **vérification** : `scripts/verifier-fumee.sh` (au moins un test joué, aucun échec) et tâches Gradle
     `effacerRapportFumee` / `verifierFumee` comme Surculus ;
  4. **CI** : étape d'émulateur x86_64 (API 34, `reactivecircus/android-emulator-runner` épinglé par SHA,
     KVM) dans `release.yml` avant la publication de l'APK ; si c'est trop lourd pour chaque PR, seulement
     à la release (comme Surculus) ;
  5. **docs** : `docs/terrain.md` ou `README` (comment lancer l'essai : `cd android && ANDROID_SERIAL=…
     gradle :app:connectedFumeeAndroidTest`), `PLAN.md`.
- **Ne doit pas :** installer sur le téléphone un APK portant l'identifiant de l'application
  (`fr.nemeton.groma`) ; désinstaller l'application de Pascal ; toucher à `main`.
- **Sources autorisées :** `android/`, `scripts/`, `.github/workflows/`, `docs/`, `~/dev/surculus` (lecture).

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** aucune ; part de `main` (branche `feat/026-essai-fumee`), indépendante de 024-025.
- **CI rouge attendue :** aucune.

## Tests

- **Comment saura-t-on que c'est juste :** `connectedFumeeAndroidTest` vert sur `emulator-5554` puis sur
  le S23 Ultra (`fr.nemeton.groma.fumee` installé à côté, `fr.nemeton.groma` intact : même
  `lastUpdateTime` avant et après) ; `verifier-fumee.sh` échoue si aucun test ne tourne.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Chaque release est essayée sur un vrai SQLite d'Android avant de partir, et l'on peut rejouer
l'essai sur le téléphone sans risque pour ses données.
