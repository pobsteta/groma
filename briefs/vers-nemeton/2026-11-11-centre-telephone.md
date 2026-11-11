# Brief vers nemeton : centre de placette mesuré avec le GNSS du téléphone (schéma 1.6.0)

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemeton/2026-11-11-centre-telephone.md`).
* **Destinataire** : `nemeton` (`creer_projet_inventaire_video()`, symbologie
  QField, `valider_inventaire_video()`, rapprochement et rapport).
* **Type de changement** : `feat:`. À traiter après la fusion des missions
  024-025 de Groma ; Groma n'en dépend pas pour fonctionner.

## 1. Contexte

Décision de Pascal du 2026-11-11 : on peut faire une placette sans récepteur
RTK. Groma mesure alors le centre avec le GNSS du téléphone (confirmation
explicite, moyenne de 30 s sur 20 positions au moins, précision annoncée
d'au plus 10 m, avertissement visible). Le centre est **moins précis, de
quelques mètres** : toute la placette (cibles, arbres) est décalée d'autant.

## 2. Changement du schéma (source : `core/src/groma/donnees/inventaire_video.schema.json`, version 1.6.0)

* Domaine `qualite_gnss` : **nouvelle valeur `telephone`** (en plus de `fix`
  et `float`). Colonne `centres_mesures.qualite_gnss` inchangée.
* `precision_h_m` d'un centre `telephone` : la plus grande de la dispersion
  des positions (RMS) et de la précision annoncée (RMS), en mètres. Chaque
  position retenue annonce au plus 10 m, mais la dispersion RMS peut faire
  dépasser 10 m à `precision_h_m`. Elle **doit être prise en compte** : un centre `telephone` n'a pas la
  qualité d'un centre RTK, même si le téléphone se dit « RTK fixe ».
* Un centre RTK n'est remplacé par un centre `telephone` qu'après
  confirmation dans Groma ; une mesure RTK faite ensuite le remplace (le plus
  récent, `fid` le plus grand, compte). Les lignes plus anciennes restent.

## 3. À faire dans nemeton

1. Recopier le schéma 1.6.0 dans `inst/inventaire_video/` et l'exemple de
   référence `exemples/inventaire_video/` (régénéré dans ce dépôt).
2. `valider_inventaire_video()` : accepter `telephone` dans le domaine
   `qualite_gnss`.
3. Symbologie QField et formulaire : afficher la valeur `telephone` (libellé
   « téléphone ») ; mettre en évidence un centre `telephone` (couleur
   d'alerte) : sa précision est de l'ordre de plusieurs mètres.
4. Rapprochement / calculs aval : tenir compte de `precision_h_m` d'un centre
   `telephone` (par exemple tolérance d'appariement des arbres élargie, ou
   mention dans le rapport) ; ne pas le traiter comme un centre RTK.

## 4. Ce qui ne change pas

Aucune autre colonne ni autre domaine. Le rapport du serveur Groma n'est pas
modifié. Les projets déjà générés n'ont rien à migrer : `telephone` est une
simple valeur de domaine.
