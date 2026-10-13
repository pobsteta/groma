package groma.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import groma.app.ui.EcranParametres
import groma.app.ui.EcranPlacettes
import groma.app.ui.GromaTheme
import groma.app.ui.LignePlacette
import groma.app.ui.Reglages
import java.io.File

class MainActivity : AppCompatActivity() {
    private var projet by mutableStateOf<Projet?>(null)
    // Bumped on resume so the list shows the processing state written by a capture.
    private var rafraichir by mutableIntStateOf(0)
    private var plotEnAttente: String? = null

    private val choisirDossier = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        projet = try { Projet.importer(this, uri) } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show(); null
        }
        Preferences.memoriserProjet(this, projet?.dossier)
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
        projet = Preferences.projet(this)
        ServeurLocal.demarrerUneFois(File(filesDir, "detections"))
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        setContent {
            GromaTheme {
                var parametres by rememberSaveable { mutableStateOf(false) }
                var reglages by remember { mutableStateOf(Preferences.reglages(this)) }
                if (parametres) {
                    BackHandler { parametres = false }
                    EcranParametres(
                        reglages = reglages,
                        onChange = { reglages = it; Preferences.memoriserReglages(this, it) },
                        onRetour = { parametres = false },
                    )
                } else {
                    val pr = projet
                    val placettes = remember(pr, rafraichir) { pr?.let(::lignes).orEmpty() }
                    EcranPlacettes(
                        projetOuvert = pr != null,
                        placettes = placettes,
                        version = version,
                        onOuvrirProjet = { choisirDossier.launch(null) },
                        onPlacette = ::demanderPlacette,
                        onParametres = { parametres = true },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rafraichir++
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
        val pr = projet ?: return
        startActivity(Intent(this, CaptureActivity::class.java)
            .putExtra(CaptureActivity.EXTRA_PROJET, pr.dossier.path)
            .putExtra(CaptureActivity.EXTRA_PLOT, plot))
    }
}

object Preferences {
    fun prefs(ctx: Context) = ctx.getSharedPreferences("groma", Context.MODE_PRIVATE)

    fun memoriserProjet(ctx: Context, dossier: File?) = prefs(ctx).edit().putString("projet", dossier?.path).apply()

    fun reglages(ctx: Context): Reglages = prefs(ctx).let {
        Reglages(it.getString("serveur", "").orEmpty(), it.getString("jeton", "").orEmpty(), it.getBoolean("4g", false))
    }

    fun memoriserReglages(ctx: Context, r: Reglages) =
        prefs(ctx).edit().putString("serveur", r.serveur).putString("jeton", r.jeton).putBoolean("4g", r.envoi4g).apply()

    fun projet(ctx: Context): Projet? = prefs(ctx).getString("projet", null)?.let { File(it) }?.takeIf { it.exists() }?.let { Projet(it) }

    fun planifierEnvoi(ctx: Context, paquet: File, plotId: String) {
        val p = prefs(ctx)
        val serveur = p.getString("serveur", "").orEmpty()
        if (serveur.isBlank()) return // kept on the phone until a server is configured
        EnvoiWorker.planifier(ctx, paquet, plotId, serveur, p.getString("jeton", null)?.ifBlank { null }, p.getBoolean("4g", false))
    }
}
