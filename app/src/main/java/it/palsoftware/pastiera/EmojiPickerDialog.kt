package it.palsoftware.pastiera

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.palsoftware.pastiera.data.emoji.EmojiRepository

/**
 * Dialog for selecting an emoji.
 * Loads emoji categories from assets (common/emoji).
 */
@Composable
fun EmojiPickerDialog(
    selectedLetter: String? = null,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var uiState by remember { mutableStateOf(EmojiPickerState()) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        uiState = EmojiPickerState(isLoading = true, error = null)
        runCatching { EmojiRepository.getEmojiCategories(context) }
            .onSuccess { categories ->
                selectedCategoryId = categories.firstOrNull()?.id
                uiState = EmojiPickerState(
                    isLoading = false,
                    categories = categories,
                    error = null
                )
            }
            .onFailure { throwable ->
                uiState = EmojiPickerState(
                    isLoading = false,
                    categories = emptyList(),
                    error = throwable.message ?: context.getString(R.string.emoji_picker_error)
                )
            }
    }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedLetter != null) {
                            stringResource(R.string.emoji_picker_title_for_letter, selectedLetter)
                        } else {
                            stringResource(R.string.emoji_picker_title)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.emoji_picker_close), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.error != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = uiState.error ?: stringResource(id = R.string.emoji_picker_error))
                        }
                    }

                    uiState.categories.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = stringResource(id = R.string.emoji_picker_error))
                        }
                    }

                    else -> {
                        val categories = uiState.categories

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            categories.forEach { category ->
                                val label = category.displayNameRes?.let { stringResource(id = it) } ?: category.id
                                FilterChip(
                                    selected = selectedCategoryId == category.id,
                                    onClick = { selectedCategoryId = category.id },
                                    label = {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    },
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.first()

                        key(selectedCategory.id) {
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
                                        val emojiSize = (48 * context.resources.displayMetrics.density).toInt()
                                        val spacing = (2 * context.resources.displayMetrics.density).toInt()
                                        val padding = (4 * context.resources.displayMetrics.density).toInt()

                                        val columns = (screenWidth / (emojiSize + spacing)).coerceAtLeast(4)

                                        recyclerView.apply {
                                            layoutManager = GridLayoutManager(context, columns)
                                            adapter = EmojiEntryRecyclerViewAdapter(selectedCategory.emojis) { emoji ->
                                                onEmojiSelected(emoji)
                                                onDismiss()
                                            }
                                            setPadding(padding, padding, padding, padding)
                                            clipToPadding = false
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
    }
}

private data class EmojiPickerState(
    val isLoading: Boolean = true,
    val categories: List<EmojiRepository.EmojiCategory> = emptyList(),
    val error: String? = null
)

