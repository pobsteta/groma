# Benchmark

Comparaison des diamètres, positions et hauteurs de chaque méthode
(embarquée, classique, IA, houppiers) aux mesures manuelles d'une placette
test (ruban, boussole de visée, décamètre). Objectifs du brief : ≥ 95 %
des arbres détectés, RMSE du diamètre ≤ 2 cm, erreur de position ≤ 0,5 m.

**État** : script prêt, en attente d'une vraie placette. Les chiffres
obtenus jusqu'ici l'ont été sur données synthétiques uniquement (voir
`server/tests/test_chaine.py`) et ne préjugent pas du terrain.

```bash
python benchmark/comparer.py --manuel P001_manuel.csv --placette P001.json \
  --methode classique=P001_definitif.gpkg --methode ia=P001_ia.gpkg \
  --methode embarque=inventaire_video.gpkg --plot-id P001 --sortie P001_benchmark.json
```

Format de `P001_manuel.csv` : `tree_id,azimut,distance_m,dbh_cm,h_m`
(azimut magnétique dans l'unité de la placette, distance horizontale du
centre à l'écorce, `h_m` facultatif). `P001.json` :
`{"centre": [x, y], "unite_azimut": "gr", "declinaison_deg": 2.4, "convergence_deg": 2.1}`.

## Segmentation des troncs (`segmentation_troncs.py`)

Compare, sur des images clés annotées à la main, l'adaptateur SAM 2 du
serveur (points posés sur les ruptures de la profondeur ARCore), SAM 3
(invite texte) et WeVisOne-VL 2B ou 4B (Tencent, tâche `segmentation`,
invite texte). La chaîne 3D ne se sert du masque que pour garder les pixels
de tronc ; le diamètre vient de l'ajustement de cercle. Ce qui compte d'abord
est donc le **nombre de troncs trouvés**, puis le coût par image ; la
finesse des bords passe au second plan.

**État** : script et tests prêts (données synthétiques, segmenteurs
simulés). Aucun des trois modèles n'a encore tourné ici (pas de GPU) ;
en attente d'une placette annotée.

### 1. Extraire et annoter

```bash
python benchmark/segmentation_troncs.py extraire --paquet P001.groma.zip --sortie P001_annot --nombre 50
```

Écrit 50 images clés réparties sur la boucle dans `P001_annot/images/`
(`000123.jpg`, numéro d'image du paquet). Les annoter dans CVAT ou Label
Studio, **un objet par tronc**, partie visible seulement (sans branches
ni houppier) :

* catégorie `tronc` : chaque tige de la placette, y compris les perches
  au-dessus du seuil de précomptage ;
* catégorie `ignorer` : ce qu'on ne veut compter ni comme trouvé ni comme
  faux positif (troncs hors placette au loin, souches, zones floues).

Deux formats acceptés : export COCO (`--coco instances.json`, polygones ou
RLE) ou un PNG d'étiquettes par image dans `P001_annot/masques/`
(`000123.png`, niveaux de gris, 0 = fond, 1…N = un tronc chacun, 255 en
8 bits ou 65535 en 16 bits = à ignorer).

### 2. Évaluer

Sur une machine GPU (L4 ou équivalent, 24 Go) :

```bash
python benchmark/segmentation_troncs.py evaluer --paquet P001.groma.zip \
  --coco P001_annot/instances.json \
  --segmenteur sam2 --segmenteur sam3 --segmenteur wevisone-4b \
  --sortie P001_segmentation.json --apercus P001_apercus
```

Un segmenteur non installé est signalé dans le rapport et sauté. Les trois
modèles ont des dépendances incompatibles entre elles ; les lancer chacun
dans son environnement, avec le même `--sortie` changé de nom :

| Segmenteur | Environnement |
|---|---|
| `sam2` | image `groma-worker-gpu` (déjà installé) |
| `sam3` | `pip install "transformers>=5.18"` ; poids `facebook/sam3` **à accès demandé** sur Hugging Face (`hf auth login`) |
| `wevisone-4b`, `wevisone-2b` | venv à part : `git clone https://github.com/Tencent/WeVisOne-VL && pip install -e WeVisOne-VL` (épingle torch 2.11 et transformers 5.12.1, incompatibles avec l'image du worker), puis `pip install -e core -e server` |

L'invite texte se change avec `--requete` (défaut `tree trunk`).

### Indicateurs

Appariement un à un des troncs prédits et annotés (algorithme hongrois sur
l'IoU, hors zone ignorée), retenu si IoU ≥ 0,5 (`--seuil-iou`).

| Indicateur | Sens |
|---|---|
| `rappel` | part des troncs annotés retrouvés ; **critère principal** |
| `precision` | part des troncs prédits qui sont de vrais troncs |
| `iou_moyen_apparies` | qualité du masque des troncs retrouvés |
| `iou_pixels`, `rappel_pixels` | recouvrement global ; un `rappel_pixels` haut avec un `rappel` bas signale des troncs fusionnés |
| `corrections_par_image` | oublis + faux positifs par image : travail de reprise si le modèle sert d'annotateur |
| `temps_median_s`, `temps_placette_min` | temps par image hors chargement et hors première image ; estimation pour une placette (`--images-par-placette`, 300 par défaut) |
| `vram_max_go` | pic de mémoire GPU pendant la segmentation |

SAM 2 renvoie un seul masque par image : ses troncs sont les composantes
connexes, donc deux tiges qui se touchent à l'image n'en font qu'une. Les
aperçus (`--apercus`) superposent les prédictions (couleurs) et les
troncs annotés (contour blanc).

### Lecture des résultats

* Segmenteur du serveur : le meilleur `rappel` à condition de tenir
  dans le budget de temps du worker (objectif de la chaîne : < 15 min par
  placette).
* WeVisOne-VL comme **annotateur** pour entraîner le modèle embarqué :
  `corrections_par_image` faible et `iou_moyen_apparies` ≥ 0,8, même si
  le temps par image est élevé.
