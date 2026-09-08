package it.palsoftware.pastiera

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Id of the settings row that is currently highlighted (deep link or search
 * result target), or null. Rows opt in via [settingRow].
 */
val LocalSettingHighlightId = compositionLocalOf<String?> { null }

/**
 * Invoked with a setting link id when the user long-presses an addressable row.
 * Null when the surrounding screen does not support sharing (e.g. previews).
 */
val LocalSettingLinkLongPress = compositionLocalOf<((String) -> Unit)?> { null }

/**
 * Makes a settings row addressable: long-press asks the surrounding app to show
 * the share/copy sheet, and while its id matches [LocalSettingHighlightId] the
 * row scrolls into view and its outline blinks a few times (no persistent
 * highlight). The outline is drawn on top of the row content so it works even
 * when the row root is a Surface that paints its own background.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.settingRow(
    linkId: String?,
    onClick: (() -> Unit)? = null
): Modifier {
    if (linkId == null) {
        return if (onClick != null) clickable(onClick = onClick) else this
    }
    val highlightId = LocalSettingHighlightId.current
    val longPressHandler = LocalSettingLinkLongPress.current
    val hapticFeedback = LocalHapticFeedback.current
    val highlighted = highlightId == linkId

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val outlineColor = MaterialTheme.colorScheme.tertiary
    val outlineAlpha = remember { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            bringIntoViewRequester.bringIntoView()
            // ~3 blinks over ~1.65 s; the screen clears the highlight after
            // 1.8 s, which fades the remaining outline out gently.
            outlineAlpha.snapTo(0f)
            outlineAlpha.animateTo(1f, tween(durationMillis = 150))
            repeat(3) {
                outlineAlpha.animateTo(0.15f, tween(durationMillis = 250))
                outlineAlpha.animateTo(1f, tween(durationMillis = 250))
            }
        } else {
            outlineAlpha.animateTo(0f, tween(durationMillis = 300))
        }
    }

    val onLongClick = longPressHandler?.let { handler ->
        {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            handler(linkId)
        }
    }

    val base = this
        .bringIntoViewRequester(bringIntoViewRequester)
        .drawWithContent {
            drawContent()
            if (outlineAlpha.value > 0.01f) {
                val stroke = 2.dp.toPx()
                drawRoundRect(
                    color = outlineColor.copy(alpha = outlineAlpha.value),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = Stroke(width = stroke)
                )
            }
        }
    return when {
        onClick != null && onLongClick != null ->
            base.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        onClick != null ->
            base.clickable(onClick = onClick)
        onLongClick != null ->
            base.pointerInput(linkId, onLongClick) {
                detectTapGestures(onLongPress = { onLongClick() })
            }
        else -> base
    }
}

/**
 * Breadcrumb like "Settings › Text input" for a settings entry.
 */
@Composable
fun settingEntryBreadcrumb(entry: SettingEntry): String {
    val context = LocalContext.current
    val root = stringResource(R.string.settings_title)
    val screen = entry.route.destination.let { destination ->
        SettingLinkRegistry.destinationTitles[destination]?.let { context.getString(it) }
    }
    val subtitle = entry.route.customizationDestination?.let { sub ->
        SettingLinkRegistry.customizationSubtitles[sub]?.let { context.getString(it) }
    }
    val deviceSubtitle = SettingLinkRegistry.keyboardsDevicesSubtitles[entry.route.keyboardsDevicesDestination]
        ?.let(context::getString)
    return listOfNotNull(root, screen, subtitle, deviceSubtitle).joinToString(" › ")
}

/**
 * Bottom sheet shown after long-pressing an addressable settings row. Offers
 * copying the raw deep link, copying it as a markdown link, and sharing the
 * markdown link (with the setting title as label, or the raw link when the
 * description toggle is off); opening the link only navigates and highlights,
 * it never changes any value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingLinkSheet(
    entry: SettingEntry,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val link = SettingLinkRegistry.buildLink(entry.id)
    val title = stringResource(entry.titleRes)
    var withDescription by rememberSaveable { mutableStateOf(true) }
    val markdown = SettingLinkRegistry.buildSettingMarkdown(title, link, withDescription)

    fun copyText(text: String) {
        clipboardManager.setText(AnnotatedString(text))
        Toast.makeText(context, R.string.settings_link_copied_toast, Toast.LENGTH_SHORT).show()
    }

    fun shareMarkdown() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, markdown)
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = settingEntryBreadcrumb(entry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = link,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = markdown,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_link_with_description),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = withDescription,
                    onCheckedChange = { withDescription = it }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { copyText(link) }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_link_copy))
                }
                Button(onClick = { copyText(markdown) }) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_link_copy_markdown))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { shareMarkdown() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.width(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_link_share))
            }
        }
    }
}

/**
 * Search field for the settings main screen.
 */
@Composable
fun SettingsSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        placeholder = { Text(stringResource(R.string.settings_search_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.auto_correct_clear_search)
                    )
                }
            }
        }
    )
}

/**
 * One search result row: entry title with its screen breadcrumb.
 */
@Composable
fun SettingSearchResultRow(
    entry: SettingEntry,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(entry.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = settingEntryBreadcrumb(entry),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
