package groma.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import groma.app.carte.configurerOsmdroid
import groma.app.gnss.ServiceGnssRtk
import groma.app.gnss.SourcePosition
import groma.app.ui.EcranCarte
import groma.app.ui.EcranParametres
import groma.app.ui.EcranPlacettes
import groma.app.ui.EcranProjets
import groma.app.ui.EcranProjetsServeur
import groma.app.ui.LigneServeur
import groma.app.ui.GromaTheme
import groma.app.ui.LignePlacette
import groma.app.ui.Reglages
import groma.core.DonneesCarte
import groma.core.choixThemeDepuis
import groma.core.ProjetsServeur
import groma.core.TriProjets
import java.io.File
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Projects (home) → plots of a project → capture; back goes up one level. */
class MainActivity : AppCompatActivity() {
    private val depot by lazy { DepotProjets(this) }
    private var projets by mutableStateOf<List<EntreeProjet>>(emptyList())
    private var chargement by mutableStateOf(false)
    // Bumped on resume so the lists show the processing state written by a capture.
    private var rafraichir by mutableIntStateOf(0)
    private var projetOuvert by mutableStateOf<Projet?>(null)
    private var plotEnAttente: String? = null

    private val choisirRacine = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        depot.choisirRacine(uri)
        actualiser()
    }

    private val importer = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        tache { depot.importer(uri) }
    }

    // ARCore throws if the camera permission is missing: ask before opening a plot.
    private val demanderCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { accorde ->
        val plot = plotEnAttente.also { plotEnAttente = null } ?: return@registerForActivityResult
        if (accorde) ouvrirPlacette(plot)
        else Toast.makeText(this, R.string.camera_requise, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ServeurLocal.demarrerUneFois(File(filesDir, "detections"), File(getExternalFilesDir(null), "projets"))
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        configurerOsmdroid(this, version)
        appliquerPleinEcran(window, Preferences.reglages(this).pleinEcran)
        setContent {
            var reglages by remember { mutableStateOf(Preferences.reglages(this)) }
            GromaTheme(darkTheme = reglages.theme.estSombre(isSystemInDarkTheme())) {
                var parametres by rememberSaveable { mutableStateOf(false) }
                var projetsServeur by rememberSaveable { mutableStateOf(false) }
                var cle by rememberSaveable { mutableStateOf<String?>(null) }
                var carte by rememberSaveable { mutableStateOf(false) }
                // Leaving the project releases the RTK receiver (for QField, and the battery).
                LaunchedEffect(cle) { if (cle == null) ServiceGnssRtk.arreter(this@MainActivity) }
                val ouvert = remember(cle) { cle?.let(depot::projet) }
                SideEffect { projetOuvert = ouvert }
                when {
                    parametres -> {
                        BackHandler { parametres = false }
                        EcranParametres(
                            reglages = reglages,
                            onChange = {
                                reglages = it
                                Preferences.memoriserReglages(this, it)
                                appliquerTheme(it.theme)
                                appliquerPleinEcran(window, it.pleinEcran)
                            },
                            onRetour = { parametres = false },
                        )
                    }
                    projetsServeur -> {
                        BackHandler { projetsServeur = false }
                        EcranProjetsServeur(
                            serveurConfigure = reglages.serveur.isNotBlank(),
                            racineChoisie = depot.racine != null,
                            charger = { withContext(Dispatchers.IO) { lignesServeur(reglages) } },
                            onTelecharger = { m -> depot.racine?.let { TelechargementProjet.planifier(this, m, it) } },
                            onAnnuler = { m -> TelechargementProjet.annuler(this, m.projetId) },
                            onInstalle = ::actualiser,
                            onParametres = { parametres = true },
                            onChoisirRacine = { choisirRacine.launch(DOSSIER_PAR_DEFAUT) },
                            onRetour = { projetsServeur = false },
                        )
                    }
                    ouvert != null && carte -> {
                        BackHandler { carte = false }
                        val titre = projets.firstOrNull { it.cle == cle }?.titre ?: ouvert.dossier.name
                        // Reloaded on resume, so a plot filmed from the map changes colour.
                        val donnees by produceState<DonneesCarte?>(null, ouvert, rafraichir) {
                            value = withContext(Dispatchers.IO) {
                                try { ouvert.donneesCarte() } catch (e: Exception) { null }
                            }
                        }
                        EcranCarte(
                            titre = titre,
                            donnees = donnees,
                            dossierProjet = ouvert.dossier,
                            onRetour = { carte = false },
                            onOuvrirPlacette = ::demanderPlacette,
                            onEnregistrerCentre = { plotId, centre ->
                                withContext(Dispatchers.IO) { ouvert.ajouterCentre(plotId, centre) }.also { rafraichir++; ServicePartageQfield.demarrer(this@MainActivity) }
                            },
                        )
                    }
                    ouvert != null -> {
                        BackHandler { cle = null }
                        // The list may still be loading (activity recreated after a capture): follow it.
                        val titre = projets.firstOrNull { it.cle == cle }?.titre ?: ouvert.dossier.name
                        val placettes = remember(ouvert, rafraichir) { lignes(ouvert) }
                        EcranPlacettes(
                            titre = titre,
                            placettes = placettes,
                            version = version,
                            onRetour = { cle = null },
                            onPlacette = ::demanderPlacette,
                            onCarte = { carte = true },
                            onParametres = { parametres = true },
                        )
                    }
                    else -> EcranProjets(
                        projets = projets,
                        racineChoisie = depot.racine != null,
                        chargement = chargement,
                        version = version,
                        onOuvrir = { e -> depot.dernierOuvert = e.cle; cle = e.cle; carte = false },
                        onActualiser = ::actualiser,
                        onChoisirRacine = { choisirRacine.launch(DOSSIER_PAR_DEFAUT) },
                        onImporter = { importer.launch(null) },
                        onProjetsServeur = { projetsServeur = true },
                        serveurConfigure = reglages.serveur.isNotBlank(),
                        onMettreAJour = { e -> tache { message(depot.mettreAJour(e), R.string.projet_mis_a_jour) } },
                        onRetirer = { e -> tache { message(depot.retirer(e), null) } },
                        onOuvrirQfield = ::ouvrirQfield,
                        onParametres = { parametres = true },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rafraichir++
        actualiser()
    }

    /** Copies the new folders and reloads the cards, off the main thread. */
    private fun actualiser() = tache {}

    /** Runs [bloc] off the main thread, then reloads the list; errors are shown, not thrown. */
    private fun tache(bloc: () -> Unit) {
        if (chargement) return
        chargement = true
        lifecycleScope.launch {
            val erreurs = mutableListOf<String>()
            val liste = withContext(Dispatchers.IO) {
                try { bloc() } catch (e: Exception) { erreurs += e.message ?: e.toString() }
                depot.actualiser(erreurs)
            }
            projets = trier(liste)
            chargement = false
            if (erreurs.isNotEmpty()) Toast.makeText(this@MainActivity, erreurs.joinToString("\n"), Toast.LENGTH_LONG).show()
        }
    }

    private fun lignesServeur(r: Reglages): List<LigneServeur> {
        val presents = depot.cles()
        return ProjetsDuServeur.lister(r.serveur, r.jeton.ifBlank { null }).map { m ->
            LigneServeur(m, ProjetsServeur.etat(m, m.projetId in presents, ProjetsDuServeur.versionInstallee(this, m.projetId)))
        }
    }

    /**
     * Hands the QField project to QField as a zip (ACTION_VIEW, then ACTION_SEND):
     * QField imports it into "Imported Projects", plugin included, and opens
     * it. Each import is a new folder there, without what was entered in the
     * previous one: the same version is reopened from QField (resending stays
     * possible if QField's copy was deleted), a new version is sent with a
     * warning. Falls back to the folder to import by hand.
     */
    private fun ouvrirQfield(e: EntreeProjet) {
        lifecycleScope.launch {
            val empreinte = try {
                withContext(Dispatchers.IO) { depot.empreinteQfield(e) }
            } catch (ex: Exception) {
                Toast.makeText(this@MainActivity, ex.message ?: ex.toString(), Toast.LENGTH_LONG).show(); return@launch
            }
            val precedent = depot.envoiQfield(e.cle) ?: return@launch envoyerQfield(e, empreinte)
            val qfield = packageManager.getLaunchIntentForPackage(QFIELD)
            val ouvrir = { startActivity(qfield) }
            val envoyer = { lifecycleScope.launch { envoyerQfield(e, empreinte) } }
            val d = AlertDialog.Builder(this@MainActivity).setNegativeButton(R.string.annuler, null)
            if (precedent == empreinte) {
                d.setTitle(R.string.qfield_deja_titre).setMessage(getString(R.string.qfield_deja_texte, e.cle))
                if (qfield != null) d.setPositiveButton(R.string.qfield_ouvrir_app) { _, _ -> ouvrir() }
                d.setNeutralButton(R.string.qfield_renvoyer) { _, _ -> envoyer() }
            } else {
                d.setTitle(R.string.qfield_nouvelle_version_titre).setMessage(getString(R.string.qfield_nouvelle_version_texte, e.cle))
                d.setPositiveButton(R.string.qfield_envoyer_version) { _, _ -> envoyer() }
                if (qfield != null) d.setNeutralButton(R.string.qfield_ouvrir_app) { _, _ -> ouvrir() }
            }
            d.show()
        }
    }

    private suspend fun envoyerQfield(e: EntreeProjet, empreinte: String) {
        Toast.makeText(this, R.string.qfield_preparation, Toast.LENGTH_SHORT).show()
        val zip = try {
            withContext(Dispatchers.IO) { depot.archiveQfield(e) }
        } catch (ex: Exception) {
            Toast.makeText(this, ex.message ?: ex.toString(), Toast.LENGTH_LONG).show(); return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.fichiers", zip)
        val vue = Intent(Intent.ACTION_VIEW).setDataAndType(uri, TYPE_ZIP)
        val envoi = Intent(Intent.ACTION_SEND).setType(TYPE_ZIP).putExtra(Intent.EXTRA_STREAM, uri)
        for (i in listOf(vue, envoi)) {
            i.setPackage(QFIELD).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                startActivity(i)
                depot.memoriserEnvoiQfield(e.cle, empreinte)
                return
            } catch (_: ActivityNotFoundException) {}
        }
        val installe = try { packageManager.getPackageInfo(QFIELD, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
        AlertDialog.Builder(this)
            .setTitle(R.string.qfield_repli_titre)
            .setMessage(getString(R.string.qfield_repli_texte, if (installe) "" else " " + getString(R.string.qfield_absent), e.cle))
            .setPositiveButton(R.string.fermer, null)
            .show()
    }

    private fun message(r: DepotProjets.Resultat, ok: Int?) {
        val texte = when (r) {
            is DepotProjets.Resultat.Refuse -> r.raison
            DepotProjets.Resultat.Fait -> ok?.let(::getString) ?: return
        }
        runOnUiThread { Toast.makeText(this, texte, Toast.LENGTH_LONG).show() }
    }

    /**
     * Last opened project first, then by distance to the phone when a position
     * is known (location allowed on the map), else by name (French collation).
     */
    private fun trier(liste: List<EntreeProjet>): List<EntreeProjet> {
        val collator = Collator.getInstance(Locale.FRENCH)
        val position = SourcePosition.positionApprochee(this)
        return TriProjets.trier(
            liste, depot.dernierOuvert, position, { it.cle }, { it.infos?.emprise },
            Comparator { a, b -> collator.compare(a.titre, b.titre) },
        ).map { it.copy(distanceM = TriProjets.distanceM(position, it.infos?.emprise)) }
    }

    private fun lignes(pr: Projet): List<LignePlacette> = try {
        pr.placettes().map {
            LignePlacette(
                it["plot_id"] as String,
                it["etat_traitement"] as? String ?: "a_faire",
                (it["rayon_m"] as? Number)?.toDouble(),
                (it["seuil_diam_cm"] as? Number)?.toDouble(),
            )
        }
    } catch (e: Exception) {
        Toast.makeText(this, e.message, Toast.LENGTH_LONG).show(); emptyList()
    }

    private fun demanderPlacette(plot: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) ouvrirPlacette(plot)
        else { plotEnAttente = plot; demanderCamera.launch(Manifest.permission.CAMERA) }
    }

    private fun ouvrirPlacette(plot: String) {
        val pr = projetOuvert ?: return
        startActivity(Intent(this, CaptureActivity::class.java)
            .putExtra(CaptureActivity.EXTRA_PROJET, pr.dossier.path)
            .putExtra(CaptureActivity.EXTRA_PLOT, plot))
    }

    companion object {
        /** Where the folder picker opens the first time: Téléchargements/groma. */
        private const val QFIELD = "ch.opengis.qfield"
        private const val TYPE_ZIP = "application/zip"

        private val DOSSIER_PAR_DEFAUT: Uri =
            DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Download/groma")
    }
}

object Preferences {
    fun prefs(ctx: Context) = ctx.getSharedPreferences("groma", Context.MODE_PRIVATE)

    fun reglages(ctx: Context): Reglages = prefs(ctx).let {
        Reglages(it.getString("serveur", "").orEmpty(), it.getString("jeton", "").orEmpty(), it.getBoolean("4g", false),
            choixThemeDepuis(it.getString("theme", null)), it.getBoolean("plein_ecran", false))
    }

    fun memoriserReglages(ctx: Context, r: Reglages) =
        prefs(ctx).edit().putString("serveur", r.serveur).putString("jeton", r.jeton).putBoolean("4g", r.envoi4g)
            .putString("theme", r.theme.name).putBoolean("plein_ecran", r.pleinEcran).apply()

    fun planifierEnvoi(ctx: Context, paquet: File, projetId: String, plotId: String) {
        val p = prefs(ctx)
        val serveur = p.getString("serveur", "").orEmpty()
        if (serveur.isBlank()) return // kept on the phone until a server is configured
        EnvoiWorker.planifier(ctx, paquet, projetId, plotId, serveur, p.getString("jeton", null)?.ifBlank { null }, p.getBoolean("4g", false))
    }
}
