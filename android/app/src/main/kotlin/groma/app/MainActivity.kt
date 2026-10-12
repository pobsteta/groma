package groma.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class MainActivity : AppCompatActivity() {
    private var projet: Projet? = null
    private var plotEnAttente: String? = null

    // ARCore throws if the camera permission is missing: ask before opening a plot.
    private val demanderCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { accorde ->
        val plot = plotEnAttente.also { plotEnAttente = null } ?: return@registerForActivityResult
        if (accorde) ouvrirPlacette(plot)
        else Toast.makeText(this, "La caméra est nécessaire pour filmer la placette.", Toast.LENGTH_LONG).show()
    }

    private val choisirDossier = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        projet = try { Projet.importer(this, uri) } catch (e: Exception) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show(); null
        }
        Preferences.memoriserProjet(this, projet?.dossier)
        afficher()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activite_principale)
        projet = Preferences.projet(this)
        findViewById<Button>(R.id.ouvrir).setOnClickListener { choisirDossier.launch(null) }
        val p = Preferences.prefs(this)
        findViewById<EditText>(R.id.serveur).setText(p.getString("serveur", ""))
        findViewById<EditText>(R.id.jeton).setText(p.getString("jeton", ""))
        findViewById<CheckBox>(R.id.envoi4g).isChecked = p.getBoolean("4g", false)
        ServeurLocal(File(filesDir, "detections")).demarrer()
        afficher()
    }

    override fun onPause() {
        super.onPause()
        Preferences.prefs(this).edit()
            .putString("serveur", findViewById<EditText>(R.id.serveur).text.toString())
            .putString("jeton", findViewById<EditText>(R.id.jeton).text.toString())
            .putBoolean("4g", findViewById<CheckBox>(R.id.envoi4g).isChecked)
            .apply()
    }

    private fun afficher() {
        val pr = projet ?: return
        val lignes = pr.placettes().map { "${it["plot_id"]} · ${it["etat_traitement"] ?: "a_faire"}" to it["plot_id"] as String }
        val liste = findViewById<ListView>(R.id.placettes)
        liste.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, lignes.map { it.first })
        liste.setOnItemClickListener { _, _, i, _ ->
            val plot = lignes[i].second
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) ouvrirPlacette(plot)
            else { plotEnAttente = plot; demanderCamera.launch(Manifest.permission.CAMERA) }
        }
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

    fun projet(ctx: Context): Projet? = prefs(ctx).getString("projet", null)?.let { File(it) }?.takeIf { it.exists() }?.let { Projet(it) }

    fun planifierEnvoi(ctx: Context, paquet: File, plotId: String) {
        val p = prefs(ctx)
        val serveur = p.getString("serveur", "").orEmpty()
        if (serveur.isBlank()) return // kept on the phone until a server is configured
        EnvoiWorker.planifier(ctx, paquet, plotId, serveur, p.getString("jeton", null)?.ifBlank { null }, p.getBoolean("4g", false))
    }
}
