package groma.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import groma.app.R
import groma.core.GroupeEssence

/**
 * Colour of a species group on the plan of the plot: Marculus' `COULEURS_ESSENCES_DEFAUT`, named in
 * `res/values/couleurs_essences.xml` (the only place where these colours are written).
 */
@Composable
fun GroupeEssence.couleur(): Color = colorResource(
    when (this) {
        GroupeEssence.CHENE -> R.color.essence_chene
        GroupeEssence.HETRE -> R.color.essence_hetre
        GroupeEssence.FEUILLU -> R.color.essence_feuillu
        GroupeEssence.SAPIN -> R.color.essence_sapin
        GroupeEssence.EPICEA -> R.color.essence_epicea
        GroupeEssence.RESINEUX -> R.color.essence_resineux
        GroupeEssence.INCONNUE -> R.color.essence_inconnue
    },
)
