package groma.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EtatKanbanTest {
    private class Memoire : MagasinKanban {
        val textes = mutableMapOf<String, String>()
        val booleens = mutableMapOf<String, Boolean>()
        override fun lireTexte(cle: String) = textes[cle]
        override fun ecrireTexte(cle: String, valeur: String) { textes[cle] = valeur }
        override fun lireBooleen(cle: String, parDefaut: Boolean) = booleens[cle] ?: parDefaut
        override fun ecrireBooleen(cle: String, valeur: Boolean) { booleens[cle] = valeur }
    }

    private fun resume(nbBase: Int = 4, baseFaites: Int = 0, faites: Int = baseFaites, envoyees: Int = 0, nonEnvoyees: Int = 0) =
        ResumeProjet(nbBase, 0, faites, baseFaites, envoyees, nonEnvoyees, 0.0, null, null, null)

    @Test fun `les cinq colonnes, dans l'ordre, avec leurs libelles`() {
        assertEquals(listOf("Proposée", "Validée", "Planifiée", "Réalisée", "Abandonnée"), EtatKanban.entries.map { it.libelle })
    }

    @Test fun `aucune placette faite donne Proposee`() {
        assertEquals(EtatKanban.PROPOSEE, statutMinimal(resume()))
    }

    @Test fun `une placette faite donne au moins Planifiee`() {
        assertEquals(EtatKanban.PLANIFIEE, statutMinimal(resume(baseFaites = 1, faites = 1, nonEnvoyees = 1)))
    }

    @Test fun `une placette de remplacement faite suffit pour Planifiee`() {
        assertEquals(EtatKanban.PLANIFIEE, statutMinimal(resume(baseFaites = 0, faites = 1)))
    }

    @Test fun `tout fait mais pas tout envoye reste Planifiee`() {
        val r = resume(baseFaites = 4, envoyees = 3, nonEnvoyees = 1)
        assertTrue(r.termine)
        assertEquals(EtatKanban.PLANIFIEE, statutMinimal(r))
    }

    @Test fun `tout fait et tout envoye donne Realisee`() {
        assertEquals(EtatKanban.REALISEE, statutMinimal(resume(baseFaites = 4, envoyees = 4)))
    }

    @Test fun `base incomplete reste Planifiee meme si tout ce qui est fait est envoye`() {
        val r = resume(baseFaites = 3, envoyees = 3)
        assertFalse(r.termine)
        assertEquals(EtatKanban.PLANIFIEE, statutMinimal(r))
    }

    @Test fun `un plan sans placette de base n'est jamais Realisee`() {
        assertEquals(EtatKanban.PROPOSEE, statutMinimal(resume(nbBase = 0)))
    }

    @Test fun `colonne affichee = le plus avance du statut garde et du minimum`() {
        assertEquals(EtatKanban.PLANIFIEE, colonneAffichee(EtatKanban.PROPOSEE, EtatKanban.PLANIFIEE))
        assertEquals(EtatKanban.PLANIFIEE, colonneAffichee(EtatKanban.VALIDEE, EtatKanban.PLANIFIEE))
        assertEquals(EtatKanban.REALISEE, colonneAffichee(EtatKanban.REALISEE, EtatKanban.PLANIFIEE))
        assertEquals(EtatKanban.VALIDEE, colonneAffichee(EtatKanban.VALIDEE, EtatKanban.PROPOSEE))
        assertEquals(EtatKanban.ABANDONNEE, colonneAffichee(EtatKanban.ABANDONNEE, EtatKanban.REALISEE))
    }

    @Test fun `un depot sous le minimum est refuse`() {
        val minimal = EtatKanban.PLANIFIEE
        assertNull(deposer(EtatKanban.PLANIFIEE, EtatKanban.PROPOSEE, minimal))
        assertNull(deposer(EtatKanban.PLANIFIEE, EtatKanban.VALIDEE, minimal))
    }

    @Test fun `un depot au minimum ou au-dessus est accepte, dans la meme colonne il ne change rien`() {
        assertEquals(EtatKanban.REALISEE, deposer(EtatKanban.PLANIFIEE, EtatKanban.REALISEE, EtatKanban.PLANIFIEE))
        assertEquals(EtatKanban.ABANDONNEE, deposer(EtatKanban.PLANIFIEE, EtatKanban.ABANDONNEE, EtatKanban.PLANIFIEE))
        assertEquals(EtatKanban.VALIDEE, deposer(EtatKanban.PROPOSEE, EtatKanban.VALIDEE, EtatKanban.PROPOSEE))
        assertNull(deposer(EtatKanban.VALIDEE, EtatKanban.VALIDEE, EtatKanban.PROPOSEE))
    }

    @Test fun `un statut inconnu ou absent vaut Proposee`() {
        assertEquals(EtatKanban.PROPOSEE, EtatKanban.depuisNom(null))
        assertEquals(EtatKanban.PROPOSEE, EtatKanban.depuisNom("N_IMPORTE_QUOI"))
        assertEquals(EtatKanban.VALIDEE, EtatKanban.depuisNom("VALIDEE"))
    }

    @Test fun `repartir presente les cinq colonnes, vides comprises, dans l'ordre d'origine`() {
        val m = repartir(listOf("a" to EtatKanban.VALIDEE, "b" to EtatKanban.PROPOSEE, "c" to EtatKanban.VALIDEE)) { it.second }
        assertEquals(EtatKanban.entries.toList(), m.keys.toList())
        assertEquals(listOf("a", "c"), m.getValue(EtatKanban.VALIDEE).map { it.first })
        assertTrue(m.getValue(EtatKanban.ABANDONNEE).isEmpty())
    }

    @Test fun `les reglages gardent le statut par projet et le choix de vue`() {
        val mem = Memoire()
        val r = ReglagesKanban(mem)
        assertEquals(EtatKanban.PROPOSEE, r.statut("p1"))
        assertFalse(r.vueColonnes)
        r.definirStatut("p1", EtatKanban.ABANDONNEE)
        r.vueColonnes = true
        val relu = ReglagesKanban(mem)
        assertEquals(EtatKanban.ABANDONNEE, relu.statut("p1"))
        assertEquals(EtatKanban.PROPOSEE, relu.statut("p2"))
        assertTrue(relu.vueColonnes)
    }
}
