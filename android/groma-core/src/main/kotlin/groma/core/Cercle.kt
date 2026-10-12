package groma.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/** Circle fitting on 2D trunk sections (port of groma/cercle.py). */
data class CercleAjuste(
    val xc: Double, val yc: Double, val rayonM: Double, val rmseM: Double,
    val nPoints: Int, val nInliers: Int, val couvertureDeg: Double,
) {
    val dbhCm get() = 200.0 * rayonM
}

object Cercle {
    /** Kasa algebraic fit on centred data. */
    fun algebrique(x: DoubleArray, y: DoubleArray): Triple<Double, Double, Double> {
        val n = x.size
        require(n >= 3) { "Au moins 3 points sont nécessaires." }
        val mx = x.average()
        val my = y.average()
        // Normal equations of [u v 1] [D E F]^T = -(u² + v²)
        val a = Array(3) { DoubleArray(3) }
        val b = DoubleArray(3)
        for (i in 0 until n) {
            val u = x[i] - mx
            val v = y[i] - my
            val row = doubleArrayOf(u, v, 1.0)
            val rhs = -(u * u + v * v)
            for (r in 0..2) {
                for (c in 0..2) a[r][c] += row[r] * row[c]
                b[r] += row[r] * rhs
            }
        }
        val s = resoudre3(a, b)
        val xc = -s[0] / 2
        val yc = -s[1] / 2
        val r2 = xc * xc + yc * yc - s[2]
        require(r2 > 0) { "Ajustement dégénéré." }
        return Triple(xc + mx, yc + my, sqrt(r2))
    }

    /** Geometric fit (Gauss-Newton on orthogonal distances). */
    fun geometrique(x: DoubleArray, y: DoubleArray, init: Triple<Double, Double, Double>? = null, iterations: Int = 50): Triple<Double, Double, Double> {
        var (xc, yc, r) = init ?: algebrique(x, y)
        repeat(iterations) {
            val a = Array(3) { DoubleArray(3) }
            val b = DoubleArray(3)
            for (i in x.indices) {
                val dx = x[i] - xc
                val dy = y[i] - yc
                val di = maxOf(hypot(dx, dy), 1e-12)
                val j = doubleArrayOf(-dx / di, -dy / di, -1.0)
                val res = di - r
                for (p in 0..2) {
                    for (q in 0..2) a[p][q] += j[p] * j[q]
                    b[p] += -j[p] * res
                }
            }
            val pas = try { resoudre3(a, b) } catch (e: IllegalStateException) { return Triple(xc, yc, abs(r)) }
            xc += pas[0]; yc += pas[1]; r += pas[2]
            if (sqrt(pas[0] * pas[0] + pas[1] * pas[1] + pas[2] * pas[2]) < 1e-10) return Triple(xc, yc, abs(r))
        }
        return Triple(xc, yc, abs(r))
    }

    fun couvertureDeg(x: DoubleArray, y: DoubleArray, xc: Double, yc: Double, secteurs: Int = 72): Double {
        val occupes = HashSet<Int>()
        for (i in x.indices) {
            val a = (Math.toDegrees(kotlin.math.atan2(y[i] - yc, x[i] - xc)) + 360.0) % 360.0
            occupes += (a / (360.0 / secteurs)).toInt()
        }
        return occupes.size * 360.0 / secteurs
    }

    fun ransac(
        x: DoubleArray, y: DoubleArray, seuilM: Double = 0.015, iterations: Int = 300,
        rayonMinM: Double = 0.02, rayonMaxM: Double = 2.0, graine: Int = 0,
    ): CercleAjuste? {
        val n = x.size
        if (n < 5) return null
        val rng = Random(graine)
        var meilleur: BooleanArray? = null
        var nMeilleur = 0
        repeat(iterations) {
            val i = rng.nextInt(n); var j = rng.nextInt(n); var k = rng.nextInt(n)
            if (i == j || j == k || i == k) return@repeat
            val c = troisPoints(x[i], y[i], x[j], y[j], x[k], y[k]) ?: return@repeat
            if (c.third !in rayonMinM..rayonMaxM) return@repeat
            val inl = BooleanArray(n) { abs(hypot(x[it] - c.first, y[it] - c.second) - c.third) < seuilM }
            val cnt = inl.count { it }
            if (cnt > nMeilleur) { nMeilleur = cnt; meilleur = inl }
        }
        val sel = meilleur ?: return null
        if (nMeilleur < 5) return null
        val sx = x.filterIndexed { i, _ -> sel[i] }.toDoubleArray()
        val sy = y.filterIndexed { i, _ -> sel[i] }.toDoubleArray()
        val (xc, yc, r) = try { geometrique(sx, sy) } catch (e: IllegalArgumentException) { return null }
        if (r !in rayonMinM..rayonMaxM) return null
        var somme = 0.0
        val ix = ArrayList<Double>(); val iy = ArrayList<Double>()
        for (i in 0 until n) {
            val res = hypot(x[i] - xc, y[i] - yc) - r
            if (abs(res) < seuilM * 1.5) { somme += res * res; ix += x[i]; iy += y[i] }
        }
        val nIn = ix.size
        return CercleAjuste(xc, yc, r, if (nIn > 0) sqrt(somme / nIn) else Double.POSITIVE_INFINITY, n, nIn,
            couvertureDeg(ix.toDoubleArray(), iy.toDoubleArray(), xc, yc))
    }

    private fun troisPoints(x1: Double, y1: Double, x2: Double, y2: Double, x3: Double, y3: Double): Triple<Double, Double, Double>? {
        val d = 2.0 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2))
        if (abs(d) < 1e-12) return null
        val s1 = x1 * x1 + y1 * y1; val s2 = x2 * x2 + y2 * y2; val s3 = x3 * x3 + y3 * y3
        val xc = (s1 * (y2 - y3) + s2 * (y3 - y1) + s3 * (y1 - y2)) / d
        val yc = (s1 * (x3 - x2) + s2 * (x1 - x3) + s3 * (x2 - x1)) / d
        return Triple(xc, yc, hypot(x1 - xc, y1 - yc))
    }

    /** 3x3 linear solve (Gaussian elimination with partial pivoting). */
    internal fun resoudre3(a0: Array<DoubleArray>, b0: DoubleArray): DoubleArray {
        val a = Array(3) { a0[it].copyOf() }
        val b = b0.copyOf()
        for (c in 0..2) {
            val p = (c..2).maxBy { abs(a[it][c]) }
            check(abs(a[p][c]) > 1e-15) { "Système singulier." }
            val t = a[c]; a[c] = a[p]; a[p] = t
            val tb = b[c]; b[c] = b[p]; b[p] = tb
            for (r in c + 1..2) {
                val f = a[r][c] / a[c][c]
                for (k in c..2) a[r][k] -= f * a[c][k]
                b[r] -= f * b[c]
            }
        }
        val s = DoubleArray(3)
        for (r in 2 downTo 0) {
            var v = b[r]
            for (k in r + 1..2) v -= a[r][k] * s[k]
            s[r] = v / a[r][r]
        }
        return s
    }
}
