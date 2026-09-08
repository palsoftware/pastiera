package it.palsoftware.pastiera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import it.palsoftware.pastiera.inputmethod.DeviceSpecific

/**
 * About screen displaying credits and acknowledgments from Markdown file.
 */
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var markdownContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        markdownContent = withContext(Dispatchers.IO) {
            loadCreditsFromAssets(context)
        }
        isLoading = false
    }


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
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.about_build_info),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = BuildInfo.getBuildInfoString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_device_keyboard_info,
                            DeviceSpecific.deviceName(),
                            DeviceSpecific.keyboardName()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Image(
                painter = painterResource(id = R.drawable.kofi5),
                contentDescription = stringResource(R.string.settings_support_ko_fi),
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterHorizontally)
                    .settingRow(SettingLinkIds.ABOUT_SUPPORT_KO_FI) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/palsoftware"))
                        )
                    }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(32.dp)
                )
            } else {
                markdownContent?.let { content ->
                    MarkdownContent(
                        markdown = content,
                        context = context,
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: Text(
                    text = context.getString(R.string.about_credits_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun MarkdownClickableText(
    text: AnnotatedString,
    style: TextStyle,
    urlMap: Map<Int, String>,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = text,
        style = style,
        modifier = modifier.pointerInput(urlMap) {
            detectTapGestures { position ->
                val offset = layoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                urlMap[offset]?.let { url -> openUrl(context, url) }
            }
        },
        onTextLayout = { layoutResult = it }
    )
}

/**
 * Sealed class representing markdown content elements.
 */
private sealed class MarkdownElement {
    data class Heading1(val text: String) : MarkdownElement()
    data class Heading2(val text: String) : MarkdownElement()
    data class Heading3(val text: String) : MarkdownElement()
    data class Heading4(val text: String) : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
    data class ListItem(val text: String) : MarkdownElement()
    data class Image(val altText: String, val url: String) : MarkdownElement()
    object HorizontalRule : MarkdownElement()
}

/**
 * Loads credits text from assets/common/about_credits.md
 */
private suspend fun loadCreditsFromAssets(context: android.content.Context): String? {
    return try {
        context.assets.open("common/about_credits.md").bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        android.util.Log.e("AboutScreen", "Error loading credits file", e)
        null
    }
}

/**
 * Parses Markdown into a list of MarkdownElement objects.
 */
private fun parseMarkdown(markdown: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = markdown.lines()

    var i = 0
    while (i < lines.size) {
        val line = lines[i].trim()

        when {
            line.isEmpty() -> {
                // Skip empty lines or treat as paragraph separator
                i++
            }
            line.startsWith("# ") -> {
                elements.add(MarkdownElement.Heading1(line.removePrefix("# ").trim()))
                i++
            }
            line.startsWith("## ") -> {
                elements.add(MarkdownElement.Heading2(line.removePrefix("## ").trim()))
                i++
            }
            line.startsWith("### ") -> {
                elements.add(MarkdownElement.Heading3(line.removePrefix("### ").trim()))
                i++
            }
            line.startsWith("#### ") -> {
                elements.add(MarkdownElement.Heading4(line.removePrefix("#### ").trim()))
                i++
            }
            line.startsWith("---") || line.startsWith("***") || line.startsWith("___") -> {
                elements.add(MarkdownElement.HorizontalRule)
                i++
            }
            line.startsWith("- ") || line.startsWith("* ") -> {
                val text = line.removePrefix("- ").removePrefix("* ").trim()
                elements.add(MarkdownElement.ListItem(text))
                i++
            }
            line.startsWith("![") && "]" in line && "(" in line -> {
                // Image: ![alt](url)
                val altEnd = line.indexOf("]", 2)
                val urlStart = line.indexOf("(", altEnd)
                val urlEnd = line.indexOf(")", urlStart)
                if (altEnd > 2 && urlStart > altEnd && urlEnd > urlStart) {
                    val altText = line.substring(2, altEnd)
                    val url = line.substring(urlStart + 1, urlEnd)
                    elements.add(MarkdownElement.Image(altText, url))
                }
                i++
            }
            else -> {
                // Regular paragraph - collect until empty line or next block element
                val paragraphLines = mutableListOf<String>()
                while (i < lines.size) {
                    val currentLine = lines[i].trim()
                    if (currentLine.isEmpty() ||
                        (currentLine.startsWith("#") && (currentLine.startsWith("# ") ||
                         currentLine.startsWith("## ") || currentLine.startsWith("### ") ||
                         currentLine.startsWith("#### "))) ||
                        currentLine.startsWith("- ") ||
                        currentLine.startsWith("* ") ||
                        currentLine.startsWith("---") ||
                        currentLine.startsWith("![") ||
                        currentLine.startsWith("***") ||
                        currentLine.startsWith("___")) {
                        break
                    }
                    paragraphLines.add(currentLine)
                    i++
                }
                if (paragraphLines.isNotEmpty()) {
                    elements.add(MarkdownElement.Paragraph(paragraphLines.joinToString(" ")))
                }
            }
        }
    }

    return elements
}

/**
 * Renders markdown content elements.
 */
@Composable
private fun MarkdownContent(
    markdown: String,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    val elements = remember(markdown) { parseMarkdown(markdown) }

    Column(modifier = modifier) {
        elements.forEachIndexed { index, element ->
            when (element) {
                is MarkdownElement.Heading1 -> {
                    val (text, urlMap) = parseInlineFormatting(element.text, context)
                    MarkdownHeading1(
                        text = text,
                        urlMap = urlMap,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is MarkdownElement.Heading2 -> {
                    val (text, urlMap) = parseInlineFormatting(element.text, context)
                    MarkdownHeading2(
                        text = text,
                        urlMap = urlMap,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is MarkdownElement.Heading3 -> {
                    val (text, urlMap) = parseInlineFormatting(element.text, context)
                    MarkdownHeading3(
                        text = text,
                        urlMap = urlMap,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                is MarkdownElement.Heading4 -> {
                    val (text, urlMap) = parseInlineFormatting(element.text, context)
                    MarkdownHeading4(
                        text = text,
                        urlMap = urlMap,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                is MarkdownElement.Paragraph -> {
                    val (text, urlMap) = parseInlineFormatting(element.text, context)
                    MarkdownParagraph(
                        text = text,
                        urlMap = urlMap,
                        context = context
                    )
                    if (index < elements.size - 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                is MarkdownElement.ListItem -> {
                    val (text, urlMap) = parseInlineFormatting(element.text, context)
                    MarkdownListItem(
                        text = text,
                        urlMap = urlMap,
                        context = context
                    )
                }
                is MarkdownElement.Image -> {
                    MarkdownImage(
                        altText = element.altText,
                        url = element.url,
                        context = context,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                is MarkdownElement.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownHeading1(
    text: AnnotatedString,
    urlMap: Map<Int, String>,
    context: android.content.Context
) {
    val colorScheme = MaterialTheme.colorScheme
    MarkdownClickableText(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = colorScheme.onSurface
        ),
        urlMap = urlMap,
        context = context
    )
}

@Composable
private fun MarkdownHeading2(
    text: AnnotatedString,
    urlMap: Map<Int, String>,
    context: android.content.Context
) {
    val colorScheme = MaterialTheme.colorScheme
    MarkdownClickableText(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = colorScheme.onSurface
        ),
        urlMap = urlMap,
        context = context
    )
}

@Composable
private fun MarkdownHeading3(
    text: AnnotatedString,
    urlMap: Map<Int, String>,
    context: android.content.Context
) {
    val colorScheme = MaterialTheme.colorScheme
    MarkdownClickableText(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = colorScheme.onSurface
        ),
        urlMap = urlMap,
        context = context
    )
}

@Composable
private fun MarkdownHeading4(
    text: AnnotatedString,
    urlMap: Map<Int, String>,
    context: android.content.Context
) {
    val colorScheme = MaterialTheme.colorScheme
    MarkdownClickableText(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = colorScheme.onSurface
        ),
        urlMap = urlMap,
        context = context
    )
}

@Composable
private fun MarkdownParagraph(
    text: AnnotatedString,
    urlMap: Map<Int, String>,
    context: android.content.Context
) {
    val colorScheme = MaterialTheme.colorScheme
    MarkdownClickableText(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = colorScheme.onSurface
        ),
        urlMap = urlMap,
        context = context
    )
}

@Composable
private fun MarkdownListItem(
    text: AnnotatedString,
    urlMap: Map<Int, String>,
    context: android.content.Context
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colorScheme.onSurface
            ),
            modifier = Modifier.padding(top = 2.dp)
        )
        MarkdownClickableText(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = colorScheme.onSurface
            ),
            modifier = Modifier.weight(1f),
            urlMap = urlMap,
            context = context
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun MarkdownImage(
    altText: String,
    url: String,
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(url) {
        loadImageFromAssets(context, url)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = altText,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        // Fallback: show alt text if image can't be loaded
        Text(
            text = "[Image: $altText]",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            fontStyle = FontStyle.Italic
        )
    }
}

/**
 * Loads image from assets or tries to load as URL.
 */
private fun loadImageFromAssets(context: android.content.Context, path: String): android.graphics.Bitmap? {
    return try {
        // Try loading from assets first
        context.assets.open(path).use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: run {
            // If not in assets, could be a URL - for now return null
            // In the future could use Coil or similar to load from URL
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("AboutScreen", "Error loading image: $path", e)
        null
    }
}

/**
 * Parses inline formatting (bold, italic, links) in text.
 * Returns AnnotatedString and a map of positions to URLs.
 */
@Composable
private fun parseInlineFormatting(
    text: String,
    context: android.content.Context
): Pair<AnnotatedString, Map<Int, String>> {
    val colorScheme = MaterialTheme.colorScheme
    val builder = AnnotatedString.Builder()
    val urlMap = mutableMapOf<Int, String>()
    var i = 0

    while (i < text.length) {
        when {
            // Link: [text](url)
            text.startsWith("[", i) && "]" in text.substring(i + 1) &&
            "(" in text.substring(i) -> {
                val linkEnd = text.indexOf("]", i)
                val urlStart = text.indexOf("(", linkEnd)
                val urlEnd = text.indexOf(")", urlStart)

                if (linkEnd > i && urlStart > linkEnd && urlEnd > urlStart) {
                    val linkText = text.substring(i + 1, linkEnd)
                    val url = text.substring(urlStart + 1, urlEnd)

                    val linkStartPos = builder.length
                    builder.append(linkText)
                    val linkEndPos = builder.length

                    // Store URL for all positions in the link
                    for (pos in linkStartPos until linkEndPos) {
                        urlMap[pos] = url
                    }

                    builder.addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = linkStartPos,
                        end = linkEndPos
                    )

                    i = urlEnd + 1
                    continue
                }
            }
            // Bold: **text**
            text.startsWith("**", i) && text.indexOf("**", i + 2) > i -> {
                val boldEnd = text.indexOf("**", i + 2)
                val boldText = text.substring(i + 2, boldEnd)
                val boldStartPos = builder.length
                builder.append(boldText)
                val boldEndPos = builder.length
                builder.addStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    ),
                    start = boldStartPos,
                    end = boldEndPos
                )
                i = boldEnd + 2
                continue
            }
            // Bold: __text__ (alternative syntax)
            text.startsWith("__", i) && text.indexOf("__", i + 2) > i -> {
                val boldEnd = text.indexOf("__", i + 2)
                val boldText = text.substring(i + 2, boldEnd)
                val boldStartPos = builder.length
                builder.append(boldText)
                val boldEndPos = builder.length
                builder.addStyle(
                    style = SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    ),
                    start = boldStartPos,
                    end = boldEndPos
                )
                i = boldEnd + 2
                continue
            }
            // Italic: *text* (but not if it's part of **)
            text.startsWith("*", i) && !text.startsWith("**", i) &&
            text.indexOf("*", i + 1) > i && (i + 1 >= text.length || text[i + 1] != '*') -> {
                val italicEnd = text.indexOf("*", i + 1)
                val italicText = text.substring(i + 1, italicEnd)
                val italicStartPos = builder.length
                builder.append(italicText)
                val italicEndPos = builder.length
                builder.addStyle(
                    style = SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = colorScheme.onSurface
                    ),
                    start = italicStartPos,
                    end = italicEndPos
                )
                i = italicEnd + 1
                continue
            }
            // Italic: _text_ (but not if it's part of __)
            text.startsWith("_", i) && !text.startsWith("__", i) &&
            text.indexOf("_", i + 1) > i && (i + 1 >= text.length || text[i + 1] != '_') -> {
                val italicEnd = text.indexOf("_", i + 1)
                val italicText = text.substring(i + 1, italicEnd)
                val italicStartPos = builder.length
                builder.append(italicText)
                val italicEndPos = builder.length
                builder.addStyle(
                    style = SpanStyle(
                        fontStyle = FontStyle.Italic,
                        color = colorScheme.onSurface
                    ),
                    start = italicStartPos,
                    end = italicEndPos
                )
                i = italicEnd + 1
                continue
            }
            // Inline code: `code`
            text.startsWith("`", i) && text.indexOf("`", i + 1) > i -> {
                val codeEnd = text.indexOf("`", i + 1)
                val codeText = text.substring(i + 1, codeEnd)
                val codeStartPos = builder.length
                builder.append(codeText)
                val codeEndPos = builder.length
                builder.addStyle(
                    style = SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        background = colorScheme.surfaceVariant,
                        color = colorScheme.onSurfaceVariant
                    ),
                    start = codeStartPos,
                    end = codeEndPos
                )
                i = codeEnd + 1
                continue
            }
            else -> {
                builder.append(text[i])
                i++
            }
        }
    }

    // The TextStyle in the composables already has the correct color (colorScheme.onSurface),
    // and inline styles (bold, italic, links, code) explicitly set their colors.
    // Text without inline formatting will inherit the color from the TextStyle in the composable.
    return Pair(builder.toAnnotatedString(), urlMap)
}

/**
 * Opens a URL in browser or appropriate app.
 */
private fun openUrl(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("AboutScreen", "Error opening URL: $url", e)
    }
}
