package groma.core

/**
 * Types and objects shared with the `terrain` library (v2026.11.1, missions 020 and 035, brief
 * 2026-10-17-bibliotheques-terrain): Groma's own copies were identical and are gone.
 * Aliases keep the `groma.core` names, so no caller changes; the PROJ tests
 * (ProjectionsTest, GeodesieTest) stay as a safety net.
 */
typealias Projections = fr.nemeton.terrain.geo.Projections
typealias Geodesie = fr.nemeton.terrain.geo.Geodesie

/** SQLite access of the GeoPackage protocol: same three members as Groma's former interface. */
typealias BaseSqlite = fr.nemeton.terrain.gpkg.BaseSqlite

/** WGS 84 position, kept under the `groma.core` name. */
typealias Position = fr.nemeton.terrain.geo.Position
