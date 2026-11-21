package groma.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Mission 041b: the short line of the folded ribbon is red when C0 is not measured or comes from the phone. */
object RubanCentre {
    fun alerte(centreMesure: Boolean, centreQualite: String?): Boolean =
        !centreMesure || centreQualite == "telephone"
}

/** Mission 041b: ribbon folded by default; [resume] stays visible, [contenu] shows when unfolded. */
@Composable
fun RubanReplie(
    resume: String,
    alerte: Boolean,
    deplie: Boolean,
    onBasculer: () -> Unit,
    contenu: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onBasculer).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                resume,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (alerte) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(if (deplie) "▴" else "▾", style = MaterialTheme.typography.titleMedium)
        }
        if (deplie) contenu()
    }
}
