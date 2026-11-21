# Mission 038 — Les dalles MNT et cvat.tif arrivent sur le téléphone

## Mission

**En une phrase (quoi, et pourquoi) :** vérifier par des tests, et corriger seulement si un test
échoue, que les dalles `rasters/mnt_{x}_{y}.tif` et `rasters/cvat.tif` d'un projet 1.7.0 arrivent
dans le dossier du projet sur le téléphone, par « Projets du serveur… » (`.qgz`), par l'import d'un
dossier et par la mise à jour d'un projet, parce que 039 et 040 les lisent là.

**Redite par l'agent :** _à compléter par l'agent avant d'agir._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui : `ExtractionQgz` (`groma-core/…/ProjetServeur.kt`), `DepotProjets.copier`, `TelechargementProjet` ; serveur `projets_qfield.py` | |
| A-t-il les moyens ? | oui après 037 (exemple 1.7.0 avec dalles) | confier après 037 |

**Temps de la main :** donner. **Degré de la main :** le chef tranche.

## Ma réponse d'abord

_Rempli par Pascal seul._

## L'Homme

- **Agent :** `dev` (écrit les tests puis, si besoin, la correction). **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** lire ou afficher les dalles (039, 040).

## Harnais

- **Peut (périmètre) :** `android/groma-core/src/test/`, `android/groma-core/src/main/kotlin/groma/core/ProjetServeur.kt`,
  `android/app/src/main/kotlin/groma/app/DepotProjets.kt`, `android/app/src/main/kotlin/groma/app/TelechargementProjet.kt`,
  `server/tests/`, `server/src/groma_server/projets_qfield.py`, `docs/terrain.md`, `PLAN.md`, journal (ajout seul).
- **Câblé :** pas de `main` ; pas de « terminé » sans tests verts ; journal en ajout seul.
- **Ne doit pas :** accepter des sous-dossiers de `rasters/` (le brief les exclut) ; affaiblir la
  protection contre le zip-slip.

## Valeur provisoire (chiffre, réglage)

- Aucune.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** 037 fusionnée ; part de `origin/main` après 037.
- **CI rouge attendue :** aucune.

## Tests

- `.qgz` d'essai contenant `rasters/mnt_0849_6689.tif`, `rasters/cvat.tif` et un raster par
  placette → les trois extraits ; `rasters/sous/x.tif` ignoré. Import d'un dossier et mise à jour :
  mêmes fichiers présents après. Serveur : un `.qgz` de 50 Mo de dalles est accepté (limite `GROMA_TAILLE_MAX_PROJET`, 500 Mo par défaut : la noter dans `docs/terrain.md` avec la taille réelle du premier projet 1.7.0).

## Terrain

- Le premier projet 1.7.0 livré par nemeton, téléchargé sur le S23 : les dalles sont dans `rasters/`.

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Sur le S23, `Android/data/fr.nemeton.groma/files/projets/<projet>/rasters/` contient les dalles.
