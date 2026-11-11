# Brief vers nemeton : saisies de terrain envoyées par Groma au serveur, intégration sans la copie de QField

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemeton/2026-11-11-terrain-sans-qfield.md`).
* **Destinataire** : `nemeton` (`groma_telecharger_resultats()`,
  `importer_inventaire_video()`) ; `nemetonshiny` n'a rien à changer
  d'autre que d'afficher la date d'envoi (§ 3, point 4).
* **Type de changement** : `feat:`. À traiter après la fusion des missions
  020-022 de Groma ; Groma n'en dépend pas pour fonctionner, et la copie de
  QField reste acceptée.

## 1. Contexte

Décision de Pascal du 2026-11-11 : se passer de QField. Groma saisit déjà
le centre, les cibles et les attributs des arbres ; il envoie désormais au
serveur, **séparément du paquet vidéo**, ce qui a été saisi pour une
placette : l'archive « terrain ». Elle est renvoyée à chaque modification
(regroupée 30 s après la dernière) et remplace la précédente.

## 2. L'archive `terrain.zip`

* Téléchargement : `GET /projets/{projet_id}/placettes/{plot_id}/terrain.zip`
  (projet par défaut : `GET /placettes/{plot_id}/terrain.zip`), jeton porteur
  comme les autres routes ; 404 tant que rien n'a été envoyé.
* `GET /placettes[?projet_id=]` ajoute `terrain_envoye_le` (ISO 8601 UTC,
  ou `null`) à chaque entrée. Une placette qui n'a que du terrain (pas de
  paquet vidéo) est listée avec `id` nul et `etat = "sans_paquet"`.
* Contenu du zip :
  * `terrain.gpkg` : **même schéma** que `inventaire_video.gpkg` (même
    version), avec **une seule placette** : ses lignes de `placettes`,
    `centres_mesures`, `cibles`, `arbres`, `detections`, et la table `projet`
    entière ; `houppiers`, `parcelles` et `ugf` sont présentes mais vides.
    Valeurs identiques à la copie de Groma (aucun attribut perdu, règle 5) ;
  * `photos/<nom>` : les photos citées ; dans `terrain.gpkg`, les colonnes
    `arbres.photos` et `placettes.photos` (un ou plusieurs chemins séparés
    par `;`) y renvoient en chemins relatifs à l'archive. Une photo
    introuvable n'est pas emballée, mais son chemin (réécrit en
    `photos/<nom>`) reste.

## 3. À faire dans nemeton

1. `groma_telecharger_resultats()` : télécharger aussi `terrain.zip` de
   chaque placette qui en a un (`terrain_envoye_le` non nul), le ranger à
   côté de `resultat.gpkg`, le retélécharger quand `terrain_envoye_le`
   change.
2. `importer_inventaire_video()` : lire les `terrain.gpkg` du serveur
   (une placette chacun, même schéma que la copie de QField) **à la place de
   la copie de QField**, photos comprises (les copier dans le dossier de
   photos du projet et réécrire les chemins).
3. La copie de QField reste acceptée (projets en cours). **Si les deux
   existent pour une placette, celle du serveur gagne**, avec une alerte
   (les attributs saisis seulement dans QField, s'il y en a, sont signalés).
4. `nemetonshiny` : afficher `terrain_envoye_le` dans le bloc « Suivre »
   (« terrain reçu à … ») ; une placette `sans_paquet` est une placette dont
   seule la saisie est arrivée.

## 4. Vérifications

* Un `terrain.gpkg` s'ouvre dans GDAL/QGIS (GeoPackage valide, SCR 2154,
  index R*Tree à jour).
* Placette sans arbre : archive valide avec 0 ligne dans `arbres`.
* Les attributs du terrain (essence, statut, qualité, `dbh_cm` et
  `dbh_source`, notes, photos) arrivent tels que saisis.

## 5. Ce que Groma fait, et ce qu'il ne fait pas

Groma n'envoie ni le contenu des couches `houppiers`, `parcelles`, `ugf`,
ni les rasters. Les notes et les photos d'arbre saisies dans Groma relèvent
de la mission 022. Rien n'est supprimé côté serveur : le dernier envoi
complet remplace le précédent.
