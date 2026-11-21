# Mission 041b — Vue Placette : pas de « Relever C0 » en mode arbre, en-tête repliable

## Mission

**En une phrase (quoi, et pourquoi) :** dans la vue Placette, masquer « Relever C0 » en mode
« Ajouter un arbre » et ranger dans un ruban repliable tout le texte au-dessus du plan, parce qu'en
mode arbre on ne relève que des arbres (pas C0 ni les cibles) et que ce texte prend la place du plan
sur le téléphone (demandes de Pascal du 2026-11-21, après essai de la v2026.11.11).

**Redite par l'agent :** _à compléter par l'agent avant d'agir._

## Les trois questions (règle de staffing)

| Question | Réponse | Conséquence |
|---|---|---|
| Peut-il redire la mission en une phrase, avec son but ? | oui | |
| Sait-il comment faire ? | oui : `ui/EcranPlacette.kt` (rangée de la mission 041, lignes de l'état du centre de la mission 029) | |
| A-t-il les moyens ? | oui : compilation, tests `:app:testFumeeUnitTest`, essai de Pascal | confier |

**Temps de la main :** donner. **Degré de la main :** le chef tranche.

## Ma réponse d'abord

_Rempli par Pascal seul._

## L'Homme

- **Agent :** `dev`. **Modèle :** sonnet.
- **Ce qui ne lui est pas demandé :** couches sur le plan (042) ; vue Carte.

## Ce qui est attendu

1. Mode « Ajouter un arbre » : ni « Relever C0 » ni puces C1 à C3 ; le texte d'aide de l'ajout seul.
   Hors de ce mode : rangée inchangée (« Relever C0 » puis C1 à C3).
2. **Ruban repliable** au-dessus du plan : il contient tout le texte qui précède le plan aujourd'hui
   (état du centre C0, avertissements centre prévu / GNSS du téléphone, réception). Replié, il garde
   une ligne courte toujours visible : l'état de C0 (mesuré ou non, qualité), en rouge si le centre
   n'est pas mesuré ou vient du téléphone, pour que l'avertissement ne disparaisse jamais tout à fait.
   Une flèche ou un appui sur la ligne déplie et replie.
3. Replié par défaut ; l'état replié/déplié est retenu pendant la session (pas besoin de le garder
   entre deux lancements).

## Harnais

- **Peut (périmètre) :** `android/app/src/main/kotlin/groma/app/ui/EcranPlacette.kt`, un nouveau
  fichier `ui/RubanReplie.kt` si utile, `res/values/strings.xml`, tests `android/app/src/test/`,
  `docs/terrain.md`, `PLAN.md`, journal (ajout seul).
- **Câblé :** pas de `main` ; pas de « terminé » sans tests verts ; journal en ajout seul.
- **Ne doit pas :** toucher à `PlanPlacette.kt` (042) ; changer le parcours « Relever C0 » (mission 030) ;
  cacher complètement un avertissement sur le centre.

## Valeur provisoire (chiffre, réglage)

- Replié par défaut : choix provisoire, jugé par Pascal à l'essai.

## Dépend de / rouge attendu jusqu'à

- **Dépend de :** rien ; part de `origin/main`. **Avant :** 042 (même fichier `EcranPlacette.kt`).
- **CI rouge attendue :** aucune.

## Tests

- Test de la ligne courte (fonction pure : centre prévu → rouge ; téléphone → rouge ; RTK → normal).
- Compilation ; essai sur le S23 (version d'essai `fumee` ou release).

## Terrain

- Sur le S23 en portrait : plan visible sans défiler, ruban replié ; mode « Ajouter un arbre » sans
  « Relever C0 ».

## Qui veille, qui tranche

- **Relecteur :** `relecteur`, puis Pascal. **Arrêt :** 3 allers-retours. **Qui tranche :** Pascal.

## Comment saura-t-on que ça marche ?

Pascal ouvre une placette et voit le plan tout de suite, avec l'état de C0 sur une ligne.
