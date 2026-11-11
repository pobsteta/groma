# Brief vers nemeton : attributs des arbres saisis dans Groma, diamètre au ruban prioritaire (schéma 1.4.0)

* **Émetteur** : dépôt `groma_nemeton` (copie de référence :
  `briefs/vers-nemeton/2026-11-11-arbres-saisis-dans-groma.md`).
* **Destinataire** : `nemeton` (`creer_projet_inventaire_video()`, sa
  symbologie, `valider_inventaire_video()`, `rapprocher_arbres()`).
* **Type de changement** : `feat:`. À traiter après la fusion des missions
  017-019 de Groma ; Groma n'en dépend pas pour fonctionner.

## 1. Contexte

Décision de Pascal du 2026-11-11 : les attributs des arbres (essence,
diamètre au ruban, statut, qualité) se saisissent désormais **dans Groma**,
sur le plan de la placette, après la capture. Un bouton du plugin QField
les recopie ensuite dans la copie de QField, **sans jamais écraser une
valeur saisie dans QField**. QField garde les photos et les notes. Un
diamètre mesuré au ruban est prioritaire sur le diamètre estimé par la
vidéo, comme une hauteur mesurée (`h_source = mesuree`) l'est sur le MNH.

## 2. Changements du schéma (source : `core/src/groma/donnees/inventaire_video.schema.json`, version 1.4.0)

* Colonne `dbh_source` (TEXT) sur `arbres`, domaine `dbh_source` :
  `estime` (défaut), `mesure`. Origine `groma`.
  * `estime` : le diamètre `dbh_cm` est celui de la vidéo (il suit les
    détections) ;
  * `mesure` : le diamètre est celui du ruban, saisi dans Groma ou dans
    QField ; il n'est écrasé ni par une nouvelle détection ni par le
    rapprochement définitif.
* Colonnes existantes inchangées. `essence_arbre`, `dbh_cm`, `statut`,
  `qualite` gardent leur domaine ; ce qui change est **qui peut les
  écrire** : Groma, en plus de QField.
* Déclencheurs (`declencheurs.sql`) : `trg_detections_insert` crée
  l'arbre avec `dbh_source = 'estime'` ; `trg_detections_update` ne change
  pas `dbh_cm` quand `dbh_source = 'mesure'` (comme pour `h_m` et
  `h_source = 'mesuree'`). Un arbre `estime` suit la détection comme avant.

## 3. À faire dans nemeton

1. Créer la colonne `dbh_source` de `arbres` (domaine `estime` / `mesure`,
   défaut `estime`), **lecture seule dans le formulaire QField** (comme
   `h_source`) ; recopier le schéma 1.4.0 et le fichier `declencheurs.sql`
   dans `inst/inventaire_video/`, et l'exemple de référence
   `exemples/inventaire_video/` (régénéré dans ce dépôt).
2. `valider_inventaire_video()` : contrôler le domaine de `dbh_source`
   (`estime`, `mesure`, ou absent dans un projet plus ancien).
3. `rapprocher_arbres()` (rapprochement définitif) : pour un arbre apparié
   dont `dbh_source = "mesure"`, **garder `dbh_cm` et `dbh_source`** de
   l'arbre provisoire (la position vient du serveur, comme avant) ;
   l'alerte `ecart_dbh` reste émise, comparée au diamètre au ruban (écart de
   plus de 10 cm **et** de 50 % du plus petit). `dbh_source` fait partie des
   attributs de terrain jamais perdus, et un arbre `mesure` qui sort de
   l'inventaire (hors rayon, sous le seuil) est conservé en `a_verifier`
   comme tout arbre portant une saisie. Référence : `groma.rapprochement`
   (`core/src/groma/rapprochement.py`) et `core/tests/test_rapprochement_saisies.py`.
4. Lecture par nemeton (import dans l'inventaire) : `dbh_cm` est le
   diamètre à retenir, quelle que soit `dbh_source` ; `dbh_source` sert à
   la traçabilité (diamètre mesuré ou estimé) et peut alimenter un contrôle
   qualité (part des arbres mesurés au ruban).
5. Prévoir que `essence_arbre`, `statut` et `qualite` puissent désormais
   venir de Groma : aucune différence de format ni de domaine.

## 4. Projets déjà générés

Groma ajoute la colonne `dbh_source` (défaut `estime`) à la couche `arbres`
de **sa** copie, et recrée les deux déclencheurs de `detections` à
l'identique de `declencheurs.sql`, à la première saisie d'un arbre. La copie
de QField reste sans `dbh_source` : le plugin ne recopie alors que
l'essence, le statut et la qualité (le diamètre au ruban a besoin de la
colonne `dbh_source` de QField, donc d'un projet régénéré avec le schéma
1.4.0). Groma ne relit jamais la copie de QField.

## 5. Ce que Groma garantit (testé dans `groma_nemeton`)

* un diamètre au ruban survit à une nouvelle capture et au calcul définitif ;
* les attributs saisis dans Groma sont conservés par le rapprochement
  (règle 5) ;
* le plugin QField ne remplace jamais une valeur saisie dans QField.
