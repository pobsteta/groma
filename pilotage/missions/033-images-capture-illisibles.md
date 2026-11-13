# Mission 033 — Images de capture illisibles (première ligne répétée)

## Mission

**En une phrase :** corriger la lecture de l'image caméra d'ARCore, qui recopiait la première ligne du
capteur sur toute l'image, parce que les premiers tournages en forêt (Marsannay-la-Côte, placettes 65 et
67, 2026-11-13) n'ont trouvé aucune cible alors qu'elles étaient bien visibles à l'écran.

**Temps de la main :** appuyer — réalisé par l'orchestrateur, essai de Pascal.

## Constat

- Paquets `P065.groma.zip` et `P067.groma.zip` (1 200 et 1 230 images 640 × 480) : `detections_cibles`
  vide ; toutes les lignes de chaque image identiques (écart moyen entre lignes successives 0,0) ; aucune
  cible trouvée par OpenCV, même avec des réglages souples.
- Cause : `Image.Plane.getBuffer()` d'ARCore renvoie un NOUVEAU tampon à la position 0 à chaque appel.
  `luminance()` faisait `p.buffer.position(...)` puis `p.buffer.get(...)` sur un autre tampon : chaque
  ligne relisait la première. Le détecteur ArUco du téléphone travaillait sur ces pixels. Code inchangé
  depuis le prototype (#1).
- Profondeur (`compacter`, tampon pris une fois) et poses : justes. Images de P065 et P067 perdues :
  à refilmer.

## Réalisé

- `groma.core.ImageYuv.luminance` et `ImageYuv.nv21` : fonctions pures, lecture par index absolus
  (pas de ligne, pas de pixel) ; `CaptureActivity` prend chaque tampon une seule fois et calcule la
  luminance une fois pour le détecteur et le JPEG.

- « Terminer » plantait (« Aucune cible commune entre la prise de vue et les mesures », P001 sans cible
  placée) AVANT l'écriture du paquet : tournage perdu. Le paquet est maintenant écrit en premier ; le calcul
  sur le téléphone est protégé (message, paquet gardé) ; des cibles vues mais jamais placées donnent un
  message explicite au lieu d'une exception.

## Essais

- `ImageYuvTest` (4 cas écrits à la main : pas de ligne avec bourrage, position du tampon ignorée,
  pas de pixel 2, NV21 entrelacé).
- À faire par Pascal : court tournage avec une cible, contrôle des images du paquet.
