package it.palsoftware.pastiera

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Dialog for selecting a variation character for a specific letter.
 * Shows available variations from AllVariations.json.
 */
@Composable
fun VariationPickerDialog(
    letter: String,
    availableVariations: List<String>,
    onVariationSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Header section
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Centered title
                    Text(
                        text = stringResource(R.string.variation_picker_title, letter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    // Close button on the right
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(stringResource(R.string.unicode_picker_close), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                
                // Empty option button
                OutlinedButton(
                    onClick = {
                        onVariationSelected("")
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.variation_picker_clear))
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Character grid with RecyclerView for optimal performance
                key(letter) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds()
                    ) {
                        AndroidView(
                            factory = { context ->
                                val recyclerView = RecyclerView(context)
                                val screenWidth = context.resources.displayMetrics.widthPixels
                                val characterSize = (48 * context.resources.displayMetrics.density).toInt()
                                val spacing = (2 * context.resources.displayMetrics.density).toInt()
                                val padding = (4 * context.resources.displayMetrics.density).toInt()
                                
                                // Calculate number of columns based on screen width
                                val columns = (screenWidth / (characterSize + spacing)).coerceAtLeast(4)
                                
                                val layoutManager = GridLayoutManager(context, columns)
                                val adapter = UnicodeCharacterRecyclerViewAdapter(availableVariations) { character ->
                                    onVariationSelected(character)
                                    onDismiss()
                                }
                                
                                recyclerView.apply {
                                    this.layoutManager = layoutManager
                                    this.adapter = adapter
                                    setPadding(padding, padding, padding, padding)
                                    clipToPadding = false
                                    // Performance optimizations
                                    setHasFixedSize(true)
                                    setItemViewCacheSize(20)
                                }
                                recyclerView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}


