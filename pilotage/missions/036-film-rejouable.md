# Mission 036 — Film rejouable de la capture (enregistrement ARCore)

## Mission

**En une phrase (quoi, et pourquoi) :** enregistrer pendant la capture d'une placette un film ARCore
(vidéo de la caméra et capteurs, au format MP4) que Groma sait rejouer comme une capture en direct,
parce que les paquets ne gardent que des images fixes. Le 2026-11-13, un bug a rendu inutilisables les
images de P065 et P067 : il a fallu retourner sur le terrain alors qu'un film aurait suffi à recalculer
(demande de Pascal du 2026-11-14 : « les films ne sont pas enregistrés sur le téléphone ? Ils ne
peuvent pas être rejoués ? »).

## Les trois questions

| Question | Réponse | Conséquence |
|---|---|---|
| Redire en une phrase ? | oui | |
| Sait-il comment faire ? | en partie | API ARCore d'enregistrement et de relecture (`Session.startRecording(RecordingConfig)`, `Session.setPlaybackDatasetUri`, pistes personnalisées `Track`), disponible dans `com.google.ar:core:1.57.0` déjà utilisé. À vérifier d'abord : relecture sur l'émulateur, taille réelle par minute sur le S23. |
| A-t-il les moyens ? | oui | S23 de Pascal (essai terrain), émulateur (relecture si ARCore la permet), tests JVM |

**Temps de la main :** soutenir — un agent dev dans son worktree, relu avant fusion, essai de Pascal.

## Ce qui est attendu

0. **Réglage.** Une case à cocher dans les paramètres de Groma, « Enregistrer le film de la capture »,
   **décochée par défaut**, décide si le film est enregistré (décisions de Pascal du 2026-11-14). Elle est lue au lancement de
   chaque capture ; décochée, la capture se passe exactement comme aujourd'hui.
1. **Enregistrement.** Si la case est cochée, `CaptureActivity` démarre l'enregistrement ARCore en même temps que la capture
   et l'arrête à « Terminer ». Le film `Pxxx.groma.mp4` est écrit à côté du paquet, dans
   `paquets/<projet>/`, et **hors du zip** : il n'est jamais envoyé au serveur et reste sur le
   téléphone, d'où on le récupère par câble si besoin (décision de Pascal du 2026-11-14). Une nouvelle capture de la
   même placette n'écrase pas le film précédent : un suffixe horodaté est ajouté.
2. **Piste d'identité dans le film.** Une piste personnalisée ARCore porte le projet, la placette, la
   version de l'appli, la date et le rayon et le seuil de la placette, pour qu'on sache ce que c'est
   sans le paquet.
3. **Jamais au détriment du paquet.** Si l'enregistrement échoue (place, ARCore), la capture continue
   et le paquet est écrit comme aujourd'hui ; un message dit que le film n'a pas pu être enregistré.
   Avant de commencer, Groma vérifie la place libre et prévient s'il en manque.
4. **Relecture, au bureau seulement.** « Rejouer un film » n'existe que dans la version d'essai
   (`fr.nemeton.groma.fumee`), jamais dans l'appli de terrain (décision de Pascal du 2026-11-14 : « on
   rejoue au bureau »), sur l'émulateur ou sur le S23 branché (elle s'installe à côté de l'appli de
   terrain sans y toucher ; le film y est copié par câble, l'appli d'essai ne lit pas les dossiers de
   l'appli de terrain). Elle choisit un `.groma.mp4` et lance
   `CaptureActivity` en mode relecture (`setPlaybackDatasetUri` avant `resume`). Le traitement est
   celui d'une capture en direct ; le paquet produit est marqué `source = rejeu` dans le manifeste et
   n'écrase jamais un paquet du terrain (règle 5).
5. **Documentation.** `docs/terrain.md` : la place prise par un film, où il se trouve, comment le
   rejouer, comment le récupérer sur le PC.

## Décisions de Pascal (2026-11-14)

- Case dans les paramètres, décochée par défaut.
- Le film reste sur le téléphone, récupéré par câble si besoin ; jamais envoyé au serveur.
- Rejeu au bureau seulement (version d'essai).

## Essais attendus

- Tests JVM : case décochée, aucun film et capture inchangée ; nommage des films (suffixe sans écrasement), seuil de place libre, manifeste `source = rejeu`.
- Essai de fumée : si l'émulateur sait rejouer un film ARCore, rejouer un film court enregistré sur
  le S23 et vérifier que le paquet produit a des images valides (lignes différentes, cibles détectées).
  Sinon, le dire dans la fiche et faire l'essai sur le S23.
- Essai de Pascal : filmer une placette, rejouer le film au bureau, comparer les deux paquets.

## Réalisé

- Réglage « Enregistrer le film de la capture » (`Reglages.enregistrerFilm`, préférence `film_capture`, décoché par défaut).
- `groma.core.FilmCapture` (JVM, testé) : décision enregistrer / pas / place insuffisante, nommage sans écrasement
  du film (`Pxxx.groma.mp4`, puis `Pxxx-<horodatage>.groma.mp4`, puis `-2`…) et du paquet de rejeu
  (`Pxxx.rejeu.groma.zip`), contenu JSON de la piste d'identité, `source` du manifeste, garde « version d'essai ».
- `CaptureActivity` : `startRecording` après `resume` (`RecordingConfig.setMp4DatasetFilePath`, piste
  personnalisée d'identité, un échantillon écrit à la première image), arrêt à « Terminer » ou à la destruction.
  Place libre vérifiée avant (`StatFs`, seuil provisoire 1 Gio). Tout échec : message, jamais d'exception vers la
  capture ; `terminer()` écrit le paquet avant le calcul embarqué (ordre de la mission 033 inchangé).
- Rejeu : bouton « Rejouer un film (version d'essai) » sur la fiche placette, seulement si l'identifiant de
  l'appli finit par `.fumee` (contrôlé dans `MainActivity` et dans `CaptureActivity`) ; `setPlaybackDatasetUri` avant
  `resume`, fin de film = « Terminer » automatique. Le paquet de rejeu porte `source = rejeu`, un nom distinct,
  n'est pas planifié à l'envoi ; le calcul embarqué est sauté (le projet n'est pas touché).
- `source` est écrit aussi pour les captures directes (`terrain`). Le modèle pydantic du serveur ignore les champs
  inconnus : aucun changement serveur (à déclarer dans le modèle si le serveur doit s'en servir : hors fiche).
- `docs/terrain.md` : section « Film rejouable de la capture ».

## Relecture ARCore sur l'émulateur : ce qu'on sait

- L'API existe dans `com.google.ar:core:1.57.0` (vérifié dans le jar : `Session.startRecording/stopRecording/
  getRecordingStatus`, `setPlaybackDatasetUri`, `getPlaybackStatus` avec `FINISHED`, `Track`,
  `Frame.recordTrackData`, `Frame.getUpdatedTrackData`).
- La documentation Google « Recording and Playback » ne dit rien de l'émulateur ; la page « Run AR Apps in Android
  Emulator » ne décrit que la scène virtuelle (x86, API 27+). **Non vérifié** : la relecture d'un MP4 sur
  l'émulateur n'a pas été essayée pendant le développement. **Essayée le 2026-11-20 : échec**, voir « Essais ».
- Taille réelle mesurée le 2026-11-20 : environ 72 Mio par minute (voir « Essais ») ; le seuil de 1 Gio de place
  libre couvre donc une dizaine de minutes de film.

## Essais

- JVM : `FilmCaptureTest` (décision case décochée / place / rejeu, nommage sans écrasement, paquet de rejeu
  distinct, `source`, garde `.fumee`, piste d'identité) ; `gradle :groma-core:test :app:testFumeeUnitTest` verts,
  `:app:assembleFumee` compile.
- **Essai de Pascal, 2026-11-20, Marsannay (projet `20261015_194200_yzoh`), placette P067, 15 m.**
  * Enregistrement sur le S23 (build release de la branche) : film `P067.groma.mp4` de 2 min 52 s, 206 Mio
    (≈ 72 Mio/min), vidéo 640×480 à 30 i/s et 5 pistes de capteurs ARCore. Paquet de 91 Mio, 525 images toutes
    distinctes (pas de lignes répétées, cf. mission 033), boucle fermée, cibles C1-C3 mesurées.
  * Chaîne serveur (méthode `profondeur` seule, pas de GPU) sur le paquet du terrain : 26 arbres, verdict « à
    vérifier ». Fermeture de boucle 7,7 m, échelle 0,91 (non appliquée), C2 jamais détectée, résidu d'orientation
    4,2° sur C1-C3, diamètres des cibles faux (C1 −4,5 cm, C3 +37,5 cm : mauvais arbre apparié).
  * **Rejeu sur l'émulateur : échec.** AVD API 36.1 Play Store x86_64, ARCore 1.57 pour émulateur, caméra arrière
    en scène virtuelle : la seule caméra exposée porte le n° 10, ARCore écrit « No default camera 0 found » et
    `Session(...)` lève `FatalException` avant tout `setPlaybackDatasetUri` (la relecture ne contourne pas la
    caméra). Même résultat sans caméra avant. Non essayé : image système API 34/35.
  * **Défaut trouvé et corrigé** : cette `FatalException` n'était pas rattrapée, l'appli se fermait. Désormais
    `CaptureActivity` affiche « ARCore n'a pas pu démarrer sur cet appareil (…) » et revient à la placette, en
    capture comme en rejeu (création et reprise de la session ; le fil de rendu ne touche plus une session
    absente). Non testable sur la JVM (cycle de vie d'une Activity, pas de Robolectric) ; vérifié par compilation.
    À revérifier sur l'émulateur.
  * **Rejeu sur le S23 : réussi.** Version d'essai `fr.nemeton.groma.fumee` installée à côté de l'appli de terrain
    (aucune donnée de terrain touchée). Paquet `P067.rejeu.groma.zip` de 87 Mio, `source = rejeu`, 530 images toutes
    distinctes, mêmes instants que le terrain (171 s), suivi ARCore actif sur toutes les images.
  * Comparaison des trajectoires : à instant égal, le rejeu reste à ≈ 0,6-0,75 m du terrain (4 m au plus). La
    dérive se reproduit : écart début-fin 7,5 m au rejeu contre 7,7 m au terrain. Le film permet donc d'étudier
    cette dérive au bureau.
  * Détections au rejeu : C1 ×13, C2 ×4, C3 ×7, **C0 jamais** (terrain : C1 ×17, C3 ×5, C0 ×3). Sans C0, la chaîne
    serveur refuse le paquet rejoué (« Repère central C0 non retrouvé »).
  * Pendant l'essai, la version d'essai a lu le dossier des projets de l'appli de terrain (`Download/Groma`, copie
    d'octobre, 20 m) au lieu de la copie à 15 m déposée à part : manifeste du rejeu à 20 m, sans cibles mesurées.
    Images, poses et profondeurs n'en dépendent pas.
  * Fichiers de l'essai (hors dépôt) : `~/groma-sauvegardes/2026-11-20_P067-film/`.
- Restent ouverts : la dérive de 7,7 m sur P067 et C0 manquante au rejeu (hors du périmètre de la mission 036).
