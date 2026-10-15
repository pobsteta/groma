package groma.app.gnss

import android.content.Context

enum class TransportRtk { BLUETOOTH, TCP }

/**
 * External RTK receiver and NTRIP caster (brief 2026-10-14 carte RTK, § 6).
 * [ntrip] = true: Groma pulls RTCM from the caster and forwards it to the
 * receiver; false: the receiver gets its corrections by itself. Centipède by
 * default (docs/terrain.md, « Corrections RTK Centipède »); the mount point
 * is the nearest base, picked with « Charger ».
 */
data class ConfigRtk(
    val actif: Boolean = false,
    val transport: TransportRtk = TransportRtk.BLUETOOTH,
    val appareilBt: String? = null,
    val appareilBtNom: String? = null,
    val hoteTcp: String = "192.168.4.1",
    val portTcp: Int = 2947,
    val ntrip: Boolean = true,
    val casterHote: String = "caster.centipede.fr",
    val casterPort: Int = 2101,
    val pointMontage: String = "",
    val utilisateur: String = "centipede",
    val motDePasse: String = "centipede",
    /** Length of « Mesurer le centre » (s), brief § 5.4. */
    val dureeCentreS: Int = 10,
) {
    companion object {
        private fun prefs(ctx: Context) = ctx.getSharedPreferences("groma", Context.MODE_PRIVATE)

        fun lire(ctx: Context): ConfigRtk = prefs(ctx).let { p ->
            val d = ConfigRtk()
            ConfigRtk(
                actif = p.getBoolean("rtk_actif", d.actif),
                transport = runCatching { TransportRtk.valueOf(p.getString("rtk_transport", null)!!) }.getOrDefault(d.transport),
                appareilBt = p.getString("rtk_appareil", null),
                appareilBtNom = p.getString("rtk_appareil_nom", null),
                hoteTcp = p.getString("rtk_hote_tcp", d.hoteTcp)!!,
                portTcp = p.getInt("rtk_port_tcp", d.portTcp),
                ntrip = p.getBoolean("rtk_ntrip", d.ntrip),
                casterHote = p.getString("rtk_caster", d.casterHote)!!,
                casterPort = p.getInt("rtk_caster_port", d.casterPort),
                pointMontage = p.getString("rtk_point_montage", d.pointMontage)!!,
                utilisateur = p.getString("rtk_utilisateur", d.utilisateur)!!,
                motDePasse = p.getString("rtk_mot_de_passe", d.motDePasse)!!,
                dureeCentreS = p.getInt("rtk_duree_centre", d.dureeCentreS),
            )
        }

        fun memoriser(ctx: Context, c: ConfigRtk) = prefs(ctx).edit()
            .putBoolean("rtk_actif", c.actif).putString("rtk_transport", c.transport.name)
            .putString("rtk_appareil", c.appareilBt).putString("rtk_appareil_nom", c.appareilBtNom)
            .putString("rtk_hote_tcp", c.hoteTcp).putInt("rtk_port_tcp", c.portTcp)
            .putBoolean("rtk_ntrip", c.ntrip).putString("rtk_caster", c.casterHote).putInt("rtk_caster_port", c.casterPort)
            .putString("rtk_point_montage", c.pointMontage).putString("rtk_utilisateur", c.utilisateur)
            .putString("rtk_mot_de_passe", c.motDePasse).putInt("rtk_duree_centre", c.dureeCentreS)
            .apply()
    }
}
