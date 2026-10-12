package groma.core

import kotlin.math.hypot

/**
 * Optimal assignment (Hungarian algorithm, O(n³)). On the phone it keeps the
 * uuid of a tree from one provisional run to the next, so that the QField
 * attributes stay attached; on the server side the full reconciliation is
 * done by nemeton (rapprocher_arbres) with the same rule.
 */
object Appariement {
    /** Minimal cost assignment on a rectangular cost matrix; -1 when unassigned. */
    fun hongrois(cout: Array<DoubleArray>): IntArray {
        val n = cout.size
        if (n == 0) return IntArray(0)
        val m = cout[0].size
        val dim = maxOf(n, m)
        val grand = 1e12
        val a = Array(dim + 1) { i -> DoubleArray(dim + 1) { j -> if (i in 1..n && j in 1..m) cout[i - 1][j - 1] else if (i == 0 || j == 0) 0.0 else grand } }
        val u = DoubleArray(dim + 1); val v = DoubleArray(dim + 1)
        val p = IntArray(dim + 1); val way = IntArray(dim + 1)
        for (i in 1..dim) {
            p[0] = i
            var j0 = 0
            val minv = DoubleArray(dim + 1) { Double.POSITIVE_INFINITY }
            val used = BooleanArray(dim + 1)
            do {
                used[j0] = true
                val i0 = p[j0]
                var delta = Double.POSITIVE_INFINITY
                var j1 = 0
                for (j in 1..dim) if (!used[j]) {
                    val cur = a[i0][j] - u[i0] - v[j]
                    if (cur < minv[j]) { minv[j] = cur; way[j] = j0 }
                    if (minv[j] < delta) { delta = minv[j]; j1 = j }
                }
                for (j in 0..dim) if (used[j]) { u[p[j]] += delta; v[j] -= delta } else minv[j] -= delta
                j0 = j1
            } while (p[j0] != 0)
            do { val j1 = way[j0]; p[j0] = p[j1]; j0 = j1 } while (j0 != 0)
        }
        val res = IntArray(n) { -1 }
        for (j in 1..dim) if (p[j] in 1..n && j <= m) res[p[j] - 1] = j - 1
        return res
    }

    /** For each new position, the index of the matched old one within tolerance, or -1. */
    fun apparier(
        anciens: List<Pair<Double, Double>>, nouveaux: List<Pair<Double, Double>>, toleranceM: Double = 0.5,
    ): IntArray {
        if (anciens.isEmpty() || nouveaux.isEmpty()) return IntArray(nouveaux.size) { -1 }
        val interdit = 1e9
        val cout = Array(nouveaux.size) { i ->
            DoubleArray(anciens.size) { j ->
                val d = hypot(nouveaux[i].first - anciens[j].first, nouveaux[i].second - anciens[j].second)
                if (d <= toleranceM) d else interdit
            }
        }
        val a = hongrois(cout)
        return IntArray(nouveaux.size) { i -> if (a[i] >= 0 && cout[i][a[i]] < interdit) a[i] else -1 }
    }
}
