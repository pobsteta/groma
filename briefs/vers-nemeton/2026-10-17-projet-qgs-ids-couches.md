# Brief nemeton — identifiants de couches uniques dans le `.qgs`

Date : 2026-10-17. Émetteur : Groma. Destinataire : nemeton
(`.build_qgs_xml()` et ses appelants : `.iv_empaqueter()` de
`creer_projet_inventaire_video()`, export QField du plan d'échantillonnage).

## Constat

Essai du 2026-10-17 sur un Galaxy S23 Ultra, QField 4.3.6, projet
`20261015_194200_yzoh` régénéré avec nemeton 2026.10.12 (branche de la PR
#566). La projection, l'échelle et les groupes sont bons. Mais QField marque
six couches **invalides** (icône rouge dans la légende) et ne les dessine
pas : `arbres`, `cibles`, `ugf`, `projet`, `Plan IGN`, `Ortho IGN`. Les autres
(`detections`, `centres_mesures`, `placettes`, `houppiers`, `parcelles`,
`Cadastre IGN`, `OpenStreetMap`) sont valides. QGIS 3.44 sur PC charge les
13 couches sans erreur. Le journal de QField ne dit rien.

## Cause (vérifiée par essais sur l'appareil)

Le déclencheur est **l'identifiant de couche** (`<id>` du `<maplayer>` et
`id` du `<layer-tree-layer>`), fixe et identique dans tous les projets
générés : `arbres_01`, `cibles_01`, `ugf_01`, `projet_01`, `Plan_IGN`,
`Ortho_IGN`…

| Essai (même GeoPackage) | Résultat dans QField |
|---|---|
| projet sans les 212 rasters | mêmes 4 couches vectorielles invalides |
| ordre des couches inversé (`<projectlayers>`, puis légende) | mêmes couches invalides |
| `arbres` seule, définition d'origine, `id` `arbres_01` | invalide |
| deux copies d'`arbres`, définition d'origine, `id` `arbres_premier` et `arbres_second` | **valides** |
| projet complet, **identifiants changés** (`arbres_01_v2`…), noms inchangés | **toutes valides**, Plan IGN affiché |
| projet complet, **noms changés** (`arbres v2`…), identifiants inchangés | mêmes couches invalides |

Le contenu des couches (rendu, étiquettes, formulaire, table du
GeoPackage) n'est pas en cause. Pourquoi QField refuse ces identifiants-là
et pas `detections_01` n'est pas établi : il garde vraisemblablement, d'un
projet importé à l'autre, un état associé à l'identifiant. Mais réutiliser
les mêmes identifiants dans des projets différents n'est pas l'usage de
QGIS, qui en tire un unique par couche (`arbres_9f2c1e…`).

## Demande

Générer des **identifiants uniques à chaque création de projet**, par
exemple le nom assaini suivi d'un UUID sans tirets :

```r
id <- paste0(gsub("[^A-Za-z0-9_]", "_", nom), "_", gsub("-", "", uuid::UUIDgenerate()))
# "arbres_6f1c0e9a4b2d4e7f9a3b5c8d1e2f4a6b"
```

* le même identifiant partout où la couche est référencée dans le `.qgs`
  (`<maplayer><id>`, `<layer-tree-layer id>`, `custom-order`, et toute
  relation, thème de carte ou jointure s'il y en a) ;
* un nouvel identifiant à chaque `creer_projet_inventaire_video()` /
  `renvoyer_inventaire_video()` : une nouvelle version du projet ne doit pas
  reprendre ceux de la précédente ;
* **les noms de couches ne changent pas** (`<layername>`) : le plugin QField
  de Groma cherche `projet`, `arbres`, `detections` et `centres_mesures` par
  leur nom.

Plancher si adopté : la version de nemeton qui l'implémente ; rien à changer
côté Groma (serveur, application, plugin).

## Test proposé

Deux projets générés successivement n'ont aucun identifiant de couche en
commun, et chaque identifiant du `.qgs` est unique et référencé de façon
cohérente :

```python
ids = [m.findtext("id") for m in root.iter("maplayer")]
assert len(ids) == len(set(ids))
assert {n.get("id") for n in root.iter("layer-tree-layer")} <= set(ids)
```

Puis dans QField : aucune couche marquée invalide, Plan IGN affiché.

## Projets d'essai

Conservés côté Groma (dossier de travail de la session) : `essai_ids_changes`
et `essai_noms_changes`, sur demande.
