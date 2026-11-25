package fr.cyberagenda.core.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/**
 * Choix d'une application, présenté comme le sélecteur système (icônes et noms).
 * Utilisé pour l'agenda et l'itinéraire : la liste vient directement du système, elle est donc
 * complète et identique sur tous les téléphones (les sélecteurs de certaines surcouches,
 * comme HyperOS, masquent des applications).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppChooserSheet(
    title: String,
    apps: List<ExternalApps.AppTarget>,
    onPick: (ExternalApps.AppTarget) -> Unit,
    onDismiss: () -> Unit,
    footerLabel: String? = null,
    onFooter: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        FilterObscuredTouches()
        // Même protection que les autres panneaux : un lancer au-delà du contenu ne le fait pas bouger.
        val guard = rememberSheetListGuard()
        val scroll = rememberScrollState()
        Column(
            Modifier.fillMaxWidth().nestedScroll(guard)
                .verticalScroll(scroll, overscrollEffect = overscrollIfScrollable(scroll, guard.overscroll))
                .padding(bottom = 16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                apps.forEach { app ->
                    val bitmap = remember(app.packageName) { app.icon.toBitmap(144, 144).asImageBitmap() }
                    Column(
                        Modifier.width(84.dp).clip(MaterialTheme.shapes.medium)
                            .clickable(onClickLabel = "$title ${app.label}") { onDismiss(); onPick(app) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Image(bitmap, contentDescription = null, modifier = Modifier.size(52.dp))
                        Text(
                            app.label, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                            maxLines = 2, modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
            if (footerLabel != null && onFooter != null) {
                TextButton(onClick = { onDismiss(); onFooter() }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(footerLabel, style = Mono.meta)
                }
            }
        }
    }
}
