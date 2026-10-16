# Brief Groma : envoyer le projet de nemetonshiny au téléphone par le serveur

* **Émetteur** : Pascal, via `groma_nemeton` (copie de référence :
  `briefs/groma/2026-10-17-envoi-projet-telephone.md`).
* **Destinataire** : ce dépôt (serveur, application Android). Deux briefs
  séparés vont aux dépôts frères :
  * `briefs/vers-nemeton/2026-10-17-groma-envoyer-projet.md` (fonction
    cliente) ;
  * `briefs/vers-nemetonshiny/2026-10-17-envoyer-projet-telephone.md`
    (bouton).
* **Type** : `feat:` (plusieurs PR, voir § 7).

## 1. Besoin

Aujourd'hui, un projet passe de nemetonshiny au téléphone à la main.
nemetonshiny produit le projet QField complet : il appelle
`creer_projet_inventaire_video()`, qui écrit un `.qgz` et, à côté, le plugin
`.qml`. Ensuite, quelqu'un copie le dossier par câble (`adb push`, ou
explorateur de fichiers) dans `Download/groma/`, et importe le projet dans
QField à la main.

Pascal veut que le bouton de nemetonshiny **envoie directement** le projet
au téléphone. La voie retenue passe par le **serveur Groma**, qui reçoit
déjà les captures, rangées par projet :

```
nemetonshiny ── « Envoyer au téléphone » ──► serveur Groma ──► Groma (téléphone)
  creer_projet_inventaire_video()            /projets/<id>/qfield   « Projets du serveur »
  groma_envoyer_projet() (nemeton)                                   → Download/groma/<id>/
                                                                     → « Ouvrir dans QField »
```

Un QR code sur le réseau local (voie 2) et QFieldCloud (voie 3) sont écartés
pour l'instant. Le QR code pourra servir de dépannage tant que le serveur
n'est pas déployé.

## 2. Ce que produit nemeton (constaté, nemeton 2026.10.9)

* `<project_name>.qgz` est une archive zip. Elle contient le `.qgs`,
  `inventaire_video.gpkg` et `rasters/<plot_id>_mnt.tif`, `_mnh.tif`.
* `<project_name>.qml` est le plugin, **à côté** du `.qgz`, jamais dedans.
* Le GeoPackage suit le schéma de Groma, version 1.2.0. Sa table `projet`
  donne `projet_id`, qui sert de clé partout.

Groma attend un **dossier** qui contient `inventaire_video.gpkg` et
`rasters/`. Il devra donc extraire ces deux éléments du `.qgz`.

## 3. Serveur (`server/src/groma_server/api.py`)

Même authentification par jeton porteur (`GROMA_JETON`). Un projet est
identifié par `projet_id` (motif de `CleProjet` : `^[A-Za-z0-9_.-]{1,64}$`,
sans `.` ni `..`). Les noms de fichiers suivent le motif
`^[A-Za-z0-9_-]{1,64}\.(qgz|qml)$`.

| Méthode | Chemin | Rôle |
|---|---|---|
| PUT | `/projets/{projet_id}/qfield/fichiers/{nom}` | dépôt brut d'un fichier (`.qgz` ou `.qml`) dans une zone d'attente ; réponse : `sha256` et `taille` |
| POST | `/projets/{projet_id}/qfield/publier` | corps `{projet_nom, fichiers: [{nom, sha256, taille}], genere_le, nemeton_version}` ; vérifie les empreintes, crée la version (horodatage UTC `AAAAMMJJTHHMMSSZ`), qui devient la courante ; réponse : `{version}` |
| GET | `/projets` | une entrée par projet publié : `projet_id`, `projet_nom`, `version`, `publie_le`, `fichiers` (nom, taille, sha256), `taille_totale` |
| GET | `/projets/{projet_id}/qfield` | manifeste de la version courante |
| GET | `/projets/{projet_id}/qfield/{version}/{nom}` | téléchargement, **avec en-tête `Range`** pour reprendre un téléchargement interrompu |

Stockage :
* chaque version dans `projets_qfield/<projet_id>/<version>/`, avec
  `manifeste.json` ;
* les **3** dernières versions sont gardées ;
* la publication est atomique (renommage d'un dossier temporaire) ;
* exactement un `.qgz` par version. Le `.qml` est facultatif : il manque
  sur un projet ancien.

Tests pytest :
* dépôt, publication, liste et téléchargement complet ;
* téléchargement par morceaux avec `Range` ;
* empreinte fausse : 422, rien n'est publié ;
* `projet_id` ou nom de fichier invalide : 400 ;
* sans jeton : 401 ;
* une 4ᵉ publication supprime la plus ancienne ;
* deux projets restent séparés.

## 4. Application Android

Le serveur et le jeton sont déjà dans Paramètres.

* **Écran Projets**, ⋮ → **Projets du serveur…** : liste des projets publiés
  (nom, date de publication, taille). Pour chacun, l'état sur le téléphone :
  « absent », « à jour » ou « version plus récente sur le serveur ».
* **Télécharger** :
  * tâche `WorkManager` en Wi-Fi, ou en 4G si l'envoi en 4G est autorisé ;
  * reprise par `Range` après une coupure ;
  * contrôle du `sha256` à la fin ;
  * progression dans la notification.
* **Installation**, une fois le téléchargement vérifié :
  1. extraire `inventaire_video.gpkg` et `rasters/` du `.qgz` vers le
     dossier des projets (`Download/groma/<projet_id>/`, par l'accès déjà
     accordé), comme un dépôt par câble ;
  2. garder `<project_name>.qgz` et `<project_name>.qml` dans
     `Download/groma/<projet_id>/qfield/` ;
  3. laisser le parcours existant prendre la suite (« Actualiser »,
     copie de travail). La règle de mise à jour ne change pas : elle est
     **refusée** dès que la copie de travail contient des données de
     terrain. Rien de ce qui a été saisi n'est jamais écrasé.
* **Ouvrir dans QField** (menu de la carte du projet) :
  * Groma prépare un `.zip` qui contient `<project_name>.qgz` et
    `<project_name>.qml` ;
  * il l'ouvre par `ACTION_VIEW` (FileProvider, type `application/zip`,
    paquet `ch.opengis.qfield`) ;
  * QField l'importe et le décompresse dans « Imported Projects », plugin
    compris.
  * **À valider sur appareil** : il n'est pas vérifié que QField accepte
    cette intention. Repli : afficher le chemin et laisser l'opérateur faire
    « Importer un projet depuis un dossier ».
* Au premier lancement, ou quand la liste est vide : si un serveur est
  configuré, proposer « Projets du serveur… ».

Tests :
* `groma-core` : lecture du manifeste et comparaison des versions ;
  extraction du `.qgz` (seuls `inventaire_video.gpkg` et `rasters/*.tif`
  sont extraits, chemins assainis contre le zip-slip) ;
* application : sur appareil.

## 5. Sécurité

* Un seul jeton pour lire et publier, pour l'instant (`GROMA_JETON`). Une
  évolution possible : un jeton de publication réservé à nemetonshiny.
* Contrôles du `.qgz` côté serveur : taille plafonnée (500 Mo, réglable),
  archive zip lisible, présence d'un `.qgs` et d'`inventaire_video.gpkg`.
* L'extraction côté téléphone n'écrit qu'à l'intérieur du dossier du projet.

## 6. Dépendances (règle 6)

Aucune. Le serveur s'appuie sur FastAPI et Starlette, déjà présents. Le
téléphone utilise `java.util.zip` et WorkManager, déjà présents.

## 7. Découpage

1. `feat(server)` : routes du § 3, stockage, tests, `docs/serveur.md`.
2. `feat(core)` : manifeste, versions, extraction sûre du `.qgz` (Kotlin),
   tests.
3. `feat(android)` : « Projets du serveur… », téléchargement reprenable,
   installation dans le dossier des projets.
4. `feat(android)` : « Ouvrir dans QField » (à valider sur appareil).
5. `docs` : `terrain.md` (déposer un projet sans câble), `PLAN.md`.

Les PR 1 et 2 ne dépendent pas des dépôts frères. Le bouton de nemetonshiny
pourra être essayé dès la PR 1 fusionnée et le serveur lancé localement.
