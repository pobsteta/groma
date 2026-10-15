package groma.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import groma.app.R
import groma.app.gnss.ClientNtrip
import groma.app.gnss.ConfigRtk
import groma.app.gnss.ServiceGnssRtk
import groma.app.gnss.TransportRtk
import groma.core.PointMontage
import groma.core.StatutNtrip
import kotlinx.coroutines.launch

/**
 * Paramètres → « Récepteur RTK » (brief § 6, taken from Marculus's
 * SectionRtk): switch, transport, device, NTRIP caster, Tester, diagnostic.
 * Settings are saved as they are typed; Tester (re)starts the link with them.
 */
@Composable
fun SectionRtk() {
    val ctx = LocalContext.current
    var rtk by remember { mutableStateOf(ConfigRtk.lire(ctx)) }
    fun maj(c: ConfigRtk) { rtk = c; ConfigRtk.memoriser(ctx, c) }
    val etat by ServiceGnssRtk.etat.collectAsStateWithLifecycle()
    val fix by ServiceGnssRtk.fix.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var choixAppareil by remember { mutableStateOf(false) }
    var points by remember { mutableStateOf<List<PointMontage>>(emptyList()) }
    var menuPoints by remember { mutableStateOf(false) }
    var chargement by remember { mutableStateOf(false) }
    var etatGnss by remember { mutableStateOf(false) }

    val permissionBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) choixAppareil = true }
    // Android 13+: without it the « Libérer le récepteur » notification stays hidden.
    val permissionNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Text(stringResource(R.string.rtk_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    Text(stringResource(R.string.rtk_section_desc), style = MaterialTheme.typography.bodySmall)
    Interrupteur(stringResource(R.string.rtk_actif), stringResource(R.string.rtk_actif_desc), rtk.actif) {
        maj(rtk.copy(actif = it))
        if (!it) ServiceGnssRtk.arreter(ctx)
    }
    if (!rtk.actif) return

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Choix(stringResource(R.string.rtk_bluetooth), rtk.transport == TransportRtk.BLUETOOTH) { maj(rtk.copy(transport = TransportRtk.BLUETOOTH)) }
        Choix(stringResource(R.string.rtk_tcp), rtk.transport == TransportRtk.TCP) { maj(rtk.copy(transport = TransportRtk.TCP)) }
    }
    when (rtk.transport) {
        TransportRtk.BLUETOOTH -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(rtk.appareilBtNom ?: stringResource(R.string.rtk_aucun_appareil), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                ) choixAppareil = true
                else permissionBt.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }) { Text(stringResource(R.string.rtk_choisir)) }
        }
        TransportRtk.TCP -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Champ(rtk.hoteTcp, { maj(rtk.copy(hoteTcp = it.trim())) }, stringResource(R.string.rtk_hote), Modifier.weight(2f))
            Champ(rtk.portTcp.toString(), { v -> v.toIntOrNull()?.let { maj(rtk.copy(portTcp = it)) } }, stringResource(R.string.rtk_port), Modifier.weight(1f), KeyboardType.Number)
        }
    }

    Interrupteur(stringResource(R.string.rtk_ntrip), stringResource(R.string.rtk_ntrip_desc), rtk.ntrip) { maj(rtk.copy(ntrip = it)) }
    if (rtk.ntrip) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Champ(rtk.casterHote, { maj(rtk.copy(casterHote = it.trim())) }, stringResource(R.string.rtk_caster), Modifier.weight(2f))
            Champ(rtk.casterPort.toString(), { v -> v.toIntOrNull()?.let { maj(rtk.copy(casterPort = it)) } }, stringResource(R.string.rtk_port), Modifier.weight(1f), KeyboardType.Number)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Champ(rtk.pointMontage, { maj(rtk.copy(pointMontage = it.trim())) }, stringResource(R.string.rtk_point_montage), Modifier.weight(1f))
            Box {
                OutlinedButton(enabled = !chargement, onClick = {
                    chargement = true
                    scope.launch {
                        points = ClientNtrip.pointsMontage(rtk.casterHote, rtk.casterPort)
                        chargement = false
                        menuPoints = true
                    }
                }) { Text(stringResource(R.string.rtk_charger)) }
                DropdownMenu(expanded = menuPoints, onDismissRequest = { menuPoints = false }) {
                    if (points.isEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.rtk_aucun_point)) }, onClick = { menuPoints = false })
                    points.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.nom} · ${p.identifiant} · ${p.format}") },
                            onClick = { maj(rtk.copy(pointMontage = p.nom)); menuPoints = false },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Champ(rtk.utilisateur, { maj(rtk.copy(utilisateur = it.trim())) }, stringResource(R.string.rtk_utilisateur), Modifier.weight(1f))
            Champ(rtk.motDePasse, { maj(rtk.copy(motDePasse = it)) }, stringResource(R.string.rtk_mot_de_passe), Modifier.weight(1f), KeyboardType.Password)
        }
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) permissionNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            ServiceGnssRtk.demarrer(ctx)
        }) { Text(stringResource(R.string.rtk_tester)) }
        OutlinedButton(onClick = { ServiceGnssRtk.arreter(ctx) }) { Text(stringResource(R.string.rtk_liberer)) }
    }
    BadgeFix(fix, etat) { etatGnss = true }

    // Diagnostic: link not up / bytes but no NMEA / NMEA OK; caster status and corrections.
    val lien = when {
        etat.erreur != null -> stringResource(R.string.rtk_diag_erreur, etat.erreur ?: "")
        !etat.enMarche -> stringResource(R.string.rtk_diag_arrete)
        etat.octetsRecus == 0L -> stringResource(R.string.rtk_connexion)
        etat.tramesRecues == 0L -> stringResource(R.string.rtk_diag_sans_nmea, etat.octetsRecus)
        else -> stringResource(R.string.rtk_diag_trames, etat.tramesRecues, etat.octetsRecus)
    }
    Text(lien, style = MaterialTheme.typography.bodySmall)
    etat.derniereTrame?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
    if (rtk.ntrip && etat.enMarche) {
        val caster = when {
            rtk.pointMontage.isBlank() -> stringResource(R.string.rtk_ntrip_sans_point)
            etat.ntripErreur != null -> stringResource(R.string.rtk_diag_erreur, etat.ntripErreur ?: "")
            etat.ntripStatut == StatutNtrip.OK -> stringResource(R.string.rtk_ntrip_ok)
            etat.ntripStatut == StatutNtrip.NON_AUTORISE -> stringResource(R.string.rtk_ntrip_401)
            etat.ntripStatut == StatutNtrip.TABLE_SOURCES -> stringResource(R.string.rtk_ntrip_table)
            etat.ntripStatut == StatutNtrip.INCONNU -> stringResource(R.string.rtk_ntrip_inconnu)
            else -> stringResource(R.string.rtk_ntrip_connexion)
        }
        Text(caster, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.rtk_diag_flux, etat.ggaEnvoye, etat.rtcmEnvoye), style = MaterialTheme.typography.bodySmall)
    }

    // Length of the centre measurement (averaged RTK fixed fixes).
    Text(stringResource(R.string.rtk_duree_centre), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(5, 10, 30, 60).forEach { s -> Choix("$s s", rtk.dureeCentreS == s) { maj(rtk.copy(dureeCentreS = s)) } }
    }

    if (choixAppareil) {
        val appareils = remember { appareilsAppaires(ctx) }
        AlertDialog(
            onDismissRequest = { choixAppareil = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choixAppareil = false }) { Text(stringResource(R.string.annuler)) } },
            title = { Text(stringResource(R.string.rtk_appareils)) },
            text = {
                Column {
                    if (appareils.isEmpty()) Text(stringResource(R.string.rtk_aucun_appaire))
                    appareils.forEach { (nom, adresse) ->
                        TextButton(onClick = { maj(rtk.copy(appareilBt = adresse, appareilBtNom = nom)); choixAppareil = false }) { Text(nom) }
                    }
                }
            },
        )
    }
    if (etatGnss) DialogueEtatGnss(fix, etat) { etatGnss = false }
}

@Composable
private fun Champ(valeur: String, onChange: (String) -> Unit, libelle: String, modifier: Modifier, clavier: KeyboardType = KeyboardType.Text) {
    // Follows the saved value when it changes from outside (mount point picked with « Charger »).
    var texte by remember(valeur) { mutableStateOf(valeur) }
    OutlinedTextField(
        value = texte,
        onValueChange = { texte = it; onChange(it) },
        label = { Text(libelle) },
        singleLine = true,
        visualTransformation = if (clavier == KeyboardType.Password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = clavier),
        modifier = modifier,
    )
}

@Composable
private fun Choix(libelle: String, choisi: Boolean, onClick: () -> Unit) {
    if (choisi) Button(onClick = onClick) { Text(libelle) } else OutlinedButton(onClick = onClick) { Text(libelle) }
}

@Composable
private fun Interrupteur(titre: String, description: String, valeur: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titre, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = valeur, onCheckedChange = onChange)
    }
}

/** Paired Bluetooth devices (name, address); empty without permission. */
@SuppressLint("MissingPermission")
private fun appareilsAppaires(ctx: Context): List<Pair<String, String>> {
    val adaptateur = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching { adaptateur.bondedDevices.map { (it.name ?: it.address) to it.address } }.getOrDefault(emptyList())
}
