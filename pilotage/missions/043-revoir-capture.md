# Mission 043 — Revoir la capture avec les arbres détectés en surimpression

## Mission

**En une phrase (quoi, et pourquoi) :** dans l'appli de terrain, depuis la vue Placette, faire
défiler les images du paquet d'une placette comme un film, avec les arbres détectés dessinés sur
chaque image où ils sont visibles (numéro et diamètre) et les cibles C0 à C3, pour repérer sur
place un arbre oublié ou mal placé avant de quitter la placette, et comprendre au bureau une dérive
comme celle de P067 (demande de Pascal du 2026-11-21).

**Redite par l'agent :** _à compléter par l'agent avant d'agir._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | **en partie** : le paquet a déjà, par image, la photo, la pose ARCore (matrice 4×4) et les intrinsèques (`fx`, `fy`, …) ; les arbres sont en Lambert-93 dans le GeoPackage. Il faut passer du Lambert-93 au repère local de la capture : le géoréférencement (`groma.core.Georeferencement`, `Georef`) est calculé par `CalculEmbarque` mais **pas conservé** ; et la hauteur du sol au pied de l'arbre n'est pas connue telle quelle | **donner la main** : un plan court d'abord (comment retrouver la transformation et le sol), soumis à l'orchestrateur |
| A-t-il les moyens ? | oui : paquets réels P067 (`~/groma-sauvegardes/2026-11-20_P067-film/`, 525 images, `detections_cibles`), exemple, S23 branché (vérification d'écran par l'orchestrateur) | |

**Temps de la main :** donner — plan court d'abord. **Degré de la main :** le chef tranche.

## Ma réponse d'abord

_Rempli par Pascal seul._

## L'Homme

- **Agent :** `afficheur` ; projection et géoréférencement testés en JVM. **Modèle :** opus (justesse
  géométrique : repère ARCore, Lambert-93, nord, convergence).
- **Ce qui ne lui est pas demandé :** recalculer la détection ; le rejeu ARCore (reste dans la version
  d'essai, mission 036) ; écrire quoi que ce soit dans le paquet ou le GeoPackage.

## Ce qui est attendu

1. **Bouton « Revoir la capture »** dans la vue Placette, visible quand la placette a un paquet
   (dernier paquet de la placette, terrain ou rejeu) ; lecture seule.
2. **Visionneuse** : image par image, lecture continue et pause, curseur pour aller à une image,
   avance et recul d'une image ; affichage « image n / N » et état du suivi ARCore de l'image.
3. **Surimpression** sur chaque image :
   * chaque arbre du GeoPackage visible dans le champ : un trait vertical du sol à ~2 m (ou un
     repère à 1,30 m) à sa position, avec « numéro – diamètre » ; couleur du groupe d'essence comme
     sur le plan ; arbre ajouté à la main en pointillé (même convention que le plan) ;
   * les cibles C0 à C3 (position mesurée) et, quand l'image en a, les détections de cibles du
     paquet (`detections_cibles`, `u`, `v`) ;
   * rien n'est dessiné derrière la caméra ni hors de l'image.
4. **Transformation** : la même que celle du calcul embarqué (C0 à C3 triangulés à partir des
   `detections_cibles`, `Georeferencement.georeferencer`), recalculée à l'ouverture à partir du
   paquet, ou conservée dans le paquet par `CalculEmbarque` si le plan le juge plus sûr (alors
   champ facultatif du manifeste, lu par le serveur sans erreur). Sans géoréférencement possible
   (moins de deux cibles vues) : images sans arbres, message.
5. Lecture des images depuis le zip, sans tout décompresser ; fluide sur le S23 (au moins 5 images
   par seconde en lecture continue).

## Harnais

- **Peut (périmètre) :** nouveau `android/app/src/main/kotlin/groma/app/ui/RevoirCapture.kt` (et
  fichiers voisins `ui/`), `ui/EcranPlacette.kt` (bouton seulement), `MainActivity.kt` (navigation),
  `Projet.kt` (lecture des arbres, lecture seule), nouveau `android/groma-core/src/main/kotlin/groma/core/Projection.kt`
  et tests `android/groma-core/src/test/`, `res/values/strings.xml`, `docs/terrain.md`, `PLAN.md`,
  journal (ajout seul). Si le plan retient de conserver la transformation : `CalculEmbarque.kt` et
  `EnregistreurPaquet.kt` (écriture du champ), `server/tests/` (manifeste relu) — **après 039**, qui
  touche `EnregistreurPaquet.kt`.
- **Câblé :** pas de `main` ; pas de « terminé » sans tests verts ; journal en ajout seul.
- **Ne doit pas :** écrire dans le paquet ou le GeoPackage pendant la visionneuse ; bloquer le fil
  principal ; toucher à `CaptureActivity.kt` (039) ; inventer une convention d'axes sans test.

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** trait de 0 à 2 m au-dessus du sol (ou repère à 1,30 m) ; 5 images/s.
- **Mesurée par :** vérification d'écran par l'orchestrateur sur P067 (arbres posés sur leurs troncs
  dans les images), puis avis de Pascal.
- **Gel :** après cet essai.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** rien pour la visionneuse ; **après 039** si la transformation est conservée dans le
  paquet (fichiers communs).
- **CI rouge attendue :** aucune.

## Tests

- groma-core : projection d'un point local dans l'image (pose ARCore 4×4 en colonnes, caméra regardant
  vers −z, intrinsèques) — point devant → pixel attendu, point derrière → absent ; aller-retour
  Lambert-93 ↔ local avec `Georef` (inverse de `versGrille`) ; sur P067 : les cibles triangulées,
  reprojetées, retombent à quelques pixels des `detections_cibles` (`u`, `v`).

## Terrain

- **Vérifié par l'orchestrateur au téléphone :** P067 (paquet du 2026-11-20) : défilement, arbres et
  cibles posés sur les troncs et les plaques, captures envoyées à Pascal.
- **Par Pascal :** sur le terrain, après une capture, la visionneuse lui fait-elle voir un arbre oublié ?

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

En revoyant P067, Pascal voit chaque arbre détecté posé sur son tronc, et repère d'un coup d'œil un
tronc sans étiquette.
