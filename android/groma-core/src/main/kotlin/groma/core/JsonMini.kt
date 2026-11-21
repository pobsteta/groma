package groma.core

/**
 * Minimal JSON reader (objects -> Map, arrays -> List, numbers -> Double, strings, Boolean, null) so that
 * groma-core reads a package manifest without a dependency (rule 6). Not a validator beyond what it needs.
 */
internal object JsonMini {
    fun lire(texte: String): Any? {
        val p = Lecteur(texte)
        val v = p.valeur()
        p.blancs()
        require(p.i == texte.length) { "JSON : texte en trop à la position ${p.i}." }
        return v
    }

    private class Lecteur(val s: String) {
        var i = 0

        fun blancs() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun valeur(): Any? {
            blancs()
            require(i < s.length) { "JSON : fin inattendue." }
            return when (val c = s[i]) {
                '{' -> objet()
                '[' -> tableau()
                '"' -> chaine()
                't' -> mot("true", true)
                'f' -> mot("false", false)
                'n' -> mot("null", null)
                else -> if (c == '-' || c.isDigit()) nombre() else throw IllegalArgumentException("JSON : caractère '$c' inattendu à la position $i.")
            }
        }

        private fun mot(m: String, v: Any?): Any? {
            require(s.startsWith(m, i)) { "JSON : '$m' attendu à la position $i." }
            i += m.length
            return v
        }

        private fun nombre(): Double {
            val d = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(d, i).toDouble()
        }

        private fun chaine(): String {
            i++ // opening quote
            val b = StringBuilder()
            while (true) {
                require(i < s.length) { "JSON : chaîne non fermée." }
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> b.append('\n')
                            't' -> b.append('\t')
                            'r' -> b.append('\r')
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000c')
                            'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> b.append(e)
                        }
                    }
                    else -> b.append(c)
                }
            }
        }

        private fun tableau(): List<Any?> {
            i++
            val l = ArrayList<Any?>()
            blancs()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += valeur()
                blancs()
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return l
                    else -> throw IllegalArgumentException("JSON : ',' ou ']' attendu à la position ${i - 1}.")
                }
            }
        }

        private fun objet(): Map<String, Any?> {
            i++
            val m = LinkedHashMap<String, Any?>()
            blancs()
            if (s[i] == '}') { i++; return m }
            while (true) {
                blancs()
                require(s[i] == '"') { "JSON : clé attendue à la position $i." }
                val k = chaine()
                blancs()
                require(s[i++] == ':') { "JSON : ':' attendu à la position ${i - 1}." }
                m[k] = valeur()
                blancs()
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return m
                    else -> throw IllegalArgumentException("JSON : ',' ou '}' attendu à la position ${i - 1}.")
                }
            }
        }
    }
}
