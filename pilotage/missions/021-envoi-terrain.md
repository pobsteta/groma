# Mission 021 — envoi des saisies de terrain au serveur, nemetonshiny sans QField

## Mission

**En une phrase (quoi, et pourquoi) :** extraire de la copie de Groma l'archive « terrain » d'une
placette, l'envoyer au serveur (reprenable, renvoyée à chaque modification, Wi-Fi ou 4G selon le
réglage), la servir à nemetonshiny, et décrire dans un brief comment `nemeton` l'intègre à la place
de la copie de QField, parce que Pascal veut se passer de QField (décision du 2026-11-11).

**Redite par l'agent :** Extraire de la copie de Groma l'archive `<plot_id>.terrain.zip` d'une placette, l'envoyer au serveur (renvoyée à chaque modification, regroupée 30 s, sans doublon si l'empreinte n'a pas changé), la servir à nemetonshiny via `GET …/terrain.zip` et décrire dans un brief l'intégration par `nemeton`, pour que Pascal se passe de QField.

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? (sources, conventions nommées ci-dessous) | oui | `EnvoiWorker.kt` (WorkManager, reprise, SHA-256) ; `/televersements` du serveur ; client nemeton `groma_telecharger_resultats()` |
| A-t-il les moyens ? (outils, données d'essai, tests) | oui | tests de 020 |

**Temps de la main :** appuyer.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev`
- **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** notes et photos dans la fiche (022) ; retirer QField ;
  modifier `nemeton` ou `nemetonshiny`.

## Harnais

- **Peut :**
  1. `groma-core` : extraction et empreinte (API de 020) ;
  2. **app** : envoi par WorkManager sur le modèle d'`EnvoiWorker` (même réglage Wi-Fi / 4G, même
     serveur et jeton) ; déclenché après chaque enregistrement (fiche d'arbre, cible, centre) avec un
     délai de regroupement (une modification toutes les quelques secondes ne doit pas créer un envoi
     chacune : WorkManager `REPLACE` + délai) ; rien si l'empreinte n'a pas changé depuis le dernier
     envoi réussi ; état visible dans la liste des placettes (« Terrain envoyé à HH:MM » ou « à
     envoyer ») ;
  3. **serveur** : dépôt de l'archive (API de 020), sans tâche GPU, renvoi qui remplace, `GET …/terrain.zip`,
     date dans `/placettes` ; `docs/serveur.md` ;
  4. **brief nemeton** (`briefs/vers-nemeton/2026-11-11-terrain-sans-qfield.md`, copie déposée dans
     `/home/pascal/dev/briefs/vers-nemeton/` **après la fusion**) : `groma_telecharger_resultats()`
     télécharge aussi `terrain.zip` ; `importer_inventaire_video()` prend les GeoPackages terrain du
     serveur (une placette chacun, même schéma) à la place de la copie de QField, photos comprises ;
     la copie de QField reste acceptée (projets en cours) ; si les deux existent pour une placette,
     celle du serveur gagne, avec une alerte ;
  5. **docs** : `docs/terrain.md` (QField facultatif, envoi de terrain), `docs/architecture.md`
     (schéma du flux), `PLAN.md`.
- **Ne doit pas :** modifier les tests de 020 sans le dire ; toucher à `main` ; créer une tâche GPU
  pour une archive de terrain ; casser le téléversement vidéo.
- **Sources autorisées :** `android/`, `server/`, `core/`, `docs/`, `briefs/vers-nemeton/`.

## Valeur provisoire (chiffre, réglage)

- **Délai de regroupement des envois :** 30 s après la dernière modification ; à juger sur le terrain.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 020, PR #91.
- **Ordre de fusion :** une PR avec 020 et 022, après la #91.
- **PR en brouillon jusqu'à :** la fin de 022 et l'essai de Pascal.
- **CI rouge attendue :** aucune à la fin de la mission.

## Tests

- **Comment saura-t-on que c'est juste :** tests de 020 verts ; `pytest core server benchmark`,
  `gradle :groma-core:test :app:assembleDebug` verts.

## Terrain

- **Échantillon que Pascal vérifiera :** une placette saisie dans Groma seul (sans ouvrir QField),
  « Terrain envoyé », archive téléchargeable depuis le serveur avec ses arbres et leurs saisies.

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (une PR avec 020 et 022), puis Pascal. **Arrêt :** 3 allers-retours.
- **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Une placette faite entièrement dans Groma arrive au serveur avec toutes ses saisies, et nemetonshiny
pourra l'intégrer sans la copie de QField (une fois le brief appliqué).
