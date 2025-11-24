package it.palsoftware.pastiera

import android.view.KeyEvent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import it.palsoftware.pastiera.data.layout.JsonLayoutLoader
import it.palsoftware.pastiera.data.layout.LayoutMapping
import it.palsoftware.pastiera.data.layout.TapMapping

private data class KeyMappingRowModel(
    val keyCode: Int,
    val keyLabel: String,
    val lowercase: String,
    val uppercase: String,
    val multiTapEnabled: Boolean,
    val taps: List<TapMapping>
)

/**
 * Compact viewer for a keyboard layout mapping.
 */
@Composable
fun KeyboardLayoutViewerScreen(
    layoutName: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var isLoading by remember(layoutName) { mutableStateOf(true) }
    val layoutState by produceState<Map<Int, LayoutMapping>?>(initialValue = null, layoutName) {
        value = JsonLayoutLoader.loadLayout(context.assets, layoutName, context)
        isLoading = false
    }

    val items = remember(layoutState) {
        layoutState?.entries
            ?.sortedBy { it.key }
            ?.map { (keyCode, mapping) ->
                KeyMappingRowModel(
                    keyCode = keyCode,
                    keyLabel = KeyEvent.keyCodeToString(keyCode),
                    lowercase = mapping.lowercase,
                    uppercase = mapping.uppercase,
                    multiTapEnabled = mapping.multiTapEnabled,
                    taps = mapping.taps
                )
            }.orEmpty()
    }

    // Handle system back button
    BackHandler { onBack() }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_content_description)
                        )
                    }
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(
                            text = stringResource(R.string.keyboard_layout_viewer_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.keyboard_layout_viewer_subtitle, layoutName),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && layoutState == null -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                layoutState == null -> {
                    Text(
                        text = stringResource(R.string.keyboard_layout_viewer_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                items.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.keyboard_layout_viewer_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { it.keyCode }) { item ->
                            KeyMappingRow(item)
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyMappingRow(
    model: KeyMappingRowModel
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                tonalElevation = 0.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = model.keyLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Text(
                text = "${model.lowercase}/${model.uppercase}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.widthIn(min = 72.dp)
            )

            MultiTapBadge(enabled = model.multiTapEnabled)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                model.taps.forEachIndexed { index, tap ->
                    val label = if (tap.uppercase.isNotBlank()) {
                        "${tap.lowercase}/${tap.uppercase}"
                    } else {
                        tap.lowercase
                    }
                    TapChip(label = label)
                }
            }
        }
    }
}

@Composable
private fun MultiTapBadge(enabled: Boolean) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 0.dp,
        color = containerColor
    ) {
        Text(
            text = if (enabled) {
                stringResource(R.string.keyboard_layout_viewer_multitap_on)
            } else {
                stringResource(R.string.keyboard_layout_viewer_multitap_off)
            },
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun TapChip(
    label: String
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        tonalElevation = 0.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
