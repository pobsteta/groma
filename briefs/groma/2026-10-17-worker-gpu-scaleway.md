# Brief Groma — worker GPU à la demande sur Scaleway

Date : 2026-10-17, complété le même jour (MapAnything à la place de VGGT,
profondeur affinée par un modèle). Point ouvert 4 de `PLAN.md` (image Docker
GPU : COLMAP avec CUDA, reconstruction et profondeur par IA, SAM 2). Référence : les scripts Scaleway de maestronemeton
(`inst/scripts/deploy_scaleway.sh`, `recover_model.sh`, `mount_data.sh`),
en lecture seule.

## 1. Besoin

* Le serveur reçoit les paquets des téléphones et les projets de
  nemetonshiny : **l'API doit être joignable en permanence**.
* Le calcul définitif d'une placette (COLMAP dense, reconstruction et
  profondeur par IA, SAM 2) demande un
  GPU, **par petites tâches irrégulières** : quelques minutes par placette,
  surtout le soir d'une journée de terrain.
* Aucun GPU utilisable ici : la Quadro K5100M du poste (Kepler, 2013) est
  trop ancienne pour CUDA 12 et PyTorch.
* Une L4 allumée en permanence coûterait environ 670 €/mois pour rester
  presque toujours inactive.

maestronemeton loue une instance GPU à la main pour un entraînement de
plusieurs heures (`scw instance server create`, L4-1-24G, image
`ubuntu_jammy_gpu_os_12`, zone fr-par-2, suppression manuelle). Groma
reprend l'outillage, mais pas le mode d'emploi : il faut une API toujours
allumée et un GPU allumé seulement quand il y a du travail.

## 2. Architecture cible

```
téléphones, nemetonshiny ──HTTPS──▶ API (instance CPU, toujours allumée)
                                     │  file SQLite, projets QField
                                     ├──▶ Object Storage (fr-par) : paquets, résultats
                                     └──▶ crée / supprime ──▶ instance GPU L4
                                                               worker (image CUDA)
                                                               ◀──HTTPS── prend, rend
```

* **API** : petite instance sans GPU (ou machine déjà disponible), Docker
  Compose actuel, derrière un proxy HTTPS (Caddy, certificat Let's
  Encrypt). Le HTTPS règle aussi le refus du HTTP non chiffré par l'APK de
  publication, qui ne l'autorise que vers `127.0.0.1`.
* **Stockage** : Scaleway Object Storage, déjà pris en charge par
  `GROMA_S3_URL`.
* **Worker GPU** : image CUDA lancée sur une instance L4 créée à la
  demande. Il ne partage plus de disque ni de base SQLite avec l'API : il
  passe par elle en HTTPS.

## 3. Worker par HTTP (PR 1, testable sans GPU) — fait (#56)

Aujourd'hui, `worker.traiter_une` ouvre la file SQLite et le paquet sur le
volume partagé. Ajouter un mode distant, sans retirer le mode local (une
seule machine, comme maintenant) :

| Méthode | Chemin | Rôle |
|---|---|---|
| POST | `/worker/prendre` | prend la plus ancienne tâche en attente (même règle que `FileAttente.prendre`) ; 204 si la file est vide |
| GET | `/worker/taches/{tid}/paquet` | télécharge le paquet, reprise par `Range` |
| POST | `/worker/taches/{tid}/bail` | prolonge le bail pendant un long calcul |
| PUT | `/worker/taches/{tid}/resultats/{nom}` | dépose `inventaire.gpkg`, `rapport.json`, `rapport.html` |
| POST | `/worker/taches/{tid}/terminer` | `{verdict}` ; l'API range les résultats comme aujourd'hui |
| POST | `/worker/taches/{tid}/echouer` | `{erreur}` ; mêmes règles de nouvelle tentative |

* Jeton distinct, `GROMA_JETON_WORKER`, refusé sur les autres routes ; le
  jeton des téléphones est refusé sur `/worker/…`.
* `groma-worker --api https://… ` lance le mode distant ; sans `--api`,
  rien ne change.
* `--arret-inactif 10m` : le worker s'arrête après 10 minutes sans tâche.
* Tests pytest : prise atomique par deux workers, bail expiré repris,
  résultats rangés comme en mode local, jetons séparés.

## 4. Image GPU (PR 2, construite en CI sans GPU)

`server/Dockerfile.gpu` :

* base `colmap/colmap` (COLMAP compilé avec CUDA, BSD-3) ou
  `nvidia/cuda:12.x-devel` avec compilation de COLMAP : à trancher sur la
  taille et la version de CUDA ;
* PyTorch CUDA 12, SAM 2, MapAnything et Prompt Depth Anything installés
  depuis leurs dépôts, à des commits épinglés ; VGGT en option, seulement
  pour les essais (§ 7) ;
* **aucun poids de modèle dans l'image** : téléchargés au premier lancement
  depuis Hugging Face dans un cache sur le disque de l'instance, en ne
  retenant que des points de contrôle Apache-2.0 (§ 7) ;
* CI : construction et publication sur GHCR
  (`ghcr.io/pobsteta/groma-worker-gpu`), comme l'image actuelle. Aucun test
  GPU en CI.

## 4 bis. Méthodes à comparer (adaptateurs, PR 2 bis)

Le serveur choisit déjà entre plusieurs reconstructions
(`GROMA_RECONSTRUCTEURS`) et garde la meilleure selon les cibles. La session
GPU les compare sur une même placette :

| Méthode | Principe | Statut |
|---|---|---|
| `profondeur` | fusion des profondeurs ARCore enregistrées par le téléphone | existante, sans GPU |
| `profondeur_ia` | **nouvelle** : chaque profondeur ARCore, basse résolution et bruitée, sert d'indication à **Prompt Depth Anything**, qui rend une profondeur métrique nette à la résolution de l'image ; puis même fusion que `profondeur`. Repli : **Metric Video Depth Anything Small** ou **Depth Anything 3 Metric**, remis à l'échelle par ARCore | à écrire |
| `colmap` | structure-from-motion puis densification ; essayer aussi le mappeur global **GLOMAP**, intégré à COLMAP, plus rapide | existante |
| `mapanything` | **remplace `vggt` par défaut** : reconstruction directe par **MapAnything**, en lui donnant en plus ce que le téléphone mesure déjà (calibration, poses et profondeur ARCore), pour un résultat à l'échelle réelle | à écrire, sur le modèle de l'adaptateur `vggt` (morceaux recouvrants, Umeyama) |
| `vggt` | reconstruction directe par VGGT | existante ; essais seulement (poids non commerciaux) |

Prompt Depth Anything a été entraîné avec la profondeur LiDAR d'iPhone en
indication, pas avec celle d'ARCore, qui vient du mouvement de la caméra :
son apport sur la profondeur d'un Galaxy sans capteur de profondeur est à
mesurer. Aucun de ces modèles n'a été évalué en sous-bois, à quelques
centimètres près : seule une vraie placette, comparée aux mesures à la main
(`benchmark/`), départage les méthodes.

## 5. Session manuelle (PR 3)

`server/scripts/session_gpu_scaleway.sh`, adapté de `deploy_scaleway.sh` :
création de l'instance L4, attente de SSH, `docker run` de l'image avec
l'adresse de l'API et le jeton worker, journal suivi, **suppression de
l'instance à la fin** (avec `--dry-run`, comme maestronemeton).

Objectif : **première exécution réelle** des méthodes du § 4 bis et de SAM 2
sur une vraie placette, et mesures pour `benchmark/` : durée par placette,
mémoire GPU, coût. Ces mesures fixent la suite.

## 6. Démarrage à la demande (PR 4)

Dans l'API, un orchestrateur :

* toutes les minutes : des tâches en attente et aucun worker vivant →
  création d'une instance L4 (API Scaleway), avec un cloud-init qui lance
  l'image ;
* le worker signale sa présence par son bail ; à l'arrêt pour inactivité,
  ou sans signe de vie depuis 15 minutes, l'orchestrateur **supprime**
  l'instance (on ne paie pas une instance supprimée ; une instance
  seulement éteinte garde ses volumes facturés) ;
* garde-fous : une seule instance à la fois, durée maximale par session
  (4 h par défaut), plafond mensuel en heures GPU, alerte (courriel ou
  ntfy) à la création, à la suppression et au dépassement ;
* la clé d'API Scaleway, à droits limités au projet, n'est connue que de
  l'API.

Démarrage d'une instance GPU : 5 à 10 minutes (constaté par
maestronemeton). Sans conséquence : les résultats provisoires sont déjà sur
le téléphone.

## 7. Licences (règle 6)

| Composant | Licence | Décision proposée |
|---|---|---|
| COLMAP | BSD-3 | sans réserve |
| SAM 2 | Apache-2.0 | sans réserve |
| PyTorch | BSD-3 | sans réserve |
| MapAnything, code | Apache-2.0 | sans réserve |
| MapAnything, poids `facebook/map-anything-apache` | Apache-2.0 | **le seul point de contrôle retenu** |
| MapAnything, poids `facebook/map-anything` | **CC-BY-NC 4.0** | écarté |
| Prompt Depth Anything, code et poids (`prompt-depth-anything-vitl`, étiquette Hugging Face) | Apache-2.0 | retenu ; la licence des poids n'est pas répétée dans le dépôt de code, à relire au moment de l'épinglage |
| Video Depth Anything `Small`, `Metric-Video-Depth-Anything-Small` | Apache-2.0 | repli retenu |
| Video Depth Anything `Base` / `Large` | **CC-BY-NC 4.0** | écartés |
| Depth Anything 3 `DA3METRIC-LARGE` | Apache-2.0 | repli possible ; `LARGE`, `GIANT`, `NESTED` sont CC-BY-NC, écartés |
| **VGGT, code** | « VGGT License » de Meta (2025-07-29) : usage commercial permis, **non OSI**, politique d'usage excluant notamment le militaire et les infrastructures critiques, redistribution sous la même licence | **Signalé.** Option d'essai seulement. |
| **VGGT, poids d'origine (VGGT-1B)** | **non commerciaux** | essais seulement |
| VGGT-1B-Commercial | usage commercial, après formulaire de demande | à demander seulement si VGGT bat MapAnything sur la placette de référence |
| SDK Python Scaleway | à vérifier avant la PR 4 | — |

Par défaut, le worker GPU tourne avec `profondeur_ia`, `colmap`,
`mapanything` et `profondeur` ; `vggt` ne s'ajoute que pour les essais,
par `GROMA_RECONSTRUCTEURS`. Les poids CC-BY-NC ne sont jamais
téléchargés : l'adaptateur refuse un point de contrôle hors de sa liste.
`docs/dependances.md` est complété en ce sens.

## 8. Coûts (estimations à confirmer en PR 3)

Prix relevés dans les scripts de maestronemeton, **non vérifiés** sur la
grille Scaleway actuelle : L4-1-24G environ 0,92 €/h, RTX 3070 environ
0,76 €/h, H100 environ 3,50 €/h. Par placette : inconnu tant que la chaîne
GPU n'a pas tourné. Petite instance de l'API et Object Storage : quelques
euros par mois.

## 9. À trancher par Pascal

1. Hébergement de l'API : petite instance Scaleway, ou machine existante.
2. VGGT : le garder pour une comparaison d'essai, ou l'écarter d'emblée au
   profit de MapAnything.
3. Plafond mensuel d'heures GPU et adresse des alertes.

## 10. Ordre des PR

1. `feat(server)` : worker par HTTP (§ 3), tests. **Fait (#56).**
2. `build(server)` : image GPU et publication GHCR (§ 4).
2 bis. `feat(server)` : adaptateurs `mapanything` et `profondeur_ia`
   (§ 4 bis), parties pures testées sans GPU.
3. `feat(server)` : session manuelle (§ 5), puis première exécution réelle
   et mesures dans `benchmark/`.
4. `feat(server)` : démarrage à la demande et garde-fous (§ 6).
5. `docs` : `serveur.md` (déploiement Scaleway), `dependances.md`,
   `PLAN.md`.
