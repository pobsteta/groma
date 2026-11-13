# Mission 034 — Bouton boussole sur la carte : nord en haut ou cap en haut

## Mission

**En une phrase :** ajouter sur la carte un bouton qui bascule entre « nord en haut » et « cap en haut »
(la carte tourne avec le téléphone, ce qui est devant soi est en haut de l'écran), parce que Pascal
navigue vers les placettes en forêt (demande du 2026-11-13).

**Temps de la main :** appuyer — réalisé par l'orchestrateur, essai de Pascal.

## Réalisé

- `groma.core.OrientationCarte` (pur) : rotation de la carte pour un cap (`-cap`, 0 en nord en haut ou
  sans cap) et seuil de 2° (chemin le plus court) pour ne pas faire trembler la carte ; retour au nord
  toujours appliqué.
- `EcranCarte` : bouton « N » au-dessus du zoom ; le « N » tourne pour montrer le nord de la carte ;
  en cap en haut, le bouton est coloré, la carte suit la position et tourne avec le cap affiché (cap
  GNSS en marche, sinon boussole, déjà utilisé par la flèche de guidage).

## Essais

- `OrientationCarteTest` (valeurs écrites à la main). Essai de Pascal sur le S23.
