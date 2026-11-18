# Bibliothèques `terrain` : ce que Groma peut adopter (mission 020)

Brief : `2026-10-17-bibliotheques-terrain.md` (v0.5.0 annoncée, v0.7.1 épinglée ici). `terrain-style` et
`terrain-carte` sont adoptés (missions 010-013). Les trois autres modules ne le sont pas encore :
le remplacement perdrait des fonctions de Groma ou n'a pas d'objet. `terrain` étant un autre dépôt, il n'est pas
modifié d'ici.

| Module | Identique à Groma (épinglé par `EquivalenceTerrainBibliothequesTest`) | Manque dans v0.7.1 / écart |
|---|---|---|
| `terrain-gnss` | GGA décodée, somme de contrôle, `NmeaDecoupeur`, NTRIP (requête, GGA, table des sources, statut) | `TrameRmc.instantUtcMs`, `FixGnss.heureUtcMs` et `recuMonotoneMs`, `LecteurNmea` (mission 019) ; origine `TELEPHONE/RECEPTEUR` (terrain : `INTERNE/EXTERNE`) ; `QualiteFix.AUCUN/RTK_FLOTTANT` (terrain : `INVALIDE/RTK_FLOAT`) ; noms d'API (`parseGga`, `parseSourcetable`, `statutReponse`) ; `Position` est un autre type (`fr.nemeton.terrain.geo`) |
| `terrain-gpkg` | encodage et décodage d'un point GeoPackage, octet pour octet | pas d'équivalent de `retirerIndexSpatial`, de la recopie des étendues, des colonnes ajoutées à la volée (missions 027-028), ni du protocole métier (détections, centres, arbres) qui reste de toute façon chez Groma |
| `terrain-journal` | règle « dernière écriture gagne » épinglée | aucun usage chez Groma (pas de fusion multi-appareils) |

Suite : un brief à `terrain` (ajouter le fix horodaté, l'origine et `LecteurNmea`, aligner les noms) ; ensuite le
remplacement de `Nmea.kt`, `Gnss.kt`, `Ntrip.kt` devient une suppression, les tests de parité en tenant lieu de filet.
Aucune rupture de contrat public : rien d'exposé n'a changé.
