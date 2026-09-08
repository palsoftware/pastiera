package it.palsoftware.pastiera

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import it.palsoftware.pastiera.ui.theme.PastieraTheme
import it.palsoftware.pastiera.BuildConfig
import it.palsoftware.pastiera.update.checkForUpdate
import it.palsoftware.pastiera.update.fetchReleaseNotesForVersion
import it.palsoftware.pastiera.update.ReleaseNotesSummary
import it.palsoftware.pastiera.update.showUpdateDialog
import it.palsoftware.pastiera.update.shouldUseGithubUpdateChecks
import it.palsoftware.pastiera.inputmethod.DeviceSpecific
import it.palsoftware.pastiera.inputmethod.SoftwareKeyboardAutoDetector
import it.palsoftware.pastiera.inputmethod.TypingSoundPlayer
import it.palsoftware.pastiera.data.variation.VariationRepository
import java.util.Locale

private const val ACTION_UNIHERTZ_GESTURE_NAVIGATION_SETTINGS = "com.android.settings.GESTURE_NAVIGATION_SETTINGS"
private const val SETTINGS_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key"
private const val UNIHERTZ_HIDE_IME_CAPTION_BAR_KEY = "agui_hide_ime_caption_bar"

class TutorialActivity : LocalizedComponentActivity() {
    companion object {
        const val EXTRA_UPDATE_TUTORIAL = "it.palsoftware.pastiera.UPDATE_TUTORIAL"
        const val EXTRA_PREVIEW_UPDATE_TUTORIAL = "it.palsoftware.pastiera.PREVIEW_UPDATE_TUTORIAL"
        const val EXTRA_PREVIOUS_VERSION = "it.palsoftware.pastiera.PREVIOUS_VERSION"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val updateTutorial = intent.getBooleanExtra(EXTRA_UPDATE_TUTORIAL, false)
        val previewUpdateTutorial = intent.getBooleanExtra(EXTRA_PREVIEW_UPDATE_TUTORIAL, false)
        val previousVersionOverride = intent.getStringExtra(EXTRA_PREVIOUS_VERSION)
        setContent {
            PastieraTheme {
                TutorialScreen(
                    updateTutorial = updateTutorial,
                    previousVersionOverride = previousVersionOverride,
                    onComplete = {
                        SettingsManager.setTutorialCompleted(this@TutorialActivity)
                        if (updateTutorial && !previewUpdateTutorial) {
                            SettingsManager.markWhatsNewSeen(this@TutorialActivity, BuildConfig.VERSION_NAME)
                        }
                        val intent = Intent(this@TutorialActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

sealed class TutorialPageType {
    data class WhatsNew(
        val summary: ReleaseNotesSummary,
        val previousVersion: String?,
        val currentVersion: String
    ) : TutorialPageType()

    data class Welcome(
        val title: String,
        val description: String,
        @DrawableRes val imageRes: Int
    ) : TutorialPageType()
    
    data class Standard(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val iconTint: Color
    ) : TutorialPageType()

    data class NavMode(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val iconTint: Color
    ) : TutorialPageType()

    data class Customization(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val iconTint: Color
    ) : TutorialPageType()

    object QuickLauncher : TutorialPageType()

    object MessengerPresets : TutorialPageType()

    object FeatureStatuses : TutorialPageType()
    
    data class EnablePastiera(
        val title: String,
        val description: String
    ) : TutorialPageType()
    
    data class SelectPastiera(
        val title: String,
        val description: String
    ) : TutorialPageType()

    data class ImeCaptionBar(
        val title: String,
        val description: String
    ) : TutorialPageType()

    data class SoftwareKeyboard(
        val title: String,
        val description: String,
        val icon: ImageVector,
        val iconTint: Color
    ) : TutorialPageType()

    data class LedIndicator(
        val title: String,
        val description: String,
        val iconTint: Color
    ) : TutorialPageType()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(
    updateTutorial: Boolean = false,
    previousVersionOverride: String? = null,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val releaseNotesLanguageTag = context.resources.configuration.locales[0]?.toLanguageTag()
        ?: Locale.getDefault().toLanguageTag()
    val lastSeenWhatsNewVersion = remember {
        SettingsManager.getLastSeenWhatsNewVersion(context)
    }
    var releaseNotes by remember {
        mutableStateOf(ReleaseNotesSummary.fallback(BuildConfig.VERSION_NAME, releaseNotesLanguageTag))
    }

    LaunchedEffect(updateTutorial) {
        if (updateTutorial) {
            fetchReleaseNotesForVersion(
                version = BuildConfig.VERSION_NAME,
                languageTag = releaseNotesLanguageTag
            ) { summary ->
                if (summary != null && summary.highlights.isNotEmpty()) {
                    releaseNotes = summary
                }
            }
        }
    }

    // Check IME status
    var isPastieraEnabled by remember { mutableStateOf(false) }
    var isPastieraSelected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        checkImeStatus(context) { enabled, selected ->
            isPastieraEnabled = enabled
            isPastieraSelected = selected
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            checkImeStatus(context) { enabled, selected ->
                isPastieraEnabled = enabled
                isPastieraSelected = selected
            }
        }
    }

    val pages = buildList {
        if (updateTutorial) {
            add(
                TutorialPageType.WhatsNew(
                    summary = releaseNotes,
                    previousVersion = previousVersionOverride ?: lastSeenWhatsNewVersion,
                    currentVersion = BuildConfig.VERSION_NAME
                )
            )
        }
        add(
            TutorialPageType.Welcome(
                title = stringResource(R.string.tutorial_page_welcome_title),
                description = stringResource(R.string.tutorial_page_welcome_description),
                imageRes = R.drawable.tutorial_welcome
            )
        )
        if (!isPastieraEnabled) {
            add(
                TutorialPageType.EnablePastiera(
                    title = stringResource(R.string.tutorial_page_enable_title),
                    description = stringResource(R.string.tutorial_page_enable_description)
                )
            )
        }
        if (!isPastieraSelected) {
            add(
                TutorialPageType.SelectPastiera(
                    title = stringResource(R.string.tutorial_page_select_title),
                    description = stringResource(R.string.tutorial_page_select_description)
                )
            )
        }
        add(
            TutorialPageType.Customization(
                title = stringResource(R.string.tutorial_page_customization_title),
                description = stringResource(R.string.tutorial_page_customization_description),
                icon = Icons.Filled.Settings,
                iconTint = MaterialTheme.colorScheme.primary
            )
        )
        add(TutorialPageType.QuickLauncher)
        add(TutorialPageType.MessengerPresets)
        add(
            TutorialPageType.ImeCaptionBar(
                title = stringResource(R.string.tutorial_android16_ime_caption_title),
                description = stringResource(R.string.tutorial_android16_ime_caption_description)
            )
        )
        add(
            TutorialPageType.SoftwareKeyboard(
                title = stringResource(R.string.tutorial_page_software_keyboard_title),
                description = stringResource(R.string.tutorial_page_software_keyboard_description),
                icon = Icons.Filled.Keyboard,
                iconTint = MaterialTheme.colorScheme.primary
            )
        )
        add(
            TutorialPageType.NavMode(
                title = stringResource(R.string.tutorial_page_nav_mode_title),
                description = stringResource(R.string.tutorial_page_nav_mode_description),
                icon = Icons.Filled.Navigation,
                iconTint = MaterialTheme.colorScheme.tertiary
            )
        )
        add(
            TutorialPageType.LedIndicator(
                title = stringResource(R.string.tutorial_page_led_title),
                description = stringResource(R.string.tutorial_page_led_description),
                iconTint = MaterialTheme.colorScheme.secondary
            )
        )
        add(TutorialPageType.FeatureStatuses)
        add(
            TutorialPageType.Standard(
                title = stringResource(R.string.tutorial_page_ready_title),
                description = stringResource(R.string.tutorial_page_ready_description),
                icon = Icons.Filled.CheckCircle,
                iconTint = MaterialTheme.colorScheme.primary
            )
        )
    }
    
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    
    // Automatic update check at tutorial start (only once, respecting dismissed releases)
    if (shouldUseGithubUpdateChecks(context)) {
        LaunchedEffect(Unit) {
            checkForUpdate(
                context = context,
                currentVersion = BuildConfig.VERSION_NAME,
                releaseChannel = BuildConfig.RELEASE_CHANNEL,
                ignoreDismissedReleases = true
            ) { hasUpdate, latestVersion, downloadUrl, releasePageUrl ->
                if (hasUpdate && latestVersion != null) {
                    showUpdateDialog(context, latestVersion, downloadUrl, releasePageUrl)
                }
            }
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.dp)
                    .padding(horizontal = 12.dp)
            ) {
            }
            
            // Pager content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (val pageType = pages[page]) {
                        is TutorialPageType.WhatsNew -> {
                            TutorialWhatsNewPageContent(
                                page = pageType,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is TutorialPageType.Welcome -> {
                            TutorialWelcomePageContent(
                                page = pageType,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is TutorialPageType.Standard -> {
                            if (page == pages.lastIndex) {
                                TutorialReadyPageContent(
                                    page = pageType,
                                    onComplete = onComplete,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                TutorialStandardPageContent(
                                    page = pageType,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        is TutorialPageType.NavMode -> {
                            TutorialNavModePageContent(
                                page = pageType,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is TutorialPageType.Customization -> {
                            TutorialCustomizationPageContent(
                                page = pageType,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        TutorialPageType.QuickLauncher -> {
                            TutorialQuickLauncherPageContent(modifier = Modifier.fillMaxSize())
                        }
                        TutorialPageType.MessengerPresets -> {
                            TutorialMessengerPresetsPageContent(modifier = Modifier.fillMaxSize())
                        }
                        TutorialPageType.FeatureStatuses -> {
                            TutorialFeatureStatusesPageContent(modifier = Modifier.fillMaxSize())
                        }
                        is TutorialPageType.EnablePastiera -> {
                            TutorialEnablePastieraPageContent(
                                page = pageType,
                                isPastieraEnabled = isPastieraEnabled,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is TutorialPageType.SelectPastiera -> {
                            TutorialSelectPastieraPageContent(
                                page = pageType,
                                isPastieraEnabled = isPastieraEnabled,
                                isPastieraSelected = isPastieraSelected,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is TutorialPageType.ImeCaptionBar -> {
                            TutorialImeCaptionBarPageContent(
                                page = pageType,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is TutorialPageType.SoftwareKeyboard -> {
                            TutorialSoftwareKeyboardPageContent(
                                page = pageType,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is TutorialPageType.LedIndicator -> {
                            TutorialLedIndicatorPageContent(
                                page = pageType,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                if (pagerState.currentPage < pages.lastIndex) {
                    FilledTonalButton(
                        onClick = onComplete,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = 12.dp)
                            .height(32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tutorial_skip),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (
                    BuildConfig.RELEASE_CHANNEL == "nightly" &&
                    !updateTutorial &&
                    pagerState.currentPage == 0
                ) {
                    Button(
                        onClick = {
                            applyDevsChoiceSettings(context)
                            Toast.makeText(
                                context,
                                R.string.tutorial_devs_choice_applied,
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 12.dp, top = 12.dp)
                            .height(32.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tutorial_devs_choice_button),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        pagerState.currentPage - 1,
                                        animationSpec = tween(300)
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.tutorial_previous),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.tutorial_previous), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.forEachIndexed { index, _ ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    }
                                )
                                .padding(if (isSelected) 0.dp else 1.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimary)
                                )
                            }
                        }
                        if (index < pages.size - 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                }
                
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (pagerState.currentPage < pages.size - 1) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        pagerState.currentPage + 1,
                                        animationSpec = tween(300)
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(stringResource(R.string.tutorial_next), style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.tutorial_next),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialSoftwareKeyboardPageContent(
    page: TutorialPageType.SoftwareKeyboard,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf(SettingsManager.getSoftwareKeyboardMode(context)) }
    val autoDetection = remember(selectedMode) { resolveSoftwareKeyboardAutoDetection(context, selectedMode) }

    val label = when (selectedMode) {
        SettingsManager.SoftwareKeyboardMode.AUTO -> stringResource(R.string.software_keyboard_mode_auto_short)
        SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL -> stringResource(R.string.software_keyboard_mode_always_virtual)
        SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE -> stringResource(R.string.software_keyboard_mode_always_hardware)
    }

    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        centered = true,
        descriptionLeftAligned = true,
        iconContent = {
            TutorialIconSurface(
                icon = page.icon,
                tint = page.iconTint
            )
        }
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Science,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.tutorial_software_keyboard_alpha_warning),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TutorialKeyboardModeInfoRow(
                title = stringResource(R.string.software_keyboard_mode_auto_short),
                description = stringResource(R.string.tutorial_software_keyboard_auto_bullet),
                icon = Icons.Filled.AutoMode
            )
            TutorialKeyboardModeInfoRow(
                title = stringResource(R.string.software_keyboard_mode_always_hardware),
                description = stringResource(R.string.tutorial_software_keyboard_hardware_bullet),
                icon = Icons.Filled.Keyboard
            )
            TutorialKeyboardModeInfoRow(
                title = stringResource(R.string.software_keyboard_mode_always_virtual),
                description = stringResource(R.string.tutorial_software_keyboard_virtual_bullet),
                icon = Icons.Filled.TabletAndroid
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.software_keyboard_mode_title)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                listOf(
                    SettingsManager.SoftwareKeyboardMode.AUTO to stringResource(R.string.software_keyboard_mode_auto_short),
                    SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL to stringResource(R.string.software_keyboard_mode_always_virtual),
                    SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE to stringResource(R.string.software_keyboard_mode_always_hardware)
                ).forEach { (mode, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            SettingsManager.setSoftwareKeyboardMode(context, mode)
                            selectedMode = mode
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        SoftwareKeyboardAutoDetectionCard(autoDetection)
    }
}

@Composable
private fun TutorialKeyboardModeInfoRow(
    title: String,
    description: String,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TutorialImeCaptionBarPageContent(
    page: TutorialPageType.ImeCaptionBar,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imeCaptionBarChecks = remember { resolveImeCaptionBarChecks(context) }
    val missingRequirement = imeCaptionBarChecks.firstOrNull { !it.isMet }
    val canOpenDirectly = missingRequirement == null

    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        centered = true,
        descriptionLeftAligned = true,
        iconContent = {
            TutorialIconSurface(
                icon = Icons.Filled.Keyboard,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImeCaptionBarPreviewImage(
                        imageRes = R.drawable.tutorial_titan2_ime_caption_bar_with,
                        label = stringResource(R.string.tutorial_android16_ime_caption_before)
                    )
                    ImeCaptionBarPreviewImage(
                        imageRes = R.drawable.tutorial_titan2_ime_caption_bar_without,
                        label = stringResource(R.string.tutorial_android16_ime_caption_after)
                    )
                }

                if (missingRequirement != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(
                                R.string.tutorial_android16_ime_caption_missing_requirement,
                                stringResource(missingRequirement.labelRes)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                        )
                    }
                }

                Button(
                    onClick = {
                        if (canOpenDirectly) {
                            openImeCaptionBarSettings(context)
                        } else {
                            Toast.makeText(
                                context,
                                R.string.tutorial_android16_ime_caption_vendor_toast,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (canOpenDirectly) {
                            Icons.AutoMirrored.Filled.ArrowForward
                        } else {
                            Icons.Filled.Info
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (canOpenDirectly) {
                                R.string.tutorial_android16_ime_caption_button
                            } else {
                                R.string.tutorial_android16_ime_caption_unavailable_button
                            }
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SoftwareKeyboardAutoDetectionCard(
    detection: SoftwareKeyboardAutoDetection
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Rule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.tutorial_software_keyboard_auto_checker_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            RequirementRow(
                label = detection.modeLabel,
                isMet = true
            )
            RequirementRow(
                label = detection.profileLabel,
                isMet = detection.isPhysicalKeyboardProfile
            )
            Text(
                text = detection.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ImeCaptionBarPreviewImage(
    @DrawableRes imageRes: Int,
    label: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = label,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.FillWidth
        )
    }
}

@Composable
private fun RequirementRow(
    label: String,
    isMet: Boolean
) {
    val tint = if (isMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val icon = if (isMet) Icons.Filled.CheckCircle else Icons.Filled.Cancel
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class ImeCaptionBarRequirement(
    val labelRes: Int,
    val isMet: Boolean
)

private data class SoftwareKeyboardAutoDetection(
    val modeLabel: String,
    val profileLabel: String,
    val reason: String,
    val isPhysicalKeyboardProfile: Boolean
)

private fun resolveSoftwareKeyboardAutoDetection(
    context: Context,
    configuredMode: SettingsManager.SoftwareKeyboardMode
): SoftwareKeyboardAutoDetection {
    val profileOverride = SettingsManager.getPhysicalKeyboardProfileOverride(context)
    val physicalProfile = DeviceSpecific.physicalKeyboardName()
    val keyboardFamily = DeviceSpecific.keyboardName()
    val isPhysicalKeyboardProfile = DeviceSpecific.isPhysicalKeyboardDevice(profileOverride)
    val autoMode = SoftwareKeyboardAutoDetector.resolve(context)
    val effectiveMode = if (configuredMode == SettingsManager.SoftwareKeyboardMode.AUTO) {
        autoMode
    } else {
        configuredMode
    }
    val effectiveLabel = context.softwareKeyboardModeLabel(effectiveMode)
    val autoLabel = context.softwareKeyboardModeLabel(autoMode)
    val modeLabel = if (configuredMode == SettingsManager.SoftwareKeyboardMode.AUTO) {
        context.getString(R.string.tutorial_software_keyboard_auto_selected, autoLabel)
    } else {
        context.getString(
            R.string.tutorial_software_keyboard_auto_overridden,
            effectiveLabel,
            autoLabel
        )
    }
    val profileLabel = context.getString(
        R.string.tutorial_software_keyboard_auto_profile,
        keyboardFamily,
        physicalProfile
    )
    val reason = if (isPhysicalKeyboardProfile) {
        context.getString(R.string.tutorial_software_keyboard_auto_reason_physical)
    } else {
        context.getString(R.string.tutorial_software_keyboard_auto_reason_touch)
    }
    return SoftwareKeyboardAutoDetection(
        modeLabel = modeLabel,
        profileLabel = profileLabel,
        reason = reason,
        isPhysicalKeyboardProfile = isPhysicalKeyboardProfile
    )
}

private fun Context.softwareKeyboardModeLabel(mode: SettingsManager.SoftwareKeyboardMode): String {
    return when (mode) {
        SettingsManager.SoftwareKeyboardMode.AUTO -> getString(R.string.software_keyboard_mode_auto_short)
        SettingsManager.SoftwareKeyboardMode.FORCE_VIRTUAL -> getString(R.string.software_keyboard_mode_always_virtual)
        SettingsManager.SoftwareKeyboardMode.FORCE_HARDWARE -> getString(R.string.software_keyboard_mode_always_hardware)
    }
}

private fun resolveImeCaptionBarChecks(context: Context): List<ImeCaptionBarRequirement> {
    return listOf(
        ImeCaptionBarRequirement(
            labelRes = R.string.tutorial_android16_ime_caption_check_android16,
            isMet = Build.VERSION.SDK_INT >= 36
        ),
        ImeCaptionBarRequirement(
            labelRes = R.string.tutorial_android16_ime_caption_check_titan2,
            isMet = DeviceSpecific.isTitan2Device()
        ),
        ImeCaptionBarRequirement(
            labelRes = R.string.tutorial_android16_ime_caption_check_setting,
            isMet = buildImeCaptionBarSettingsIntent().resolveActivity(context.packageManager) != null
        )
    )
}

private fun openImeCaptionBarSettings(context: Context) {
    val intent = buildImeCaptionBarSettingsIntent()
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            R.string.tutorial_android16_ime_caption_fallback,
            Toast.LENGTH_LONG
        ).show()
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun buildImeCaptionBarSettingsIntent(): Intent {
    return Intent(ACTION_UNIHERTZ_GESTURE_NAVIGATION_SETTINGS).apply {
        addCategory(Intent.CATEGORY_DEFAULT)
        putExtra(SETTINGS_FRAGMENT_ARGS_KEY, UNIHERTZ_HIDE_IME_CAPTION_BAR_KEY)
    }
}

private val TutorialIconSurfaceSize = 82.dp
private val TutorialIconSize = 38.dp
private val TutorialWelcomeImageSize = 240.dp

@Composable
private fun TutorialPageLayout(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    centered: Boolean = true,
    descriptionLeftAligned: Boolean = false,
    iconContent: (@Composable () -> Unit)?,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        if (iconContent != null) {
            TutorialPageHeader(
                title = title,
                centered = centered,
                iconContent = iconContent
            )
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = if (descriptionLeftAligned) TextAlign.Start else if (centered) TextAlign.Center else TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
            modifier = Modifier.fillMaxWidth()
        )
        
        content()
    }
}

@Composable
private fun TutorialPageHeader(
    title: String,
    centered: Boolean,
    iconContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        iconContent()
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.wrapContentWidth()
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun TutorialIconSurface(
    icon: ImageVector,
    tint: Color
) {
    Surface(
        modifier = Modifier.size(TutorialIconSurfaceSize),
        shape = CircleShape,
        color = tint.copy(alpha = 0.1f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(TutorialIconSize),
                tint = tint
            )
        }
    }
}

@Composable
fun TutorialWhatsNewPageContent(
    page: TutorialPageType.WhatsNew,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var bugFixesExpanded by remember { mutableStateOf(false) }
    val releaseRange = remember(page.previousVersion, page.currentVersion) {
        buildReleaseRangeLabel(page.previousVersion, page.currentVersion)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TutorialIconSurface(
            icon = Icons.Filled.AutoAwesome,
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.tutorial_whats_new_title, releaseRange),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.tutorial_whats_new_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f
        )

        Spacer(modifier = Modifier.height(16.dp))

        page.summary.highlights.forEach { highlight ->
            ReleaseNoteRow(
                text = highlight,
                icon = Icons.Filled.Star,
                tint = MaterialTheme.colorScheme.primary,
                prominent = true
            )
        }

        if (page.summary.improvements.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            page.summary.improvements.forEach { improvement ->
                ReleaseNoteRow(
                    text = improvement,
                    icon = Icons.Filled.CheckCircle,
                    tint = MaterialTheme.colorScheme.secondary,
                    prominent = false
                )
            }
        }

        if (page.summary.bugFixes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { bugFixesExpanded = !bugFixesExpanded },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.tutorial_whats_new_bug_fixes_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (bugFixesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (bugFixesExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                page.summary.bugFixes.forEach { bugFix ->
                    ReleaseNoteRow(
                        text = bugFix,
                        icon = Icons.Filled.BugReport,
                        tint = MaterialTheme.colorScheme.tertiary,
                        prominent = false
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(page.summary.docsUrl)))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tutorial_whats_new_docs_button))
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.tutorial_whats_new_continue_hint),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ReleaseNoteRow(
    text: String,
    icon: ImageVector,
    tint: Color,
    prominent: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (prominent) 6.dp else 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(if (prominent) 18.dp else 16.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = if (prominent) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (prominent) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun buildReleaseRangeLabel(previousVersion: String?, currentVersion: String): String {
    val normalizedCurrent = currentVersion.trim().ifBlank { "current" }
    val normalizedPrevious = previousVersion
        ?.trim()
        ?.takeIf { it.isNotBlank() && it != normalizedCurrent }
    return if (normalizedPrevious != null) {
        "$normalizedPrevious → $normalizedCurrent"
    } else {
        normalizedCurrent
    }
}

@Composable
fun TutorialWelcomePageContent(
    page: TutorialPageType.Welcome,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = page.imageRes),
                contentDescription = null,
                modifier = Modifier
                    .size(TutorialWelcomeImageSize)
                    .clip(RoundedCornerShape(28.dp)),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(1.dp))
        
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f
        )
    }
}

@Composable
fun TutorialQuickLauncherPageContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var spaceShortcut by remember {
        mutableStateOf(SettingsManager.getLauncherShortcut(context, KeyEvent.KEYCODE_SPACE))
    }
    val blocksSymSpace = shouldShowQuickLauncherMappingConflict(context)
    TutorialFeaturePageContent(
        title = stringResource(R.string.tutorial_quick_launcher_title),
        description = stringResource(R.string.tutorial_quick_launcher_description),
        icon = Icons.AutoMirrored.Filled.ManageSearch,
        tint = MaterialTheme.colorScheme.primary,
        bullets = listOf(
            stringResource(R.string.tutorial_quick_launcher_bullet_sym_space),
            stringResource(R.string.tutorial_quick_launcher_bullet_text_fields),
            stringResource(R.string.tutorial_quick_launcher_bullet_search),
            stringResource(R.string.tutorial_quick_launcher_bullet_layout)
        ),
        buttonText = stringResource(R.string.tutorial_quick_launcher_button),
        onButtonClick = { openCustomizationDestination(context, SettingsActivity.CUSTOMIZATION_DESTINATION_LAUNCHER_SHORTCUTS) },
        modifier = modifier
    ) {
        if (blocksSymSpace) {
            Spacer(modifier = Modifier.height(12.dp))
            QuickLauncherMappingConflictCard(
                currentMappingLabel = launcherShortcutLabel(spaceShortcut),
                onReplaceClick = {
                    SettingsManager.setLauncherShortcutsEnabled(context, true)
                    SettingsManager.setQuickLauncherTextFieldShortcuts(context, true)
                    SettingsManager.setQuickLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
                    spaceShortcut = SettingsManager.getLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
                }
            )
        }
    }
}

internal fun shouldShowQuickLauncherMappingConflict(context: Context): Boolean {
    val spaceShortcut = SettingsManager.getLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
    return spaceShortcut != null && !SettingsManager.isQuickLauncherShortcut(context, KeyEvent.KEYCODE_SPACE)
}

@Composable
fun TutorialMessengerPresetsPageContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    TutorialFeaturePageContent(
        title = stringResource(R.string.tutorial_messenger_presets_title),
        description = stringResource(R.string.tutorial_messenger_presets_description),
        icon = Icons.AutoMirrored.Filled.Send,
        tint = MaterialTheme.colorScheme.tertiary,
        bullets = listOf(
            stringResource(R.string.tutorial_messenger_presets_bullet_enter),
            stringResource(R.string.tutorial_messenger_presets_bullet_sym_enter),
            stringResource(R.string.tutorial_messenger_presets_bullet_apps)
        ),
        buttonText = stringResource(R.string.tutorial_messenger_presets_button),
        onButtonClick = { openCustomizationDestination(context, SettingsActivity.CUSTOMIZATION_DESTINATION_APP_ENTER_BEHAVIOR) },
        modifier = modifier
    )
}

@Composable
private fun TutorialFeaturePageContent(
    title: String,
    description: String,
    icon: ImageVector,
    tint: Color,
    bullets: List<String>,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraContent: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TutorialPageHeader(
            title = title,
            centered = true,
            iconContent = {
                TutorialIconSurface(icon = icon, tint = tint)
            }
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            bullets.forEach { bullet ->
                TutorialBulletRow(text = bullet)
            }
        }
        extraContent()
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onButtonClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}

@Composable
private fun QuickLauncherMappingConflictCard(
    currentMappingLabel: String,
    onReplaceClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.tutorial_quick_launcher_mapping_conflict_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(
                    R.string.tutorial_quick_launcher_mapping_conflict_description,
                    currentMappingLabel
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = onReplaceClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.tutorial_quick_launcher_mapping_conflict_button))
            }
        }
    }
}

@Composable
private fun TutorialBulletRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun openCustomizationDestination(context: Context, destination: String) {
    val intent = Intent(context, SettingsActivity::class.java).apply {
        putExtra(SettingsActivity.EXTRA_DESTINATION, SettingsActivity.DESTINATION_CUSTOMIZATION)
        putExtra(SettingsActivity.EXTRA_CUSTOMIZATION_DESTINATION, destination)
    }
    context.startActivity(intent)
}

private fun launcherShortcutLabel(shortcut: SettingsManager.LauncherShortcut?): String {
    return when (shortcut?.type) {
        SettingsManager.LauncherShortcut.TYPE_APP -> shortcut.appName ?: shortcut.packageName ?: "app shortcut"
        SettingsManager.LauncherShortcut.TYPE_SHORTCUT -> shortcut.action ?: "shortcut"
        SettingsManager.LauncherShortcut.TYPE_QUICK_LAUNCHER -> "QuickLauncher"
        null -> "none"
        else -> shortcut.action ?: shortcut.appName ?: shortcut.type
    }
}

internal fun applyDevsChoiceSettings(context: Context) {
    SettingsManager.setAppLanguageTag(context, null)
    SettingsManager.setPhysicalKeyboardProfileOverride(context, "auto")
    SettingsManager.setSoftwareKeyboardMode(context, SettingsManager.SoftwareKeyboardMode.AUTO)
    SettingsManager.setLongPressModifier(context, "variations")
    SettingsManager.setLongPressThreshold(context, 200L)
    SettingsManager.setTrackpadGesturesEnabled(context, true)
    SettingsManager.setKeyboardLayoutAutoByLocale(context, false)
    SettingsManager.setKeyboardLayout(context, "qwertz")
    SettingsManager.setKeyboardLayoutList(context, listOf("qwertz"))
    SettingsManager.setStaticVariationBarPreset(context, SettingsManager.STATIC_VARIATION_PRESET_DEV_CHOICE)
    val activeVariations = VariationRepository.loadVariations(
        assets = context.assets,
        context = context
    ).mapKeys { it.key.toString() }
    SettingsManager.saveVariations(
        context = context,
        variations = activeVariations,
        staticVariations = SettingsManager.getDevChoiceStaticVariationBasePreset(),
        staticVariationsShift = SettingsManager.getDevChoiceStaticVariationBasePreset(),
        staticVariationsAlt = SettingsManager.getDevChoiceStaticVariationBasePreset()
    )
}

@Composable
fun TutorialStandardPageContent(
    page: TutorialPageType.Standard,
    modifier: Modifier = Modifier
) {
    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        iconContent = {
            TutorialIconSurface(
                icon = page.icon,
                tint = page.iconTint
            )
        }
    )
}

@Composable
fun TutorialLedIndicatorPageContent(
    page: TutorialPageType.LedIndicator,
    modifier: Modifier = Modifier
) {
    var enabled by remember { mutableStateOf(false) }
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (enabled) 0.24f else 0.1f,
        animationSpec = tween(220),
        label = "ledIndicatorBackground"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (enabled) 1.12f else 1f,
        animationSpec = tween(220),
        label = "ledIndicatorScale"
    )
    val tint = if (enabled) MaterialTheme.colorScheme.primary else page.iconTint

    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        iconContent = {
            Surface(
                modifier = Modifier
                    .size(TutorialIconSurfaceSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { enabled = !enabled },
                shape = CircleShape,
                color = tint.copy(alpha = backgroundAlpha)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier
                            .size(TutorialIconSize)
                            .scale(iconScale),
                        tint = tint
                    )
                }
            }
        }
    )
}

@Composable
fun TutorialReadyPageContent(
    page: TutorialPageType.Standard,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var finishing by remember { mutableStateOf(false) }
    val contentOffset by animateDpAsState(
        targetValue = if (finishing) (-120).dp else 0.dp,
        animationSpec = tween(520),
        label = "readyContentOffset"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (finishing) 0f else 1f,
        animationSpec = tween(520),
        label = "readyContentAlpha"
    )
    val checkScale by animateFloatAsState(
        targetValue = if (finishing) 1.22f else 1f,
        animationSpec = tween(420),
        label = "readyCheckScale"
    )
    val checkBackgroundAlpha by animateFloatAsState(
        targetValue = if (finishing) 0.92f else 0.12f,
        animationSpec = tween(420),
        label = "readyCheckBackground"
    )

    LaunchedEffect(finishing) {
        if (finishing) {
            delay(620)
            onComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = contentOffset)
                .alpha(contentAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1.45f))
            Spacer(modifier = Modifier.height(52.dp))

            Surface(
                modifier = Modifier
                    .size(156.dp)
                    .scale(checkScale)
                    .clickable(
                        enabled = !finishing,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { finishing = true },
                shape = RoundedCornerShape(34.dp),
                color = page.iconTint.copy(alpha = checkBackgroundAlpha),
                contentColor = if (finishing) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    page.iconTint
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckBox,
                        contentDescription = stringResource(R.string.tutorial_finish),
                        modifier = Modifier.size(86.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.tutorial_finish),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.weight(0.65f))
        }
    }
}

@Composable
fun TutorialFeatureStatusesPageContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.tutorial_feature_status_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.tutorial_feature_status_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(28.dp))
        TutorialFeatureStatusCard(
            status = FeatureStatus.Construction,
            title = stringResource(R.string.tutorial_feature_status_construction_title),
            description = stringResource(R.string.tutorial_feature_status_construction_description)
        )
        Spacer(modifier = Modifier.height(14.dp))
        TutorialFeatureStatusCard(
            status = FeatureStatus.Experimental,
            title = stringResource(R.string.tutorial_feature_status_experimental_title),
            description = stringResource(R.string.tutorial_feature_status_experimental_description)
        )
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.tutorial_feature_status_footer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TutorialFeatureStatusCard(
    status: FeatureStatus,
    title: String,
    description: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = status.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(30.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TutorialNavModePageContent(
    page: TutorialPageType.NavMode,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var navModeCtrlHoldEnabled by remember {
        mutableStateOf(SettingsManager.getNavModeCtrlHoldEnabled(context))
    }

    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        centered = true,
        descriptionLeftAligned = true,
        iconContent = {
            TutorialIconSurface(
                icon = page.icon,
                tint = page.iconTint
            )
        }
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TutorialBulletRow(stringResource(R.string.tutorial_nav_mode_bullet_selection))
            TutorialBulletRow(stringResource(R.string.tutorial_nav_mode_bullet_words))
            TutorialBulletRow(stringResource(R.string.tutorial_nav_mode_bullet_media))
        }

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.nav_mode_ctrl_hold_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = stringResource(R.string.nav_mode_ctrl_hold_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Switch(
                    checked = navModeCtrlHoldEnabled,
                    onCheckedChange = { enabled ->
                        navModeCtrlHoldEnabled = enabled
                        SettingsManager.setNavModeCtrlHoldEnabled(context, enabled)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialCustomizationPageContent(
    page: TutorialPageType.Customization,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var appLanguageExpanded by remember { mutableStateOf(false) }
    var modifierExpanded by remember { mutableStateOf(false) }
    var typingSoundsExpanded by remember { mutableStateOf(false) }
    var typingSoundExpanded by remember { mutableStateOf(false) }
    var typingSoundOutputExpanded by remember { mutableStateOf(false) }
    var selectedLanguageTag by remember { mutableStateOf(SettingsManager.getAppLanguageTag(context)) }
    var selectedLongPressModifier by remember { mutableStateOf(SettingsManager.getLongPressModifier(context)) }
    var typingSoundMode by remember { mutableStateOf(SettingsManager.getTypingSoundMode(context)) }
    var typingSoundOutputMode by remember { mutableStateOf(SettingsManager.getTypingSoundOutputMode(context)) }
    var demoText by remember { mutableStateOf("") }
    val typingSoundPlayer = remember { TypingSoundPlayer(context).apply { reload() } }

    val languageOptions = remember {
        listOf(null, "en", "it", "de", "es", "fr", "pl", "ru", "uk", "ko", "vi", "hy")
    }

    DisposableEffect(typingSoundPlayer) {
        onDispose { typingSoundPlayer.release() }
    }

    val selectedLanguageLabel = if (selectedLanguageTag == null) {
        getTutorialSystemDefaultLanguageLabel(context)
    } else {
        getTutorialLanguageOptionLabel(context, selectedLanguageTag!!)
    }

    val selectedLongPressModifierLabel = when (selectedLongPressModifier) {
        "shift" -> stringResource(R.string.long_press_modifier_shift)
        "variations" -> stringResource(R.string.long_press_modifier_variations)
        "sym" -> stringResource(R.string.long_press_modifier_sym)
        "sym_symbols" -> stringResource(R.string.long_press_modifier_sym_symbols)
        "sym_emoji" -> stringResource(R.string.long_press_modifier_sym_emoji)
        else -> stringResource(R.string.long_press_modifier_alt)
    }

    TutorialPageLayout(
        title = page.title,
        description = page.description,
        modifier = modifier,
        centered = true,
        descriptionLeftAligned = true,
        iconContent = {
            TutorialIconSurface(
                icon = page.icon,
                tint = page.iconTint
            )
        }
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = appLanguageExpanded,
            onExpandedChange = { appLanguageExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedLanguageLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.app_language_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = appLanguageExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = appLanguageExpanded,
                onDismissRequest = { appLanguageExpanded = false }
            ) {
                languageOptions.forEach { tag ->
                    val label = if (tag == null) {
                        getTutorialSystemDefaultLanguageLabel(context)
                    } else {
                        getTutorialLanguageOptionLabel(context, tag)
                    }
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            SettingsManager.setAppLanguageTag(context, tag)
                            selectedLanguageTag = tag
                            appLanguageExpanded = false
                            (context as? TutorialActivity)?.recreate()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = modifierExpanded,
            onExpandedChange = { modifierExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedLongPressModifierLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.long_press_modifier_title)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modifierExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )

            ExposedDropdownMenu(
                expanded = modifierExpanded,
                onDismissRequest = { modifierExpanded = false }
            ) {
                listOf(
                    "alt" to stringResource(R.string.long_press_modifier_alt),
                    "shift" to stringResource(R.string.long_press_modifier_shift),
                    "variations" to stringResource(R.string.long_press_modifier_variations),
                    "sym" to stringResource(R.string.long_press_modifier_sym),
                    "sym_symbols" to stringResource(R.string.long_press_modifier_sym_symbols),
                    "sym_emoji" to stringResource(R.string.long_press_modifier_sym_emoji)
                ).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            SettingsManager.setLongPressModifier(context, value)
                            selectedLongPressModifier = value
                            modifierExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.tutorial_device_sym_layer_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.tutorial_device_sym_layer_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TutorialCustomizationLinkButton(
                text = stringResource(R.string.tutorial_sym_emoji_button),
                icon = Icons.Filled.EmojiSymbols,
                onClick = { context.startActivity(Intent(context, SymCustomizationActivity::class.java)) },
                modifier = Modifier.weight(1f)
            )
            TutorialCustomizationLinkButton(
                text = stringResource(R.string.tutorial_variations_button),
                icon = Icons.Filled.Tune,
                onClick = { openCustomizationDestination(context, SettingsActivity.CUSTOMIZATION_DESTINATION_VARIATIONS) },
                modifier = Modifier.weight(1f)
            )
            TutorialCustomizationLinkButton(
                text = stringResource(R.string.tutorial_status_bar_button),
                icon = Icons.Filled.SmartButton,
                onClick = { openCustomizationDestination(context, SettingsActivity.CUSTOMIZATION_DESTINATION_STATUS_BAR_BUTTONS) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { typingSoundsExpanded = !typingSoundsExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.tutorial_typing_sound_demo_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (typingSoundsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (typingSoundsExpanded) {
                    Text(
                        text = stringResource(R.string.tutorial_typing_sound_demo_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    ExposedDropdownMenuBox(
                        expanded = typingSoundExpanded,
                        onExpandedChange = { typingSoundExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = tutorialTypingSoundModeLabel(typingSoundMode),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.typing_sound_title)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typingSoundExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = typingSoundExpanded,
                            onDismissRequest = { typingSoundExpanded = false }
                        ) {
                            listOf(
                                SettingsManager.TYPING_SOUND_MODE_OFF to stringResource(R.string.typing_sound_off),
                                SettingsManager.TYPING_SOUND_MODE_CLICK to stringResource(R.string.typing_sound_click),
                                SettingsManager.TYPING_SOUND_MODE_TYPEWRITER to stringResource(R.string.typing_sound_typewriter)
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        SettingsManager.setTypingSoundMode(context, value)
                                        typingSoundMode = value
                                        typingSoundPlayer.reload()
                                        typingSoundExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = typingSoundOutputExpanded,
                        onExpandedChange = { typingSoundOutputExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = tutorialTypingSoundOutputModeLabel(typingSoundOutputMode),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.typing_sound_output_title)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typingSoundOutputExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = typingSoundOutputExpanded,
                            onDismissRequest = { typingSoundOutputExpanded = false }
                        ) {
                            listOf(
                                SettingsManager.TYPING_SOUND_OUTPUT_MEDIA to stringResource(R.string.typing_sound_output_media),
                                SettingsManager.TYPING_SOUND_OUTPUT_SYSTEM to stringResource(R.string.typing_sound_output_system),
                                SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION to stringResource(R.string.typing_sound_output_notification)
                            ).forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        SettingsManager.setTypingSoundOutputMode(context, value)
                                        typingSoundOutputMode = value
                                        typingSoundPlayer.reload()
                                        typingSoundOutputExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = demoText,
                        onValueChange = { newValue ->
                            val keyCode = tutorialTypingSoundKeyCode(demoText, newValue)
                            demoText = newValue
                            if (keyCode != null) {
                                typingSoundPlayer.play(keyCode)
                            }
                        },
                        label = { Text(stringResource(R.string.tutorial_typing_sound_demo_input_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialCustomizationLinkButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
    }
}

@Composable
private fun tutorialTypingSoundModeLabel(mode: String): String {
    return when (mode) {
        SettingsManager.TYPING_SOUND_MODE_CLICK -> stringResource(R.string.typing_sound_click)
        SettingsManager.TYPING_SOUND_MODE_TYPEWRITER -> stringResource(R.string.typing_sound_typewriter)
        else -> stringResource(R.string.typing_sound_off)
    }
}

@Composable
private fun tutorialTypingSoundOutputModeLabel(mode: String): String {
    return when (mode) {
        SettingsManager.TYPING_SOUND_OUTPUT_SYSTEM -> stringResource(R.string.typing_sound_output_system)
        SettingsManager.TYPING_SOUND_OUTPUT_NOTIFICATION -> stringResource(R.string.typing_sound_output_notification)
        else -> stringResource(R.string.typing_sound_output_media)
    }
}

private fun tutorialTypingSoundKeyCode(oldValue: String, newValue: String): Int? {
    if (newValue.length < oldValue.length) {
        return KeyEvent.KEYCODE_DEL
    }
    val inserted = newValue.drop(oldValue.length).lastOrNull() ?: return null
    return when {
        inserted == ' ' -> KeyEvent.KEYCODE_SPACE
        inserted == '\n' -> KeyEvent.KEYCODE_ENTER
        inserted.lowercaseChar() in 'a'..'z' -> KeyEvent.KEYCODE_A + (inserted.lowercaseChar() - 'a')
        inserted in '0'..'9' -> KeyEvent.KEYCODE_0 + (inserted - '0')
        else -> KeyEvent.KEYCODE_UNKNOWN
    }
}

@Composable
fun TutorialEnablePastieraPageContent(
    page: TutorialPageType.EnablePastiera,
    isPastieraEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    TutorialActionPageLayout(
        title = page.title,
        description = page.description,
        icon = Icons.Filled.Settings,
        tint = MaterialTheme.colorScheme.primary,
        modifier = modifier,
        statusContent = {
            if (isPastieraEnabled) {
                TutorialStatusRow(
                    text = stringResource(R.string.tutorial_enabled_message),
                    icon = Icons.Filled.CheckCircle,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        actionContent = {
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tutorial_enable_button), style = MaterialTheme.typography.bodyMedium)
        }
        }
    )
}

@Composable
fun TutorialSelectPastieraPageContent(
    page: TutorialPageType.SelectPastiera,
    isPastieraEnabled: Boolean,
    isPastieraSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    TutorialActionPageLayout(
        title = page.title,
        description = page.description,
        icon = Icons.Filled.Keyboard,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = modifier,
        statusContent = {
            when {
                !isPastieraEnabled -> TutorialStatusRow(
                    text = stringResource(R.string.tutorial_enable_first_message),
                    icon = Icons.Filled.Warning,
                    tint = MaterialTheme.colorScheme.error
                )
                isPastieraSelected -> TutorialStatusRow(
                    text = stringResource(R.string.tutorial_selected_message),
                    icon = Icons.Filled.CheckCircle,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        actionContent = {
        Button(
            onClick = {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = isPastieraEnabled,
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Keyboard,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.tutorial_select_button), style = MaterialTheme.typography.bodyMedium)
        }
        }
    )
}

@Composable
private fun TutorialActionPageLayout(
    title: String,
    description: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    statusContent: @Composable ColumnScope.() -> Unit = {},
    actionContent: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TutorialPageHeader(
            title = title,
            centered = true,
            iconContent = { TutorialIconSurface(icon = icon, tint = tint) }
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(14.dp))
        statusContent()
        Spacer(modifier = Modifier.weight(1f))
        actionContent()
    }
}

@Composable
private fun TutorialStatusRow(
    text: String,
    icon: ImageVector,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Checks if Pastiera IME is enabled and selected.
 */
private fun checkImeStatus(
    context: Context,
    callback: (enabled: Boolean, selected: Boolean) -> Unit
) {
    try {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pastieraPackageName = ImeIdentity.packageName
        val pastieraImeId = ImeIdentity.imeId
        
        val enabledInputMethods = imm.enabledInputMethodList
        val isEnabled = enabledInputMethods.any { inputMethodInfo ->
            inputMethodInfo.packageName == pastieraPackageName ||
            ImeIdentity.matchesImeId(inputMethodInfo.id)
        }
        
        var isSelected = false
        if (isEnabled) {
            try {
                val defaultInputMethod = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD
                ) ?: ""
                isSelected = ImeIdentity.matchesImeId(defaultInputMethod)
            } catch (e: SecurityException) {
                try {
                    val currentSubtype = imm.currentInputMethodSubtype
                    if (currentSubtype != null) {
                        val allInputMethods = imm.inputMethodList
                        val pastieraInputMethod = allInputMethods.find { 
                            it.packageName == pastieraPackageName || ImeIdentity.matchesImeId(it.id)
                        }
                        if (pastieraInputMethod != null && enabledInputMethods.size == 1) {
                            isSelected = true
                        } else {
                            isSelected = false
                        }
                    } else {
                        isSelected = false
                    }
                } catch (e2: Exception) {
                    isSelected = false
                }
            } catch (e: Exception) {
                isSelected = false
            }
        }
        
        callback(isEnabled, isSelected)
    } catch (e: Exception) {
        android.util.Log.e("TutorialActivity", "Error checking IME status", e)
        callback(false, false)
    }
}

private fun getTutorialLanguageOptionLabel(context: Context, languageTag: String): String {
    return try {
        val languageLocale = Locale.forLanguageTag(languageTag)
        val uiLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val nativeName = languageLocale.getDisplayLanguage(languageLocale)
        val uiName = languageLocale.getDisplayLanguage(uiLocale)
        if (nativeName.equals(uiName, ignoreCase = true)) nativeName else "$nativeName - $uiName"
    } catch (_: Exception) {
        languageTag
    }
}

private fun getTutorialSystemDefaultLanguageLabel(context: Context): String {
    val systemLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        android.content.res.Resources.getSystem().configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        android.content.res.Resources.getSystem().configuration.locale
    }
    val detected = getTutorialLanguageOptionLabel(context, systemLocale.toLanguageTag())
    return context.getString(R.string.app_language_system_default_with_detected, detected)
}
