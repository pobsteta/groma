# Brief vers nemeton : `groma_envoyer_projet()`, publier le projet QField sur le serveur Groma

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemeton/2026-10-17-groma-envoyer-projet.md`).
* **Destinataire** : `nemeton`. À traiter avant le brief nemetonshiny
  `2026-10-17-envoyer-projet-telephone.md`, qui appelle cette fonction.
* **Type de changement** : `feat:`.

## 1. Contexte

Pascal veut que nemetonshiny envoie le projet de l'inventaire vidéo
**directement au téléphone**. La voie retenue passe par le serveur Groma :

1. nemetonshiny génère le projet avec `creer_projet_inventaire_video()` ;
2. il le publie sur le serveur Groma ;
3. l'application Groma propose « Projets du serveur… », le télécharge et
   l'installe.

nemeton a déjà un client de ce serveur : `groma_etat_placettes()` et
`groma_telecharger_resultats()`, dans `R/inventaire_video.R`. Il manque la
publication. Le plan complet côté Groma est dans
`briefs/groma/2026-10-17-envoi-projet-telephone.md` (§ 3 : routes du
serveur).

## 2. Fonction à créer

```r
groma_envoyer_projet(qgz, url, jeton = NULL, projet_nom = NULL)
```

* `qgz` : chemin du `.qgz` renvoyé par `creer_projet_inventaire_video()`.
  Le plugin `<même nom>.qml` est pris à côté s'il existe.
* Elle lit `projet_id`, et `projet_nom` quand l'argument n'est pas donné,
  dans la table `projet` du GeoPackage contenu dans le `.qgz`. Sans
  `projet_id` valide (`^[A-Za-z0-9_.-]{1,64}$`), elle s'arrête avec une
  erreur claire.
* Appels, avec un jeton porteur comme les fonctions existantes :
  1. `PUT {url}/projets/{projet_id}/qfield/fichiers/{nom}`, corps brut,
     pour le `.qgz`, puis pour le `.qml` ;
  2. `POST {url}/projets/{projet_id}/qfield/publier`, avec
     `{projet_nom, fichiers: [{nom, sha256, taille}], genere_le,
     nemeton_version}`. Les `sha256` sont calculés localement (`digest`
     ou `openssl`, selon ce que nemeton utilise déjà ; signaler toute
     nouvelle dépendance).
* Valeur renvoyée : une liste `projet_id`, `version`, `taille`, de façon
  invisible.
* Erreurs du serveur, avec des messages en français comme ailleurs :
  * 401 : jeton refusé ;
  * 413 : projet trop gros ;
  * 422 : fichier altéré pendant l'envoi ;
  * serveur injoignable.
* `httr2` reste dans `Suggests`, comme pour les autres fonctions `groma_*`.

## 3. Tests (testthat)

* Avec un faux serveur (`httr2::with_mocked_responses` ou équivalent) :
  * l'ordre des appels (PUT `.qgz`, PUT `.qml`, POST publier) ;
  * les empreintes envoyées ;
  * `projet_id` lu dans le `.qgz` ;
  * sans `.qml` : seul le `.qgz` est envoyé et publié.
* Sans `projet_id` dans le GeoPackage : erreur, aucun appel.
* Un 401 du serveur donne un message clair.
