# Mission 044 — Exemple réel 1.7.0 : P067 de Marsannay-la-Côte

## Mission

**En une phrase (quoi, et pourquoi) :** construire, par un script reproductible, un deuxième projet
d'exemple `exemples/marsannay_p067/` au schéma 1.7.0 à partir des données réelles de Marsannay
(LiDAR HD, placette P067 filmée le 2026-11-20), pour que les tests de 039 et les vérifications
d'écran (042, 043) portent sur un vrai terrain (demande de Pascal du 2026-11-21).

**Redite par l'agent :** _à compléter par l'agent avant d'agir._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui : `exemples/creer_exemple.py` (même construction, `groma.gpkg.creer_gpkg`, `groma.dalles`), schéma 1.7.0 | |
| A-t-il les moyens ? | oui, sauf le CVAT : MNT et MNH LiDAR HD de nemeton (1 m), GeoPackage du projet, paquet P067 ; **le CVAT de Marsannay sera calculé par Pascal dans nemetonshiny** | confier ; CVAT ajouté quand il existe |

**Temps de la main :** donner. **Degré de la main :** le chef tranche.

## Ma réponse d'abord

_Rempli par Pascal seul._

## L'Homme

- **Agent :** `dev`. **Modèle :** opus (justesse des données réelles, coordonnées).
- **Ce qui ne lui est pas demandé :** calculer le CVAT (Pascal, nemetonshiny) ; modifier nemeton ou
  nemetonshiny ou leurs données (lecture seule).

## Ce qui est attendu

1. **Script** `exemples/creer_marsannay_p067.py`, qui lit en **lecture seule** (`mode=ro` pour les
   GeoPackage, chemins passés en arguments, valeurs par défaut documentées) :
   * MNT et MNH de la forêt : `~/.local/share/nemeton/projects/20261015_194200_yzoh/cache/layers/lidar_mnt/dtm.tif`
     et `lidar_mnh/chm.tif` (1 m, EPSG:2154) ;
   * le GeoPackage du projet : `~/groma-sauvegardes/2026-11-20_P067-film/projets/20261015_194200_yzoh/inventaire_video.gpkg` ;
   * le paquet de terrain : `~/groma-sauvegardes/2026-11-20_P067-film/P067.groma.zip` ;
   * le CVAT, **facultatif** (`--cvat`), quand Pascal l'aura calculé.
2. **Projet écrit** (`creer_gpkg`, schéma 1.7.0) :
   * placette **P067 centrée sur le C0 mesuré** (décision de Pascal du 2026-11-21 : centre du
     paquet / `centres_mesures`, (849 659,22 ; 6 689 237,97)), rayon 15 m, attributs repris du projet
     réel ; le centre relevé dans `centres_mesures` ; les cibles C1 à C3 du paquet ;
   * emprise de la forêt réduite pour garder l'exemple petit : carré de 100 m autour du C0 mesuré
     plus la marge du schéma (`marge_foret_m`), une `ugf` et une `parcelle` réelles qui la couvrent
     (découpées) ;
   * **dalle MNT** `rasters/mnt_0849_6689.tif` au format `rasters.format` (2 000 × 2 000, 0,5 m),
     rééchantillonnée en bilinéaire depuis le MNT à 1 m sur l'emprise, nodata ailleurs (le fichier
     reste petit) ; rasters par placette `P067_mnt.tif` / `P067_mnh.tif` autour du C0 mesuré ;
   * **houppiers** avec `x_sommet`, `y_sommet` sur l'emprise : segmentation du MNH par maxima locaux
     et ligne de partage des eaux (`scipy.ndimage`, pas de nouvelle dépendance), sommet = pixel de
     `h_max` ; `plot_id` = P067 s'ils touchent la placette, sinon NULL ; marqués comme calculés par
     Groma pour l'exemple (remarque dans `projet` ou le README), pas par nemeton ;
   * `rasters/cvat.tif` au format `rasters.cvat` découpé sur l'emprise **si** `--cvat` est fourni.
3. **Paquet d'exemple** : copie réduite du paquet P067 (manifeste complet, une image sur dix au plus,
   moins de 5 Mo) dans `exemples/marsannay_p067/paquets/`, pour 039 et 043.
4. **Taille** : moins de 6 Mo commités au total. `README.md` : sources, dates, licence des données
   (LiDAR HD IGN, Licence Ouverte 2.0), ce qui est réel et ce qui est calculé.
5. **Tests** `core/tests/test_exemple_marsannay.py` : `verifier_projet` sans écart ; P067 centrée sur
   le C0 mesuré ; chaque sommet dans son houppier ; la dalle couvre le disque de la placette ;
   valeurs de la dalle proches du MNT à 1 m (écart < 5 cm aux nœuds communs).

## Harnais

- **Peut (périmètre) :** `exemples/creer_marsannay_p067.py`, `exemples/marsannay_p067/`,
  `core/tests/test_exemple_marsannay.py`, `docs/` (section des exemples), `PLAN.md`, journal (ajout seul).
- **Câblé :** pas de `main` ; pas de « terminé » sans tests verts ; journal en ajout seul.
- **Ne doit pas :** écrire dans les dossiers de nemeton, de nemetonshiny ou de `~/groma-sauvegardes`
  ; ajouter une dépendance ; toucher à `exemples/inventaire_video` (l'exemple synthétique reste pour
  les cas limites, comme le coin de quatre dalles).

## Valeur provisoire (chiffre, réglage)

- **Valeur provisoire :** MNT rééchantillonné de 1 m à 0,5 m ; segmentation simple (pas celle de
  nemeton).
- **Mesurée par :** comparaison au premier projet 1.7.0 livré par nemeton pour Marsannay.
- **Gel :** remplacée par le projet nemeton quand il existe.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** rien ; en parallèle de 039 (aucun fichier commun). Le CVAT arrive après le calcul
  de Pascal (commit de plus sur la même branche).
- **CI rouge attendue :** aucune.

## Terrain

- **Vérifié par l'orchestrateur au téléphone :** projet importé sur le S23 ; vue Placette de P067 :
  relief (quand le CVAT existe), contours et sommets autour du C0 mesuré.
- **Par Pascal :** les houppiers calculés ressemblent-ils à ce qu'il a vu sur place ?

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.
