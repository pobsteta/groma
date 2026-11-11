// QField project plugin for inventaire_video (Groma). PROTOTYPE, to validate on device.
//
// Android 11+ keeps QField's imported project in QField's private folder,
// which Groma cannot open. Groma therefore serves what it produced on
// http://127.0.0.1:8765/inventaire/<projet_id> (ServeurLocal.kt) and this
// plugin writes it into QField's copy itself, through the layer API, so the
// GeoPackage triggers (declencheurs.sql) create the provisional `arbres`
// rows exactly as for a direct write. Nothing already present is modified:
// detections are matched on `uuid`, centres on `plot_id` + `date_mesure`.
//
// Mission 018: what was entered in Groma on each tree (essence_arbre, tape
// diameter dbh_cm + dbh_source, statut, qualite; answer key `arbres`) is then
// copied into QField's `arbres`, by `uuid`, never over a value entered in
// QField (function aRecopier, same rule as RecopieQfield.kt, tested there).
//
// API checked against QField 3.7 to 4.3 sources (see docs/terrain.md).
// Must sit next to the project as `<project name>.qml`.

import QtQuick
import QtQuick.Controls
import org.qfield
import org.qgis
import Theme

Item {
  id: plugin

  readonly property string base: "http://127.0.0.1:8765"
  readonly property var champsDetection: ["uuid", "plot_id", "num_provisoire", "azimut", "distance_m", "dbh_cm", "h_m",
                                          "h_source", "houppier_id", "etat_detection", "methode", "score", "date_detection"]
  readonly property var champsCentre: ["plot_id", "qualite_gnss", "precision_h_m", "ecart_prevu_m", "date_mesure"]
  property var mainWindow: iface.mainWindow()
  property bool enCours: false

  Component.onCompleted: iface.addItemToPluginsToolbar(bouton)

  QfToolButton {
    id: bouton
    iconSource: Theme.getThemeVectorIcon("ic_download_white_24dp")
    iconColor: Theme.toolButtonColor
    bgcolor: Theme.toolButtonBackgroundColor
    round: true
    enabled: !plugin.enCours
    onClicked: plugin.synchroniser()
  }

  function toast(message, type) {
    mainWindow.displayToast(message, type === undefined ? "info" : type)
  }

  function couche(nom) {
    const l = qgisProject.mapLayersByName(nom)
    return l.length > 0 ? l[0] : null
  }

  // projet_id from the one-row `projet` table (schema >= 1.1.0).
  function projetId() {
    const p = couche("projet")
    if (!p) return null
    const it = LayerUtils.createFeatureIterator(p)
    let id = null
    if (it.hasNext()) id = it.next().attribute("projet_id")
    it.close()
    return id ? String(id) : null
  }

  function synchroniser() {
    const id = projetId()
    if (!id) {
      toast(qsTr("Groma : table « projet » absente ou vide, projet_id inconnu."), "error")
      return
    }
    const moderne = typeof iface.createHttpRequest === "function"  // QField >= 4.1
    const req = moderne ? iface.createHttpRequest() : new XMLHttpRequest()
    req.onreadystatechange = function () {
      if (req.readyState !== XMLHttpRequest.DONE) return
      plugin.enCours = false
      if (req.status === 404) {
        toast(qsTr("Groma n'a rien pour ce projet (%1).").arg(id), "warning")
        return
      }
      if (req.status !== 200) {
        toast(qsTr("Groma injoignable : ouvrir Groma sur ce téléphone, puis réessayer."), "error")
        return
      }
      let donnees
      try {
        donnees = JSON.parse(req.responseText)
      } catch (e) {
        toast(qsTr("Groma : réponse illisible."), "error")
        return
      }
      importer(donnees)
    }
    plugin.enCours = true
    req.open("GET", base + "/inventaire/" + encodeURIComponent(id))
    if (moderne) req.timeout = 10000
    req.send()
  }

  // Keys already present in `layer`, built by `cle(feature)`.
  function clesExistantes(layer, cle) {
    const vues = {}
    const it = LayerUtils.createFeatureIterator(layer)
    while (it.hasNext()) vues[cle(it.next())] = true
    it.close()
    return vues
  }

  // Appends the missing `elements` to the layer named `nom`. Returns
  // {ajoutes, ignores} or {erreur}.
  function ajouter(nom, elements, champs, cleFeature, cleElement) {
    if (!elements || elements.length === 0) return { ajoutes: 0, ignores: 0 }
    const layer = couche(nom)
    if (!layer) return { erreur: qsTr("couche « %1 » absente").arg(nom) }
    if (LayerUtils.isFeatureAdditionLocked(layer)) return { erreur: qsTr("ajout verrouillé sur « %1 »").arg(nom) }
    const vues = clesExistantes(layer, cleFeature)
    const nouveaux = elements.filter(e => !vues[cleElement(e)])
    if (nouveaux.length === 0) return { ajoutes: 0, ignores: elements.length }
    // Never commit someone else's pending edits along with ours.
    if (layer.isEditable || !layer.startEditing())
      return { erreur: qsTr("« %1 » est déjà en cours d'édition : enregistrer ou annuler, puis réessayer").arg(nom) }
    const fields = layer.fields
    let ajoutes = 0
    for (const e of nouveaux) {
      // Coordinates come in the GeoPackage CRS (Lambert-93), as the layer.
      const geom = GeometryUtils.createGeometryFromWkt("POINT(" + e.x + " " + e.y + ")")
      const f = FeatureUtils.createFeature(layer, geom)
      for (const c of champs) {
        const i = fields.indexOf(c)
        if (i >= 0 && e[c] !== undefined && e[c] !== null) f.setAttribute(i, e[c])
      }
      if (LayerUtils.addFeature(layer, f)) ajoutes++
    }
    if (!layer.commitChanges()) {
      layer.rollBack()
      return { erreur: qsTr("échec de l'enregistrement dans « %1 »").arg(nom) }
    }
    return { ajoutes: ajoutes, ignores: elements.length - nouveaux.length }
  }

  // QGIS hands DATETIME back as a Date, Groma sends ISO text: compare instants.
  function instant(v) {
    if (v === undefined || v === null || v === "") return ""
    const t = new Date(v).getTime()
    return isNaN(t) ? String(v) : String(t)
  }

  // ---- Copy of the attributes entered in Groma ---------------------------
  // SAME RULE, LINE FOR LINE, AS groma.core.RecopieQfield.aRecopier (Kotlin,
  // android/groma-core, tested by RecopieQfieldTest): change both together.
  // Returns ONLY the attributes to write in QField ({} = nothing to do).
  function vide(v) {
    return v === undefined || v === null || String(v).trim() === ""
  }

  function aRecopier(groma, qfield) {
    const res = {}
    for (const k of ["essence_arbre", "qualite"]) {
      if (!vide(groma[k]) && vide(qfield[k])) res[k] = groma[k]
    }
    const sq = qfield["statut"]
    if (!vide(groma["statut"]) && groma["statut"] !== "vivant" && (vide(sq) || sq === "vivant")) res["statut"] = groma["statut"]
    const sourceQ = qfield["dbh_source"]
    if (groma["dbh_source"] === "mesure" && !vide(groma["dbh_cm"]) && (vide(sourceQ) || sourceQ === "estime")) {
      res["dbh_cm"] = groma["dbh_cm"]
      res["dbh_source"] = "mesure"
    }
    return res
  }

  // Layer WITHOUT `dbh_source` (project generated before schema 1.4.0): QField's
  // source is then unknown, and aRecopier would read it as empty and overwrite a
  // tape diameter typed in QField. So neither dbh_cm nor dbh_source is copied
  // there (brief nemeton section 4). Done here, not in aRecopier, whose pure rule
  // (RecopieQfield.aRecopier, RecopieQfieldTest) stays the same on both sides;
  // Kotlin side: nothing to do, the plugin is the only writer of QField's copy.
  function sansDiametreSiPasDeSource(r, aDbhSource) {
    if (aDbhSource) return r
    const res = {}
    for (const k of Object.keys(r)) if (k !== "dbh_cm" && k !== "dbh_source") res[k] = r[k]
    return res
  }

  // The five attributes of QField's tree (NULL comes back null: `vide` copes).
  function attributsArbre(f, fields) {
    const a = {}
    for (const c of ["essence_arbre", "dbh_cm", "dbh_source", "statut", "qualite"]) {
      if (fields.indexOf(c) >= 0) a[c] = f.attribute(c)
    }
    return a
  }

  // Copies the attributes of `saisies` (answer key `arbres`, one object per
  // uuid) into the `arbres` layer: one validated edit for the whole layer,
  // nothing if the layer is already being edited. Returns {recopies} or {erreur}.
  function recopierArbres(saisies) {
    if (!saisies || saisies.length === 0) return { recopies: 0 }
    const layer = couche("arbres")
    if (!layer) return { erreur: qsTr("couche « arbres » absente") }
    const fields = layer.fields
    const aDbhSource = fields.indexOf("dbh_source") >= 0
    const parUuid = {}
    for (const s of saisies) parUuid[String(s.uuid)] = s
    // Plan first, edit afterwards: no edit session when there is nothing to copy.
    const plan = []
    const it = LayerUtils.createFeatureIterator(layer)
    while (it.hasNext()) {
      const f = it.next()
      const g = parUuid[String(f.attribute("uuid"))]
      if (!g) continue
      const r = sansDiametreSiPasDeSource(aRecopier(g, attributsArbre(f, fields)), aDbhSource)
      if (Object.keys(r).length > 0) plan.push({ fid: f.id, valeurs: r })
    }
    it.close()
    if (plan.length === 0) return { recopies: 0 }
    // Never commit someone else's pending edits along with ours.
    if (layer.isEditable || !layer.startEditing())
      return { erreur: qsTr("« arbres » est déjà en cours d'édition : enregistrer ou annuler, puis réessayer") }
    let recopies = 0
    for (const p of plan) {
      for (const k of Object.keys(p.valeurs)) {
        const i = fields.indexOf(k)
        // dbh_cm and dbh_source go together: counted as ONE attribute (counted on dbh_cm).
        if (i >= 0 && layer.changeAttributeValue(p.fid, i, p.valeurs[k]) && k !== "dbh_source") recopies++
      }
    }
    if (!layer.commitChanges()) {
      layer.rollBack()
      return { erreur: qsTr("échec de l'enregistrement dans « arbres »") }
    }
    return { recopies: recopies }
  }

  function importer(d) {
    const det = ajouter("detections", d.detections, champsDetection,
                        f => String(f.attribute("uuid")), e => String(e.uuid))
    const cen = ajouter("centres_mesures", d.centres, champsCentre,
                        f => f.attribute("plot_id") + "|" + instant(f.attribute("date_mesure")),
                        e => e.plot_id + "|" + instant(e.date_mesure))
    // Rows created by the triggers in `arbres` only show once it is re-read.
    const relire = () => {
      for (const nom of ["arbres", "detections", "centres_mesures"]) {
        const l = couche(nom)
        if (!l) continue
        if (typeof l.reload === "function") l.reload()
        l.triggerRepaint()
      }
    }
    relire()
    // The new rows are there: they receive what was entered in Groma.
    const rec = recopierArbres(d.arbres)
    if (rec.recopies) relire()
    const erreurs = [det.erreur, cen.erreur, rec.erreur].filter(e => e)
    const bilan = qsTr("Groma : %1 détection(s) et %2 centre(s) ajoutés, %3 déjà présents, %4 attributs recopiés.")
      .arg(det.ajoutes || 0).arg(cen.ajoutes || 0).arg((det.ignores || 0) + (cen.ignores || 0)).arg(rec.recopies || 0)
    if (erreurs.length > 0) toast(bilan + " " + erreurs.join(" ; "), "error")
    else toast(bilan)
  }
}
