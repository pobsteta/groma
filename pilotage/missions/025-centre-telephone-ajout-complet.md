# Mission 025 — centre au GNSS du téléphone, fiche complète pour un arbre ajouté

## Mission

**En une phrase (quoi, et pourquoi) :** permettre de mesurer le centre de la placette avec le GNSS
du téléphone (confirmation, moyenne de 30 s, qualité `telephone`, avertissement visible), et donner
à l'arbre ajouté à la main la même fiche qu'un arbre de la vidéo (essence visible, note, photos),
parce que Pascal veut prendre le centre sans récepteur RTK et ne trouve ni l'essence, ni la note, ni
la photo quand il ajoute un arbre (demandes du 2026-11-11).

**Redite par l'agent :** Faire mesurer le centre de la placette avec le GNSS du téléphone, sur confirmation, par une moyenne de 30 s (20 positions utiles au moins, précision annoncée d'au plus 10 m), enregistrée en qualité `telephone` avec un avertissement visible et sans jamais écraser un centre RTK sans confirmation ; et, pour un arbre ajouté à la main, ouvrir la même fiche complète que pour un arbre de la vidéo (essence visible, note, photos), afin que Pascal puisse faire une placette sans récepteur RTK et compléter un arbre ajouté.

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui | « Mesurer le centre » d'`EcranCarte.kt` (confirmation du flottant), `MesureCentre`, `FicheArbre` (mode `creation`), `EtatPhotos`, `ajouterArbre` |
| A-t-il les moyens ? | oui | tests de 024, S23 Ultra de Pascal |

**Temps de la main :** appuyer — essai de Pascal.

## Ma réponse d'abord

_Rempli par Pascal seul, avant de lire le travail de l'agent._

## L'Homme

- **Agent :** `dev` · **Modèle :** sonnet
- **Ce qui ne lui est pas demandé :** changer le géoréférencement du serveur ; modifier `nemeton`.

## Harnais

- **Peut :**
  1. **centre téléphone** : `MesureCentre` (API de 024) ; dans « Mesurer le centre » (carte), quand
     la position vient du téléphone, ou que le récepteur n'a pas de fix RTK, proposer « Mesurer avec
     le GNSS du téléphone » avec un avertissement (« précision de quelques mètres : toute la placette
     sera décalée d'autant ; à remplacer par une mesure RTK si possible ») et la précision annoncée ;
     décompte de 30 s, puis résultat (précision, écart au point prévu) ; confirmation pour remplacer un
     centre RTK ;
  2. **affichage** : la vue « Placette » et la carte signalent un centre `telephone` (mention et
     précision, comme la mention du centre prévu) ; le rapport du serveur n'est pas modifié ;
  3. **schéma** : valeur `telephone` du domaine `qualite_gnss`, version mineure suivante, exemples
     régénérés ; brief nemeton (`briefs/vers-nemeton/2026-11-11-centre-telephone.md`, dépôt seulement,
     copie déposée après la fusion) : nouvelle valeur, précision à prendre en compte ;
  4. **arbre ajouté, fiche complète** : vérifier pourquoi l'essence n'apparaît pas à la création
     (feuille à mi-hauteur, clavier ?) et la rendre visible (feuille ouverte en grand,
     `skipPartiallyExpanded`, défilement) ; à « Enregistrer » d'un nouvel arbre, l'arbre est créé
     (`A<n>`) puis **sa fiche complète s'ouvre aussitôt** (note, photos, « Prendre une photo »), ou
     bien la note est saisie dès la création et les photos prises sont renommées une fois le numéro
     connu : choisir la plus simple et sûre, et le dire ;
  5. **docs** : `docs/terrain.md` (étape 1 : centre au téléphone, avertissement ; étape 6 : arbre
     ajouté), `PLAN.md`.
- **Ne doit pas :** modifier les tests de 024 sans le dire ; toucher à `main` ; accepter la position
  du téléphone sans confirmation ; écraser un centre RTK sans confirmation.
- **Sources autorisées :** `android/`, `core/`, `docs/`, `briefs/vers-nemeton/`.

## Valeur provisoire (chiffre, réglage)

- Celles de 024 (30 s, 20 fixes, 10 m).

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 024. **Ordre de fusion :** une PR avec 024.
- **PR en brouillon jusqu'à :** l'essai de Pascal sur le S23 Ultra. **CI rouge attendue :** aucune.

## Terrain

- **Échantillon que Pascal vérifiera :** une placette dont le centre est pris au téléphone (mention
  visible), puis remplacé par une mesure RTK ; un arbre ajouté avec essence, note et photo.

## Qui veille, qui tranche

- **Relecteur :** `relecteur` (avec 024), puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

On peut faire une placette sans récepteur RTK, en sachant qu'elle est moins précise, et un arbre
ajouté se complète comme les autres.
