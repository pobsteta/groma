# Mission 045 — Voir les images retenues, avec les arbres identifiés en surimpression

## Mission

**En une phrase (quoi, et pourquoi) :** sous le bouton « Filmer » de la vue Placette, un bouton
« Voir les images » qui fait défiler les images retenues du paquet de la placette, avec les arbres
identifiés dessinés par-dessus (numéro et diamètre), pour que Pascal voie sur les vraies images ce
que Groma a reconnu comme arbre, où, et ce qu'il a manqué (demande de Pascal du 2026-11-21 ; ce
n'est pas le film mp4).

**Redite par l'agent :** ajouter sous « Filmer » un bouton « Voir les images » qui ouvre, en lecture seule, les
images retenues du paquet de la placette (une à la fois, glisser ou curseur, « n / N »), avec les arbres du
projet dessinés par-dessus (trait du pied à 1,30 m, « numéro · diamètre ») et les cibles C0 à C3 vues, pour que
Pascal juge sur les vraies images ce que Groma a reconnu et manqué. Aller-retour 1 : arbres ajoutés à la main en
pointillé, case des faux positifs écartés, cibles et centre du projet d'abord, et les remarques du relecteur.

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui : `ui/EcranPlacette.kt` (bouton « Filmer », l. 388), manifeste du paquet (poses `world_from_camera` en colonnes, intrinsèques, `detections_cibles`), `CalculEmbarque.kt` (triangulation des cibles, `Georeferencement.georeferencer`, repère local x = X, y = −Z, z = Y), table `detections` | |
| A-t-il les moyens ? | oui : paquet réel `P067.groma.zip` (525 images retenues, C0 vue 3 fois, C1 17, C3 5) et 15 arbres provisoires de P067 sur le S23 ; copie dans `~/groma-sauvegardes/2026-11-20_P067-film/` | |

**Temps de la main :** donner. **Degré de la main :** le chef tranche.

## Ma réponse d'abord

_Rempli par Pascal seul._

## L'Homme

- **Agent :** `afficheur`. **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** le film mp4 et son rejeu ; une nouvelle détection d'arbres ;
  modifier un arbre depuis cet écran.

## Ce qui est attendu

1. **Bouton « Voir les images »** sous « Filmer », actif quand la placette a un paquet
   (`paquets/<projet>/<plot_id>.groma.zip`, le plus récent hors `rejeu`), grisé sinon.
2. **Écran des images** : une image retenue à la fois, plein écran, dans l'ordre du manifeste ;
   image suivante et précédente (glisser), curseur pour aller vite, « 123 / 525 ». Lecture en tâche
   de fond, image par image (le zip fait près de 100 Mo : jamais tout en mémoire).
3. **Arbres en surimpression** : les arbres de la placette (table `detections`, dernière version de
   chaque `uuid`, provisoires et définitifs) placés dans le repère de la capture puis projetés sur
   chaque image avec sa pose et ses intrinsèques : trait vertical du pied à 1,30 m, et
   « numéro · diamètre » (« 3 · 32 cm ») ; couleurs des arbres de la carte selon l'état. Les arbres
   derrière la caméra ou hors de l'image ne sont pas dessinés.
4. **Passage Lambert-93 → repère de la capture** : refait à partir du paquet seul (triangulation de
   C0 à C3 sur leurs `detections_cibles`, cibles mesurées du projet), comme `CalculEmbarque`, en
   fonction pure de `groma-core`. **Pied de l'arbre** : sol local tiré des profondeurs du paquet
   près du tronc, ou à défaut le MNT de la placette calé sur ces profondeurs ; l'agent choisit et le
   dit. Sans C0 ou sans cible orientée : les images s'affichent sans arbres, avec le message
   « C0 non vue : arbres non placés sur les images » (ou équivalent).
5. **Cibles** : les détections de C0 à C3 de l'image (`detections_cibles`) marquées d'un petit
   carré avec leur nom : elles montrent ce qui a servi à placer les arbres.
6. **Lecture seule** : rien n'est écrit dans le projet ni dans le paquet.

## Harnais

- **Peut (périmètre) :** `ui/EcranPlacette.kt` (le bouton), un nouvel écran dans `ui/`,
  `MainActivity.kt` (navigation), `Projet.kt` (chemin du paquet, lecture des détections),
  `CalculEmbarque.kt` (seulement extraire ce qui est partagé, sans changer son résultat),
  `android/groma-core/src/main/` (lecture du manifeste, passage Lambert-93 → repère local,
  projection sténopé, sol local) et ses tests, `res/values/strings.xml`, `docs/terrain.md`,
  `PLAN.md`, journal (ajout seul).
- **Câblé :** pas de `main` ; pas de « terminé » sans tests verts ; journal en ajout seul.
- **Ne doit pas :** bloquer le fil principal ; charger tout le paquet en mémoire ; écrire dans le
  GeoPackage ou le paquet ; changer la capture ni le calcul de fin de capture.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** trait de 1,30 m ; taille du texte ; carré de cible de 12 dp.
- **Mesurée par :** P067 sur le S23 (les traits tombent-ils sur les troncs ?).
- **Gel :** après l'avis de Pascal.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** rien. **CI rouge attendue :** aucune.

## Tests

- groma-core : projection sténopé (point devant → pixel attendu ; derrière → non dessiné) avec la
  convention de pose du paquet (`world_from_camera`, colonnes, caméra regardant −Z) ;
  aller-retour Lambert-93 → repère local → Lambert-93 au millimètre ; scène synthétique (caméra,
  cibles C0 à C3 vues, deux arbres) → les arbres retombent à moins de 2 px de leur place.
- Manifeste réel : lecture du manifeste de `exemples/marsannay_p067/paquets/P067.groma.zip`
  (525 images, poses, `detections_cibles`) sans tout décompresser.

## Terrain

- **Vérifié par l'orchestrateur au téléphone** (S23 branché) avant la fusion, dans la version
  d'essai `fumee` (paquet distinct : la version de terrain et ses données restent ; installée
  seulement avec l'accord de Pascal), avec une copie du projet de Marsannay et de
  `P067.groma.zip` dans ses dossiers : « Voir les images », captures d'écran de trois images où
  des arbres sont marqués, fluidité, `logcat` sans erreur. **Jamais** de version debug par-dessus
  la version de terrain (désinstallation, données perdues).
- **Par Pascal :** dire si les traits tombent sur les bons troncs et lesquels manquent.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Sur P067, Pascal fait défiler les images et voit, sur chaque tronc reconnu, son numéro et son
diamètre ; il repère d'un coup d'œil les arbres manqués et les fausses détections.

## Aller-retour 1/3 (2026-11-21)

**Relecteur :** prêt pour Pascal (398 tests verts, lecture seule et hors fil principal vérifiés,
poses en lignes confirmées : la fiche se trompait en disant « en colonnes »).

**Décisions de Pascal (2026-11-21) :**

1. **Arbres dessinés** : la table `arbres` (comme le plan), **mais** les arbres ajoutés à la main
   (`methode = 'terrain'`) dessinés autrement (trait pointillé, provisoire), et une case
   **« Montrer les faux positifs écartés »** (décochée par défaut) qui ajoute, dans un style
   distinct (gris, barré ou équivalent), les détections marquées faux positifs.
2. **Cibles et centre** : ceux du **projet** (dernières cibles mesurées de la placette, dernier C0
   de `centres_mesures`), qui placent aussi les arbres ; le manifeste du paquet **seulement** si le
   projet n'a pas de cibles pour la placette (le dire dans un message discret).

**Remarques non bloquantes du relecteur, à traiter :**

3. `PlacementPaquet.calculer` recopie la chaîne de `CalculEmbarque` (3 vues, seuil 3°) : renvoi
   croisé en commentaire dans les deux, ou fonction commune si c'est simple et sans changer le
   résultat de `CalculEmbarque`.
4. `MainActivity` : ne plus lister le dossier des paquets pendant la composition (le faire hors fil
   principal) et ne plus modifier un état pendant la composition.
5. Remplacer la sentinelle `"SOL"` de `ArbresSurImages.message` par un type explicite ; retirer la
   ressource `images_retour` inutilisée.
6. Compléter la rubrique « Redite par l'agent ».

Ne pas installer sur le téléphone : l'essai au S23 (version `fumee`, accordé par Pascal) est fait par
l'orchestrateur après la relecture.
