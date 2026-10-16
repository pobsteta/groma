# Brief vers nemetonshiny : « Envoyer au téléphone » dans l'inventaire vidéo

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemetonshiny/2026-10-17-envoyer-projet-telephone.md`).
* **Destinataire** : `nemetonshiny`, sous-onglet Terrain → « Inventaire
  vidéo » (`R/mod_inventaire_video.R`).
* **Dépend de** : `nemeton::groma_envoyer_projet()` (brief nemeton
  `2026-10-17-groma-envoyer-projet.md`) et des routes `/projets/…/qfield`
  du serveur Groma, en cours côté Groma.
* **Type de changement** : `feat:`.

## 1. Besoin

Aujourd'hui, après « Générer le projet QField », il faut **télécharger** le
`.qgz`, puis le copier à la main sur le téléphone, par câble. Pascal veut
que le bouton **envoie directement** le projet au téléphone.

La voie retenue passe par le serveur Groma :
1. nemetonshiny publie le projet sur ce serveur ;
2. dans Groma, l'opérateur ouvre ⋮ → « Projets du serveur… » et le
   télécharge ;
3. Groma l'installe, et propose de l'ouvrir dans QField.

## 2. Interface

* À côté de « Télécharger » (`downloadButton(ns("qgz"))`), un bouton
  **« Envoyer au téléphone »**. Il est actif seulement quand un `.qgz` vient
  d'être généré (`rv$qgz`) et que l'adresse et le jeton du serveur Groma
  sont configurés. Ce sont les mêmes que pour « Actualiser » et
  « Récupérer les résultats » (`cfg$url`, `cfg$jeton`).
* L'envoi passe par `nemeton::groma_envoyer_projet(rv$qgz, cfg$url,
  cfg$jeton)`. Il tourne en **tâche de fond**, comme la génération
  (ExtendedTask ou mécanisme équivalent), pour ne pas bloquer
  l'interface. Afficher une progression simple : « Envoi en cours… ».
* Après un succès, un message : « Projet envoyé au serveur Groma (version
  <version>). Sur le téléphone : Groma → ⋮ → Projets du serveur… ».
* En cas d'échec, une notification d'erreur avec la raison donnée par
  nemeton : jeton refusé, projet trop gros, serveur injoignable.
* Sans serveur configuré, le bouton est désactivé, avec une info-bulle :
  « Renseigner l'adresse et le jeton du serveur Groma ».

## 3. i18n (`R/utils_i18n.R`, fr / en)

| Clé | fr | en |
|---|---|---|
| `video_envoyer_telephone` | Envoyer au téléphone | Send to phone |
| `video_envoi_en_cours` | Envoi en cours… | Sending… |
| `video_envoi_ok` | Projet envoyé au serveur Groma (version %s). Sur le téléphone : Groma → ⋮ → Projets du serveur… | Project sent to the Groma server (version %s). On the phone: Groma → ⋮ → Server projects… |
| `video_envoi_sans_serveur` | Renseigner l'adresse et le jeton du serveur Groma | Set the Groma server address and token |

## 4. Tests

* testServer :
  * le bouton est désactivé sans `.qgz`, et sans serveur configuré ;
  * un clic appelle `groma_envoyer_projet()` avec `rv$qgz` et la
    configuration (fonction simulée) ;
  * un succès affiche la version ;
  * une erreur est affichée sans faire planter le module.
* Un changement de projet efface l'état de l'envoi, comme pour `rv$qgz`.
