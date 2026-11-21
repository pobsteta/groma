package groma.core

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Pinhole camera of the package (mission 045). Conventions of `paquet.py`: pose = ARCore world_from_camera
 * (row major), world Y up, camera looking along -Z with +Y up; local frame x = X, y = -Z, z = Y.
 * Pixel (u right, v down) = (cx + fx * Xc / d, cy - fy * Yc / d) with d = -Zc, the distance along the axis.
 */
object CameraPaquet {
    /** A point nearer than this to the camera plane is not drawn. */
    const val PROFONDEUR_MIN_M = 0.3

    data class Pixel(val u: Double, val v: Double, val profondeurM: Double)

    /** Pixel of the local point ([x], [y], [z]), null when it lies behind (or too near) the camera. */
    fun projeter(pose: DoubleArray, k: IntrinsequesImage, x: Double, y: Double, z: Double): Pixel? {
        val dx = x - pose[3]; val dy = z - pose[7]; val dz = -y - pose[11] // local -> world, minus the camera centre
        val xc = pose[0] * dx + pose[4] * dy + pose[8] * dz
        val yc = pose[1] * dx + pose[5] * dy + pose[9] * dz
        val d = -(pose[2] * dx + pose[6] * dy + pose[10] * dz)
        if (d < PROFONDEUR_MIN_M) return null
        return Pixel(k.cx + k.fx * xc / d, k.cy - k.fy * yc / d, d)
    }

    /** Camera centre in the local frame. */
    fun centreLocal(pose: DoubleArray): DoubleArray = doubleArrayOf(pose[3], -pose[11], pose[7])

    /** Ray through pixel ([u], [v]) (same as the capture: [Rayon] in the local frame, unit direction). */
    fun rayonLocal(pose: DoubleArray, k: IntrinsequesImage, u: Double, v: Double): Rayon {
        val dc = doubleArrayOf((u - k.cx) / k.fx, -(v - k.cy) / k.fy, -1.0)
        val dw = DoubleArray(3) { r -> pose[4 * r] * dc[0] + pose[4 * r + 1] * dc[1] + pose[4 * r + 2] * dc[2] }
        val n = sqrt(dw[0] * dw[0] + dw[1] * dw[1] + dw[2] * dw[2])
        return Rayon(centreLocal(pose), doubleArrayOf(dw[0] / n, -dw[2] / n, dw[1] / n))
    }

    /** Local point seen at pixel ([u], [v]) of a depth map at distance [profondeurM] along the axis. */
    fun pointLocal(pose: DoubleArray, k: IntrinsequesImage, u: Double, v: Double, profondeurM: Double): DoubleArray {
        val xc = (u - k.cx) / k.fx * profondeurM; val yc = -(v - k.cy) / k.fy * profondeurM; val zc = -profondeurM
        val wx = pose[0] * xc + pose[1] * yc + pose[2] * zc + pose[3]
        val wy = pose[4] * xc + pose[5] * yc + pose[6] * zc + pose[7]
        val wz = pose[8] * xc + pose[9] * yc + pose[10] * zc + pose[11]
        return doubleArrayOf(wx, -wz, wy)
    }
}

/** From the package alone: the Lambert-93 <-> capture-frame transform, or why it cannot be had. */
class PlacementImages(val georef: Georef?, val message: String?, val residuDeg: Double = Double.NaN)

object PlacementPaquet {
    const val MESSAGE_C0 = "C0 non vue : arbres non placés sur les images"
    private const val VUES_MIN = 3
    const val RESIDU_ALERTE_DEG = 3.0

    /**
     * Same chain as `CalculEmbarque`: each target seen on [VUES_MIN] images or more is triangulated, C0 is the
     * fixed point, C1 to C3 and the measured targets of the manifest give the rotation.
     */
    fun calculer(m: ManifestePaquet): PlacementImages {
        val parId = m.images.associateBy { it.id }
        val rayons = HashMap<String, MutableList<Rayon>>()
        for (d in m.detectionsCibles) {
            val im = parId[d.image] ?: continue
            val k = im.k ?: continue
            if (im.pose.size != 16) continue
            rayons.getOrPut(d.idCible) { ArrayList() } += CameraPaquet.rayonLocal(im.pose, k, d.u, d.v)
        }
        val cibles = rayons.filterValues { it.size >= VUES_MIN }.mapValues { Georeferencement.trianguler(it.value) }
        val c0 = cibles["C0"] ?: return PlacementImages(null, MESSAGE_C0)
        val autres = cibles.filterKeys { it != "C0" }.mapValues { Pair(it.value[0], it.value[1]) }
        if (autres.isEmpty()) return PlacementImages(null, "Aucune cible C1 à C3 vue : arbres non placés sur les images")
        if (autres.keys.none { id -> m.ciblesMesurees.any { it.idCible == id } }) {
            return PlacementImages(null, "Cibles vues (${autres.keys.sorted().joinToString(", ")}) sans azimut ni distance saisis : arbres non placés sur les images")
        }
        val geo = Georeferencement.georeferencer(
            Pair(c0[0], c0[1]), autres, m.ciblesMesurees, Pair(m.centreX, m.centreY), m.uniteAzimut, m.declinaisonDeg, m.convergenceDeg,
        )
        val rmse = Georeferencement.rmse(geo.residusAzimutDeg.values)
        return PlacementImages(geo, if (rmse > RESIDU_ALERTE_DEG) "Orientation douteuse (%.1f°) : vérifier les azimuts des cibles".format(rmse) else null, rmse)
    }
}

/** A tree in the local frame of the capture, [zSol] the local ground height at its foot (null: not drawable). */
class ArbreLocal(val arbre: ArbrePlacette, val x: Double, val y: Double, val zSol: Double?)

/**
 * Local ground height at the foot of each tree, from the depth maps of the package (mission 045, choice made:
 * the depth points seen within [RAYON_M] of the trunk, low percentile of their height; no DTM). A tree with too
 * few points takes the median ground of the others (flat-ground assumption); no tree with points: nothing.
 */
object SolLocal {
    const val RAYON_M = 0.75
    const val PERCENTILE = 0.05
    const val POINTS_MIN = 30
    const val PROFONDEUR_MAX_M = 8.0
    const val IMAGES_MAX = 150

    /**
     * [arbres]: uuid to local (x, y). [lireProfondeur] returns the bytes of the depth map of an image (uint16
     * little endian, millimetres), null when missing; called for at most [IMAGES_MAX] images, one at a time.
     */
    fun estimer(arbres: Map<String, Pair<Double, Double>>, images: List<ImagePaquet>, lireProfondeur: (ImagePaquet) -> ByteArray?): Map<String, Double> {
        if (arbres.isEmpty()) return emptyMap()
        val avec = images.filter { it.suivi && it.profondeur != null && it.kProfondeur != null && it.pose.size == 16 }
        val pas = ((avec.size + IMAGES_MAX - 1) / IMAGES_MAX).coerceAtLeast(1)
        val cles = arbres.keys.toList()
        val ax = DoubleArray(cles.size) { arbres.getValue(cles[it]).first }
        val ay = DoubleArray(cles.size) { arbres.getValue(cles[it]).second }
        val r2 = RAYON_M * RAYON_M
        val z = Array(cles.size) { DoubleList() }
        for (i in avec.indices step pas) {
            val im = avec[i]
            val k = im.kProfondeur!!
            val b = lireProfondeur(im) ?: continue
            if (b.size < k.largeur * k.hauteur * 2) continue
            val cam = CameraPaquet.centreLocal(im.pose)
            // Trees too far from the camera cannot have depth points near them.
            val proches = cles.indices.filter { j -> (ax[j] - cam[0]).let { dx -> (ay[j] - cam[1]).let { dy -> dx * dx + dy * dy } } < (PROFONDEUR_MAX_M + RAYON_M) * (PROFONDEUR_MAX_M + RAYON_M) }
            if (proches.isEmpty()) continue
            for (v in 0 until k.hauteur) for (u in 0 until k.largeur) {
                val o = (v * k.largeur + u) * 2
                val mm = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
                if (mm == 0) continue
                val d = mm / 1000.0
                if (d > PROFONDEUR_MAX_M) continue
                val p = CameraPaquet.pointLocal(im.pose, k, u + 0.5, v + 0.5, d)
                for (j in proches) {
                    val dx = p[0] - ax[j]; val dy = p[1] - ay[j]
                    if (dx * dx + dy * dy <= r2) z[j].ajouter(p[2])
                }
            }
        }
        val trouves = HashMap<String, Double>()
        for (j in cles.indices) if (z[j].n >= POINTS_MIN) trouves[cles[j]] = z[j].percentile(PERCENTILE)
        if (trouves.isEmpty()) return emptyMap()
        val mediane = trouves.values.sorted().let { it[it.size / 2] }
        return cles.associateWith { trouves[it] ?: mediane }
    }

    private class DoubleList {
        var a = DoubleArray(256); var n = 0
        fun ajouter(x: Double) { if (n == a.size) a = a.copyOf(n * 2); a[n++] = x }
        fun percentile(p: Double): Double { val s = a.copyOf(n).also { it.sort() }; return s[floor(p * (n - 1)).toInt()] }
    }
}

/** A tree drawn on an image: foot and head of the line (pixels), label "number · diameter". */
class TraitArbre(val arbre: ArbrePlacette, val etiquette: String, val piedU: Double, val piedV: Double, val teteU: Double, val teteV: Double)

class MarqueCible(val idCible: String, val u: Double, val v: Double)

class Surimpression(val arbres: List<TraitArbre>, val cibles: List<MarqueCible>)

object SurimpressionImage {
    /** Height of the line drawn at each trunk (m): breast height. */
    const val HAUTEUR_TRAIT_M = 1.30

    fun etiquette(a: ArbrePlacette): String = a.dbhCm?.let { "${a.numero} · ${Math.round(it)} cm" } ?: a.numero

    /** Local positions of [arbres] (Lambert-93) with their ground heights [sols] (by uuid). */
    fun enLocal(arbres: List<ArbrePlacette>, georef: Georef, sols: Map<String, Double>): List<ArbreLocal> =
        arbres.map { a -> georef.versLocal(a.x, a.y).let { (x, y) -> ArbreLocal(a, x, y, sols[a.uuid]) } }

    /**
     * Trees (behind the camera or outside the image: left out) and targets of one image.
     * [arbres] empty when the placement failed; the targets are still marked.
     */
    fun calculer(image: ImagePaquet, m: ManifestePaquet, arbres: List<ArbreLocal>): Surimpression {
        val k = image.k ?: return Surimpression(emptyList(), emptyList())
        val traits = arbres.mapNotNull { t ->
            val sol = t.zSol ?: return@mapNotNull null
            val pied = CameraPaquet.projeter(image.pose, k, t.x, t.y, sol) ?: return@mapNotNull null
            val tete = CameraPaquet.projeter(image.pose, k, t.x, t.y, sol + HAUTEUR_TRAIT_M) ?: return@mapNotNull null
            if (!dans(pied, k) && !dans(tete, k)) return@mapNotNull null
            TraitArbre(t.arbre, etiquette(t.arbre), pied.u, pied.v, tete.u, tete.v)
        }
        val cibles = m.detectionsCibles.filter { it.image == image.id }.map { MarqueCible(it.idCible, it.u, it.v) }
        return Surimpression(traits, cibles)
    }

    private fun dans(p: CameraPaquet.Pixel, k: IntrinsequesImage) = p.u in 0.0..k.largeur.toDouble() && p.v in 0.0..k.hauteur.toDouble()
}
