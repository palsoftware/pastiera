package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.EditText
import android.widget.TextView
import android.widget.PopupWindow
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.content.res.Configuration
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import it.palsoftware.pastiera.data.emoji.EmojiRepository
import it.palsoftware.pastiera.data.emoji.RecentEmojiManager
import it.palsoftware.pastiera.data.emoji.EmojiSearchRepository
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Emoji picker view: single vertical list with section headers and bottom tabs.
 */
class EmojiPickerView(
    context: Context,
    private val onCloseRequested: (() -> Unit)? = null
) : FrameLayout(context) {

    private var currentInputConnection: InputConnection? = null
    private val recyclerView: RecyclerView
    private val searchField: EditText
    private val loadingView: ProgressBar
    private val emptyView: TextView
    private val tabScrollView: HorizontalScrollView
    private val tabRow: LinearLayout
    private val vertical: LinearLayout
    private val keyboardSwitcherButton: ImageView
    private val searchPanel: FrameLayout
    private val searchToggleButton: ImageView
    private val closeButton: ImageView
    private var roundedControls = false
    private var roundedIconSize = 0f
    val edgeControls: Pair<View, View> get() = searchToggleButton to closeButton

    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loadingJob: Job? = null

    private val compactHeight = dpToPx(COMPACT_HEIGHT_DP)
    private val emojiSize = dpToPx(48f)
    private val spacing = dpToPx(4f)
    private val smallPadding = dpToPx(8f)
    private val recentsApplyTopThreshold = 0

    // Data for sections
    private var sectionItems: List<SectionItem> = emptyList()
    private var itemCategoryIds: List<String> = emptyList()
    private var headerPositions: Map<String, Int> = emptyMap()
    private var selectedCategoryId: String? = null
    private var isTabClickScroll = false
    private var pendingRecentsRefresh = false
    private var pendingRecentsRefreshRequiresTop = false
    private var pendingRecentsRefreshRequiresNotRecents = false
    private var scrollState = RecyclerView.SCROLL_STATE_IDLE

    // Adapter
    private val sectionAdapter: SectionAdapter
    private val searchAdapter: SearchAdapter
    private val columns: Int
    private var regularCategories: List<EmojiRepository.EmojiCategory> = emptyList()
    private var searchIndex: EmojiSearchRepository.EmojiSearchIndex? = null
    private var searchQuery: String = ""
    private var searchJob: Job? = null
    private var isSearchMode: Boolean = false
    private var isSearchPanelVisible: Boolean = false
    private var searchInputCaptureEnabled: Boolean = true
    private var containerReordering: Boolean = false
    private var pendingSearchReplacementRange: IntRange? = null
    private var tabCategoryIds: List<String> = emptyList()
    private var lastSearchResults: List<EmojiSearchRepository.EmojiSearchResult> = emptyList()
    var onSearchPanelVisibilityChanged: ((Boolean) -> Unit)? = null
    var themeOverride: KeyboardThemeColors? = null
        set(value) {
            if (field == value) {
                return
            }
            field = value
            applyTheme()
        }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setPadding(0, 0, 0, 0)

        // Calculate columns based on screen width
        val screenWidth = context.resources.displayMetrics.widthPixels
        val availableWidth = screenWidth - smallPadding * 2
        columns = ((availableWidth + spacing) / (emojiSize + spacing)).coerceAtLeast(4).coerceAtMost(10)

        // Layout container: vertical stack (recycler + bottom tabs)
        vertical = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, compactHeight)
        }

        searchField = EditText(context).apply {
            hint = context.getString(R.string.emoji_picker_search_placeholder)
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = createSearchFieldBackground()
            val padH = dpToPx(8f)
            val padV = dpToPx(5f)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(smallPadding, smallPadding, smallPadding, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                showSoftInputOnFocus = false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val newQuery = s?.toString().orEmpty()
                    if (newQuery == searchQuery) return
                    searchQuery = newQuery
                    scheduleSearch()
                }
            })
            setOnClickListener {
                setSearchInputCaptureEnabled(!searchInputCaptureEnabled)
            }
        }
        setSearchInputCaptureEnabled(false)

        searchPanel = FrameLayout(context).apply {
            visibility = View.GONE
            setBackgroundColor(themeOverride?.background ?: Color.rgb(24, 24, 24))
            val panelPadding = dpToPx(6f)
            setPadding(panelPadding, panelPadding, panelPadding, panelPadding)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            addView(searchField, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ))
        }

        closeButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            contentDescription = context.getString(R.string.close)
            background = createCloseButtonBackground()
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(4f)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(dpToPx(36f), dpToPx(32f))
            setOnClickListener {
                onCloseRequested?.invoke()
            }
        }

        // RecyclerView with headers and emoji grid
        recyclerView = RecyclerView(context).apply {
            overScrollMode = View.OVER_SCROLL_ALWAYS
            setHasFixedSize(false)
            clipToPadding = false
            setPadding(smallPadding, smallPadding, smallPadding, smallPadding + dpToPx(44f))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Recents updates must appear silently ("as if the emoji was always there"),
            // so disable insert/change animations entirely.
            itemAnimator = null
        }

        val gridLayoutManager = GridLayoutManager(context, columns, RecyclerView.VERTICAL, false)
        sectionAdapter = SectionAdapter(columns)
        searchAdapter = SearchAdapter()
        gridLayoutManager.spanSizeLookup = sectionAdapter.spanSizeLookup
        recyclerView.layoutManager = gridLayoutManager
        recyclerView.adapter = sectionAdapter

        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val pos = parent.getChildAdapterPosition(view)
                if (pos == RecyclerView.NO_POSITION) return
                when (parent.adapter?.getItemViewType(pos) ?: return) {
                    VIEW_TYPE_HEADER -> {
                        outRect.set(0, spacing, 0, spacing)
                    }
                    VIEW_TYPE_EMOJI -> {
                        val layoutParams = view.layoutParams as? GridLayoutManager.LayoutParams
                        val column = layoutParams?.spanIndex ?: 0
                        outRect.left = if (column == 0) 0 else spacing / 2
                        outRect.right = if (column == columns - 1) 0 else spacing / 2
                        outRect.top = spacing / 2
                        outRect.bottom = spacing / 2
                    }
                }
            }
        })

        // Scroll listener to sync tabs and apply pending recents updates
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                scrollState = newState
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    isTabClickScroll = false
                    maybeApplyPendingRecentsRefresh()
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (isSearchMode) return
                if (isTabClickScroll) return
                val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (firstVisible == RecyclerView.NO_POSITION) return
                val categoryId = itemCategoryIds.getOrNull(firstVisible) ?: return
                if (categoryId != selectedCategoryId) {
                    selectedCategoryId = categoryId
                    updateTabsSelection()
                }
            }
        })

        // Loading and empty views (overlay)
        loadingView = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            visibility = View.VISIBLE
        }
        emptyView = TextView(context).apply {
            text = context.getString(R.string.emoji_picker_error)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        // Tabs at bottom (above LEDs) - full width, no scroll
        val tabHeight = dpToPx(32f) // Height cap
        searchToggleButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_search_24)
            contentDescription = context.getString(R.string.emoji_picker_search_label)
            background = createTabBackground(false)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(4f)
            setPadding(pad, pad, pad, pad)
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(tabHeight, tabHeight).apply {
                marginEnd = spacing
            }
            setOnClickListener {
                setSearchPanelVisible(!isSearchPanelVisible)
            }
        }
        tabRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tabHeight
            )
            setPadding(smallPadding / 2, 0, smallPadding / 2, 0)
        }
        // Keep tabScrollView reference for compatibility but use it as a simple wrapper
        tabScrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                tabHeight
            )
        }
        tabScrollView.addView(tabRow)
        keyboardSwitcherButton = ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            contentDescription = context.getString(R.string.close)
            background = createTabBackground(false)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val pad = dpToPx(4f)
            setPadding(pad, pad, pad, pad)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(tabHeight, tabHeight).apply {
                marginStart = spacing
            }
        }

        vertical.addView(
            FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
                addView(recyclerView)
                // Keep empty/error states inside the result area. A root-level MATCH_PARENT
                // overlay would hide the search field and bottom controls when no emoji matches.
                addView(emptyView)
                addView(searchPanel)
            }
        )
        vertical.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    tabHeight
                )
                addView(searchToggleButton)
                addView(keyboardSwitcherButton)
                addView(tabScrollView, LinearLayout.LayoutParams(0, tabHeight, 1f))
                addView(closeButton)
            }
        )

        addView(vertical)
        addView(loadingView)

        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            compactHeight
        )

        applyTheme()
        loadCategories()
    }

    fun setInputConnection(connection: InputConnection?) {
        currentInputConnection = connection
    }

    fun configureRoundedControls(enabled: Boolean, rowHeight: Int, iconSize: Float) {
        roundedControls = enabled
        roundedIconSize = iconSize
        // Reserve the slot; rounded mode uses the chrome's shared SYM close control.
        closeButton.visibility = if (enabled) View.INVISIBLE else View.VISIBLE
        val height = if (enabled) rowHeight else dpToPx(32f)
        val bar = closeButton.parent as LinearLayout
        bar.layoutParams = bar.layoutParams.apply { this.height = height }
        tabScrollView.layoutParams = tabScrollView.layoutParams.apply { this.height = height }
        tabRow.layoutParams = tabRow.layoutParams.apply { this.height = height }
        tabRow.setPadding(if (enabled) 0 else smallPadding / 2, 0, if (enabled) 0 else smallPadding / 2, 0)
        for (index in 0 until tabRow.childCount) {
            val category = tabRow.getChildAt(index)
            category.layoutParams = category.layoutParams.apply {
                this.height = if (enabled) ViewGroup.LayoutParams.MATCH_PARENT else dpToPx(32f)
            }
            (category as? ImageView)?.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        fun sizeControls(width: Int) {
            listOf(searchToggleButton, closeButton).forEach { button ->
                button.layoutParams = (button.layoutParams as LinearLayout.LayoutParams).apply {
                    this.width = if (enabled) {
                        if (button === closeButton) width - (width / 10) * 9 else width / 10
                    } else dpToPx(if (button === closeButton) 36f else 32f)
                    this.height = height
                    marginEnd = if (!enabled && button === searchToggleButton) spacing else 0
                }
            }
            applyEdgeControlAppearance()
        }
        sizeControls(width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels)
        requestLayout()
    }

    private fun applyEdgeControlAppearance() {
        listOf(searchToggleButton, closeButton).forEach { button ->
            if (roundedControls) {
                button.background = ColorDrawable(themeOverride?.statusBarButton ?: Color.TRANSPARENT)
                button.setPadding(0, 0, 0, 0)
                button.scaleType = ImageView.ScaleType.MATRIX
                button.drawable?.let { icon ->
                    val scale = roundedIconSize / icon.intrinsicHeight.coerceAtLeast(1)
                    button.imageMatrix = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            (button.layoutParams.width - icon.intrinsicWidth * scale) / 2f +
                                dpToPx(4f) * if (button === searchToggleButton) 1 else -1,
                            (button.layoutParams.height - icon.intrinsicHeight * scale) / 2f - dpToPx(2f)
                        )
                    }
                }
            } else {
                val pad = dpToPx(4f)
                button.setPadding(pad, pad, pad, pad)
                button.scaleType = ImageView.ScaleType.CENTER_INSIDE
                button.background = if (button === closeButton) createCloseButtonBackground() else createTabBackground(isSearchPanelVisible)
            }
        }
    }

    fun configureSoftwareKeyboardMode(heightPx: Int?, onKeyboardLayoutRequested: (() -> Unit)?) {
        val configuredHeight = configuredHeightPx(context)
        val targetHeight = heightPx?.takeIf { it > 0 } ?: configuredHeight
        updateHeight(targetHeight)
        keyboardSwitcherButton.visibility = if (onKeyboardLayoutRequested != null) View.VISIBLE else View.GONE
        keyboardSwitcherButton.setOnClickListener {
            onKeyboardLayoutRequested?.invoke()
        }
    }

    private fun updateHeight(heightPx: Int) {
        (layoutParams ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)).also {
            it.height = heightPx
            layoutParams = it
        }
        (vertical.layoutParams ?: LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)).also {
            it.height = heightPx
            vertical.layoutParams = it
        }
    }

    fun refresh() {
        loadCategories()
    }

    fun isSearchInputActive(): Boolean {
        return isSearchPanelVisible && searchInputCaptureEnabled
    }

    fun isSearchPanelShowing(): Boolean {
        return isSearchPanelVisible
    }

    /**
     * Routes on-screen keyboard text input into the emoji search field while the
     * search input capture is active (analogous to the hardware key path).
     */
    fun handleSearchTextInput(text: String): Boolean {
        if (!isSearchInputActive()) return false
        if (text.isNotEmpty()) {
            appendSearchText(text)
        }
        return true
    }

    /**
     * Routes on-screen keyboard backspace into the emoji search field while the
     * search input capture is active.
     */
    fun handleSearchBackspace(): Boolean {
        if (!isSearchInputActive()) return false
        deleteSearchTextBackwards()
        return true
    }

    /**
     * Commits the top emoji search result and closes the picker.
     * Stays neutral when the search input capture is inactive or there are no results.
     */
    fun commitTopSearchResultAndClose() {
        if (!isSearchInputActive()) return
        val top = lastSearchResults.firstOrNull() ?: return
        onEmojiSelected(top.entry.base, top.categoryId, closeAfterCommit = true)
    }

    private fun deleteSearchTextBackwards() {
        val text = searchField.text ?: return
        if (text.isEmpty()) return
        val replacementRange = selectedSearchRange(text.length)
        val start = replacementRange?.first
            ?: minOf(searchField.selectionStart, searchField.selectionEnd).coerceAtLeast(0)
        val end = replacementRange?.last?.plus(1)
            ?: maxOf(searchField.selectionStart, searchField.selectionEnd).coerceAtMost(text.length)
        if (start < end) {
            text.delete(start, end)
            pendingSearchReplacementRange = null
        } else {
            val cursor = searchField.selectionStart.coerceIn(0, text.length)
            if (cursor > 0) {
                text.delete(cursor - 1, cursor)
            }
        }
    }

    fun createSearchInputConnection(): InputConnection? {
        if (!isSearchInputActive()) return null
        focusSearchField()
        val baseConnection = searchField.onCreateInputConnection(EditorInfo()) ?: return null
        return object : InputConnectionWrapper(baseConnection, true) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                return handleSearchInputConnectionKeyEvent(event) || super.sendKeyEvent(event)
            }

            override fun performContextMenuAction(id: Int): Boolean {
                return searchField.onTextContextMenuItem(id) || super.performContextMenuAction(id)
            }
        }
    }

    /**
     * IME hardware keys do not automatically target this EditText.
     * Handle printable keys manually while emoji picker page is open.
     */
    fun handleSearchKeyDown(
        event: KeyEvent,
        ctrlActive: Boolean = event.isCtrlPressed,
        resolveTypedText: ((KeyEvent) -> String?)? = null
    ): Boolean {
        if (!isSearchPanelVisible) return false
        if (!searchInputCaptureEnabled) return false
        if (event.isAltPressed || event.isMetaPressed) return false
        if (ctrlActive) {
            focusSearchField()
            if (handleTextEditingCtrlShortcut(event.keyCode)) {
                return true
            }
            val ctrlEvent = event.withCtrlMeta()
            return searchField.onKeyShortcut(ctrlEvent.keyCode, ctrlEvent) ||
                searchField.dispatchKeyEvent(ctrlEvent)
        }
        focusSearchField()

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                deleteSearchTextBackwards()
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                appendSearchText(" ")
                true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                commitTopSearchResultAndClose()
                true
            }
            else -> {
                val typedText = resolveTypedText?.invoke(event) ?: run {
                    val unicode = event.unicodeChar
                    if (unicode <= 0) {
                        return searchField.dispatchKeyEvent(event)
                    }
                    val ch = unicode.toChar()
                    if (Character.isISOControl(ch)) {
                        return searchField.dispatchKeyEvent(event)
                    }
                    ch.toString()
                }
                appendSearchText(typedText)
                true
            }
        }
    }

    fun shouldConsumeSearchKeyUp(event: KeyEvent): Boolean {
        if (!isSearchPanelVisible) return false
        if (!searchInputCaptureEnabled) return false
        if (event.isAltPressed || event.isMetaPressed) return false
        if (event.isCtrlPressed) {
            focusSearchField()
            return searchField.onKeyShortcut(event.keyCode, event) ||
                searchField.dispatchKeyEvent(event) ||
                isTextEditingCtrlShortcut(event.keyCode)
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> true
            else -> {
                val unicode = event.unicodeChar
                unicode > 0 && !Character.isISOControl(unicode.toChar())
            }
        }
    }

    fun shouldConsumeSearchKeyUp(event: KeyEvent, ctrlActive: Boolean): Boolean {
        if (!isSearchPanelVisible) return false
        if (!searchInputCaptureEnabled) return false
        if (ctrlActive) {
            return true
        }
        return shouldConsumeSearchKeyUp(event)
    }

    private fun handleSearchInputConnectionKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                event.keyCode == KeyEvent.KEYCODE_PAGE_UP ||
                event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN
        }

        if (event.isCtrlPressed && handleTextEditingCtrlShortcut(event.keyCode)) {
            return true
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveSearchCursorBy(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveSearchCursorBy(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_PAGE_UP -> {
                setSearchSelection(0)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                setSearchSelection(searchField.text?.length ?: 0)
                true
            }
            else -> false
        }
    }

    private fun moveSearchCursorBy(delta: Int) {
        val text = searchField.text ?: return
        if (text.isEmpty()) {
            setSearchSelection(0)
            return
        }
        val anchor = if (delta < 0) {
            minOf(searchField.selectionStart, searchField.selectionEnd)
        } else {
            maxOf(searchField.selectionStart, searchField.selectionEnd)
        }.coerceIn(0, text.length)
        setSearchSelection((anchor + delta).coerceIn(0, text.length))
    }

    private fun setSearchSelection(index: Int) {
        val text = searchField.text ?: return
        Selection.setSelection(text, index.coerceIn(0, text.length))
        pendingSearchReplacementRange = null
    }

    private fun selectedSearchRange(textLength: Int): IntRange? {
        val selectionStart = searchField.selectionStart
        val selectionEnd = searchField.selectionEnd
        if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            val start = minOf(selectionStart, selectionEnd).coerceIn(0, textLength)
            val endExclusive = maxOf(selectionStart, selectionEnd).coerceIn(0, textLength)
            if (start < endExclusive) return start until endExclusive
        }
        return pendingSearchReplacementRange?.let { range ->
            val start = range.first.coerceIn(0, textLength)
            val endExclusive = (range.last + 1).coerceIn(0, textLength)
            if (start < endExclusive) start until endExclusive else null
        }
    }

    private fun isTextEditingCtrlShortcut(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_A ||
            keyCode == KeyEvent.KEYCODE_C ||
            keyCode == KeyEvent.KEYCODE_X ||
            keyCode == KeyEvent.KEYCODE_V
    }

    private fun handleTextEditingCtrlShortcut(keyCode: Int): Boolean {
        focusSearchField()
        return when (keyCode) {
            KeyEvent.KEYCODE_A -> {
                searchField.text?.let { text ->
                    Selection.selectAll(text)
                    pendingSearchReplacementRange = 0 until text.length
                }
                true
            }
            KeyEvent.KEYCODE_C -> searchField.onTextContextMenuItem(android.R.id.copy)
            KeyEvent.KEYCODE_X -> {
                pendingSearchReplacementRange = null
                searchField.onTextContextMenuItem(android.R.id.cut)
            }
            KeyEvent.KEYCODE_V -> {
                pendingSearchReplacementRange = null
                searchField.onTextContextMenuItem(android.R.id.paste)
            }
            else -> false
        }
    }

    private fun focusSearchField() {
        if (!searchField.hasFocus()) {
            searchField.requestFocus()
        }
    }

    private fun KeyEvent.withCtrlMeta(): KeyEvent {
        if (isCtrlPressed) {
            return this
        }
        return KeyEvent(
            downTime,
            eventTime,
            action,
            keyCode,
            repeatCount,
            metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
            deviceId,
            scanCode,
            flags,
            source
        )
    }
    
    /**
     * Scrolls to the top of the emoji picker.
     * Recents updates are applied only when safe for UX.
     */
    fun scrollToTop() {
        recyclerView.post {
            recyclerView.scrollToPosition(0)
            // Don't force a refresh here to avoid UI jumps while scrolling.
        }
    }

    private fun loadCategories() {
        // Cancel any previous loading job to avoid race conditions
        loadingJob?.cancel()

        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.GONE

        loadingJob = coroutineScope.launch {
            try {
                val recentCategory = withContext(Dispatchers.IO) { RecentEmojiManager.getRecentEmojiCategory(context) }
                val regularCategories = withContext(Dispatchers.IO) { EmojiRepository.getEmojiCategories(context) }
                val loadedSearchIndex = withContext(Dispatchers.IO) { EmojiSearchRepository.getSearchIndex(context) }
                this@EmojiPickerView.regularCategories = regularCategories
                this@EmojiPickerView.searchIndex = loadedSearchIndex

                val allCategories = mutableListOf<EmojiRepository.EmojiCategory>()
                if (recentCategory != null) allCategories.add(recentCategory)
                allCategories.addAll(regularCategories)

                // Always reset to first category when loading
                selectedCategoryId = allCategories.firstOrNull()?.id

                buildSections(allCategories)
                updateTabs(allCategories)

                loadingView.visibility = View.GONE
                if (allCategories.isEmpty()) {
                    emptyView.text = context.getString(R.string.emoji_picker_error)
                    emptyView.visibility = View.VISIBLE
                } else {
                    if (searchQuery.isNotBlank()) {
                        applySearchNow()
                    } else {
                        setSearchMode(false)
                        emptyView.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                        // Always start from top when opening emoji picker
                        recyclerView.scrollToPosition(0)
                    }
                }
            } catch (e: CancellationException) {
                throw e // Re-throw cancellation to properly cancel coroutine
            } catch (e: Exception) {
                loadingView.visibility = View.GONE
                emptyView.text = context.getString(R.string.emoji_picker_error)
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            kotlinx.coroutines.delay(120)
            applySearchNow()
        }
    }

    private fun appendSearchText(text: String) {
        if (text.isEmpty()) return
        val editable = searchField.text ?: return
        val selectionStart = searchField.selectionStart
        val selectionEnd = searchField.selectionEnd
        val replacementRange = selectedSearchRange(editable.length)
        if (replacementRange != null) {
            val start = replacementRange.first
            val end = replacementRange.last + 1
            editable.replace(start, end, text)
            searchField.setSelection(start + text.length)
            pendingSearchReplacementRange = null
        } else if (selectionStart >= 0 && selectionEnd >= 0 && selectionStart != selectionEnd) {
            val start = minOf(selectionStart, selectionEnd).coerceIn(0, editable.length)
            val end = maxOf(selectionStart, selectionEnd).coerceIn(0, editable.length)
            editable.replace(start, end, text)
            searchField.setSelection(start + text.length)
        } else {
            val cursor = selectionStart
                .takeIf { it == selectionEnd }
                ?.coerceIn(0, editable.length)
                ?: editable.length
            editable.insert(cursor, text)
            searchField.setSelection(cursor + text.length)
        }
    }

    fun disableSearchInputCapture() {
        setSearchInputCaptureEnabled(false)
    }

    private fun setSearchInputCaptureEnabled(enabled: Boolean) {
        searchInputCaptureEnabled = enabled
        searchField.isCursorVisible = enabled
        searchField.alpha = if (enabled) 1f else 0.75f
        if (enabled) {
            val editable = searchField.text
            if (editable != null) {
                searchField.setSelection(editable.length)
            }
        } else {
            searchField.clearFocus()
            pendingSearchReplacementRange = null
        }
    }

    private fun setSearchPanelVisible(visible: Boolean) {
        isSearchPanelVisible = visible
        searchPanel.visibility = if (visible) View.VISIBLE else View.GONE
        searchToggleButton.background = createTabBackground(visible)
        applyEdgeControlAppearance()
        setSearchInputCaptureEnabled(visible)
        if (visible) {
            searchField.requestFocus()
        }
        onSearchPanelVisibilityChanged?.invoke(visible)
    }

    private fun applySearchNow() {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            lastSearchResults = emptyList()
            setSearchMode(false)
            emptyView.text = context.getString(R.string.emoji_picker_error)
            emptyView.visibility = View.GONE
            recyclerView.visibility = if (sectionAdapter.itemCount > 0) View.VISIBLE else View.GONE
            return
        }

        val index = searchIndex
        if (index == null) {
            lastSearchResults = emptyList()
            setSearchMode(true)
            emptyView.text = context.getString(R.string.emoji_picker_error)
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            return
        }

        val results = EmojiSearchRepository.search(index, query)
        lastSearchResults = results
        setSearchMode(true)
        searchAdapter.submitList(results)
        if (results.isEmpty()) {
            emptyView.text = context.getString(R.string.emoji_picker_no_results)
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.scrollToPosition(0)
        }
    }

    private fun setSearchMode(enabled: Boolean) {
        if (isSearchMode == enabled) {
            // Ensure adapter is set correctly if external code changed it during refresh.
            val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
            if (enabled && recyclerView.adapter !== searchAdapter) {
                recyclerView.adapter = searchAdapter
                lm.spanSizeLookup = searchAdapter.spanSizeLookup
            } else if (!enabled && recyclerView.adapter !== sectionAdapter) {
                recyclerView.adapter = sectionAdapter
                lm.spanSizeLookup = sectionAdapter.spanSizeLookup
            }
            tabRow.alpha = if (enabled) 0.55f else 1f
            return
        }

        isSearchMode = enabled
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        if (enabled) {
            recyclerView.adapter = searchAdapter
            lm.spanSizeLookup = searchAdapter.spanSizeLookup
        } else {
            recyclerView.adapter = sectionAdapter
            lm.spanSizeLookup = sectionAdapter.spanSizeLookup
            updateTabsSelection()
        }
        tabRow.alpha = if (enabled) 0.55f else 1f
    }

    private fun buildSections(categories: List<EmojiRepository.EmojiCategory>) {
        val items = mutableListOf<SectionItem>()
        val categoryIds = ArrayList<String>()

        categories.forEach { category ->
            val title = category.displayNameRes?.let { context.getString(it) } ?: category.id
            items.add(SectionItem.Header(category.id, title))
            categoryIds.add(category.id)
            category.emojis.forEach { emojiEntry ->
                items.add(SectionItem.Emoji(category.id, emojiEntry))
                categoryIds.add(category.id)
            }
        }

        rebuildIndexCaches(items, categoryIds)
        sectionAdapter.submitList(items)
    }

    private fun updateTabs(categories: List<EmojiRepository.EmojiCategory>) {
        tabRow.removeAllViews()
        tabCategoryIds = categories.map { it.id }
        if (selectedCategoryId !in tabCategoryIds) {
            selectedCategoryId = tabCategoryIds.firstOrNull()
        }
        val tabHeight = dpToPx(32f)
        categories.forEach { category ->
            val iconRes = EmojiRepository.getCategoryIconRes(category.id)
            val label = category.displayNameRes?.let { context.getString(it) } ?: category.id
            val isSelected = category.id == selectedCategoryId
            val btn = ImageView(context).apply {
                setImageResource(iconRes)
                contentDescription = label
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setColorFilter(themeOverride?.textAndIcons ?: Color.WHITE)
                background = createTabBackground(isSelected)
                // Icon always visible (alpha 1), background changes
                val pad = dpToPx(4f) // Minimal padding
                setPadding(pad, pad, pad, pad)
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(
                    0, // Use weight
                    if (roundedControls) ViewGroup.LayoutParams.MATCH_PARENT else tabHeight,
                    1f // Equal weight for all tabs
                )
                setOnClickListener {
                    if (isSearchMode) return@setOnClickListener
                    selectedCategoryId = category.id
                    updateTabsSelection()
                    isTabClickScroll = true
                    
                    // Recents is always at position 0 when present
                    if (category.id == EmojiRepository.RECENTS_CATEGORY_ID) {
                        (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(0, 0)
                        recyclerView.post {
                            requestRecentsRefresh(requireTop = true, requireNotRecents = false)
                        }
                    } else {
                        val headerPos = headerPositions[category.id] ?: return@setOnClickListener
                        (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(headerPos, 0)
                    }
                }
            }
            tabRow.addView(btn)
        }
        updateTabsSelection()
    }

    private fun updateTabsSelection() {
        for (i in 0 until tabRow.childCount) {
            val view = tabRow.getChildAt(i) as? ImageView ?: continue
            val categoryId = tabCategoryIds.getOrNull(i)
            val isSelected = categoryId == selectedCategoryId
            // Icon always visible, only background changes
            view.background = createTabBackground(isSelected)
        }
    }

    private fun onEmojiSelected(emoji: String, categoryId: String, closeAfterCommit: Boolean? = null) {
        val inputConnection = currentInputConnection
        // Recents persistence must survive the SYM auto-close: closing the picker evicts this
        // view from its container, which cancels coroutineScope; ATOMIC guarantees the write
        // still runs even when cancellation lands before the coroutine body starts.
        coroutineScope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
            val changed = RecentEmojiManager.addRecentEmoji(
                context,
                emoji,
                moveToTopWhenExists = true
            )
            if (changed) {
                val requiresNotRecents = categoryId == EmojiRepository.RECENTS_CATEGORY_ID
                withContext(Dispatchers.Main) {
                    requestRecentsRefresh(requireTop = !requiresNotRecents, requireNotRecents = requiresNotRecents)
                }
            }
        }
        // Commit synchronously before closing: a post{} on a view that the close detaches
        // would only run again when the picker is re-attached (i.e. the next time it opens).
        inputConnection?.commitText(emoji, 1)
        val shouldClose = closeAfterCommit
            ?: (
                SettingsManager.getSymAutoClose(context) &&
                    SettingsManager.getSymAutoCloseOnTouch(context)
                )
        if (shouldClose) {
            onCloseRequested?.invoke()
        }
    }

    /**
     * Simple refresh of recents from storage.
     * Applies updates only when safe for UX.
     * Compares stored vs displayed recents and updates only if different.
     */
    private fun refreshRecentsFromStorage(allowInsertOrRemove: Boolean) {
        coroutineScope.launch {
            val recentCategory = withContext(Dispatchers.IO) {
                RecentEmojiManager.getRecentEmojiCategory(context)
            }

            val recentsHeaderIndex = headerPositions[EmojiRepository.RECENTS_CATEGORY_ID]

            // Case 1: Recents in storage but not displayed -> full reload
            if (recentsHeaderIndex == null && recentCategory != null) {
                if (!allowInsertOrRemove) {
                    markRecentsRefreshPending(requireTop = true, requireNotRecents = false)
                    return@launch
                }
                val anchor = captureScrollAnchor()
                val newRecentsItems = buildRecentsItems(recentCategory)
                val newItems = newRecentsItems + sectionItems
                rebuildIndexCaches(newItems)
                sectionAdapter.submitList(newItems) {
                    anchor?.let { restoreScrollAnchor(it, newRecentsItems.size) }
                }
                updateTabs(buildAllCategories(recentCategory))
                return@launch
            }

            // Case 2: No recents in storage but displayed -> full reload
            if (recentsHeaderIndex != null && recentCategory == null) {
                if (!allowInsertOrRemove) {
                    markRecentsRefreshPending(requireTop = true, requireNotRecents = false)
                    return@launch
                }
                val anchor = captureScrollAnchor()
                val nextHeaderIndex = sectionItems.withIndex()
                    .drop(recentsHeaderIndex + 1)
                    .firstOrNull { (_, item) -> item is SectionItem.Header }?.index
                    ?: sectionItems.size
                val removedCount = nextHeaderIndex - recentsHeaderIndex
                val newItems = sectionItems.toMutableList()
                repeat(removedCount) {
                    newItems.removeAt(recentsHeaderIndex)
                }
                rebuildIndexCaches(newItems)
                if (selectedCategoryId == EmojiRepository.RECENTS_CATEGORY_ID) {
                    selectedCategoryId = itemCategoryIds.firstOrNull()
                }
                sectionAdapter.submitList(newItems) {
                    anchor?.let { restoreScrollAnchor(it, -removedCount) }
                }
                updateTabs(buildAllCategories(null))
                return@launch
            }

            // Case 3: Both exist -> compare and update if different
            if (recentsHeaderIndex != null && recentCategory != null) {
                val nextHeaderIndex = sectionItems.withIndex()
                    .drop(recentsHeaderIndex + 1)
                    .firstOrNull { (_, item) -> item is SectionItem.Header }?.index
                    ?: sectionItems.size

                val displayedRecents = sectionItems
                    .subList(recentsHeaderIndex + 1, nextHeaderIndex)
                    .filterIsInstance<SectionItem.Emoji>()
                    .map { it.entry.base }

                val storedRecents = recentCategory.emojis.map { it.base }

                // Only update if different
                if (displayedRecents != storedRecents) {
                    val newRecentsItems = buildRecentsItems(recentCategory)

                    val newItems = sectionItems.toMutableList()
                    for (i in recentsHeaderIndex until nextHeaderIndex) {
                        newItems.removeAt(recentsHeaderIndex)
                    }
                    newItems.addAll(recentsHeaderIndex, newRecentsItems)

                    rebuildIndexCaches(newItems)
                    val anchor = if (isAtAbsoluteTop()) null else captureScrollAnchor()
                    sectionAdapter.submitList(newItems) {
                        anchor?.let { restoreScrollAnchor(it, 0) }
                    }
                }
            }
        }
    }

    /**
     * Updates tabs asynchronously when recents section is added/removed.
     */
    private fun updateTabsAsync() {
        coroutineScope.launch {
            val recentCategory = withContext(Dispatchers.IO) {
                RecentEmojiManager.getRecentEmojiCategory(context)
            }
            val regularCategories = withContext(Dispatchers.IO) {
                EmojiRepository.getEmojiCategories(context)
            }

            val allCategories = mutableListOf<EmojiRepository.EmojiCategory>()
            if (recentCategory != null) allCategories.add(recentCategory)
            allCategories.addAll(regularCategories)

            updateTabs(allCategories)
        }
    }

    private fun createTabBackground(isSelected: Boolean): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            val color = if (theme != null) {
                if (isSelected) colorWithAlpha(theme.accent, 100) else Color.TRANSPARENT
            } else if (isSelected) {
                Color.argb(100, 255, 255, 255)
            } else {
                Color.TRANSPARENT
            }
            setColor(color)
            if (theme != null && isSelected) {
                setStroke(dpToPx(1f), theme.divider)
            }
            cornerRadius = dpToPx(6f).toFloat()
        }
    }

    private fun createCloseButtonBackground(): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(theme?.statusBarButton ?: Color.argb(95, 220, 38, 38))
            if (theme != null) {
                setStroke(dpToPx(1f), theme.divider)
            }
            cornerRadius = dpToPx(6f).toFloat()
        }
    }

    private fun createSearchFieldBackground(): GradientDrawable {
        val theme = themeOverride
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(theme?.suggestion ?: Color.argb(36, 255, 255, 255))
            if (theme != null) {
                setStroke(dpToPx(1f), theme.divider)
            }
            cornerRadius = dpToPx(7f).toFloat()
        }
    }

    private fun applyTheme() {
        val theme = themeOverride
        val background = theme?.background ?: Color.TRANSPARENT
        setBackgroundColor(background)
        vertical.setBackgroundColor(background)
        recyclerView.setBackgroundColor(background)
        searchPanel.setBackgroundColor(background)
        loadingView.setBackgroundColor(background)
        emptyView.setBackgroundColor(background)
        searchField.setTextColor(theme?.textAndIcons ?: Color.WHITE)
        searchField.setHintTextColor(colorWithAlpha(theme?.textAndIcons ?: Color.WHITE, 160))
        searchField.background = createSearchFieldBackground()
        closeButton.setColorFilter(theme?.textAndIcons ?: Color.WHITE)
        closeButton.background = createCloseButtonBackground()
        searchToggleButton.setColorFilter(theme?.textAndIcons ?: Color.WHITE)
        searchToggleButton.background = createTabBackground(isSearchPanelVisible)
        keyboardSwitcherButton.setColorFilter(theme?.textAndIcons ?: Color.WHITE)
        keyboardSwitcherButton.background = createTabBackground(false)
        applyEdgeControlAppearance()
        emptyView.setTextColor(colorWithAlpha(theme?.textAndIcons ?: Color.WHITE, 128))
        updateTabsSelection()
        sectionAdapter.notifyDataSetChanged()
        searchAdapter.notifyDataSetChanged()
    }

    private fun colorWithAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun showVariantsPopup(anchor: View, entry: EmojiRepository.EmojiEntry, categoryId: String) {
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val horizontalPadding = (16 * density).toInt()
        val verticalPadding = (12 * density).toInt()
        val itemHorizontalPadding = (12 * density).toInt()
        val itemVerticalPadding = (8 * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            gravity = Gravity.CENTER
        }

        var popup: PopupWindow? = null
        val options = listOf(entry.base) + entry.variants
        options.forEach { emoji ->
            val textView = TextView(context).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(itemHorizontalPadding, itemVerticalPadding, itemHorizontalPadding, itemVerticalPadding)
                setTextColor(themeOverride?.textAndIcons ?: Color.BLACK)
            }
            textView.setOnClickListener {
                onEmojiSelected(emoji, categoryId)
                popup?.dismiss()
            }
            container.addView(textView)
        }

        container.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        popup = PopupWindow(
            container,
            WRAP_CONTENT,
            WRAP_CONTENT,
            false // Don't take focus to avoid closing emoji picker
        ).apply {
            setBackgroundDrawable(ColorDrawable(themeOverride?.keyPopup ?: Color.parseColor("#EEFFFFFF")))
            isOutsideTouchable = true
            isFocusable = false
            elevation = 12f
        }

        // Position popup above the anchor
        val location = IntArray(2)
        anchor.getLocationInWindow(location)
        val windowWidth = context.resources.displayMetrics.widthPixels
        val popupWidth = container.measuredWidth
        val popupHeight = container.measuredHeight
        val anchorX = location[0]
        val anchorY = location[1]
        val desiredX = anchorX + (anchor.width - popupWidth) / 2
        val clampedX = desiredX.coerceIn(0, windowWidth - popupWidth)
        val xOffset = clampedX - anchorX
        val desiredYOffset = -(popupHeight + anchor.height)
        val minYOffset = -(anchorY + anchor.height)
        val yOffset = maxOf(desiredYOffset, minYOffset)
        popup.showAsDropDown(anchor, xOffset, yOffset)
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Recreate coroutine scope if it was cancelled
        if (!coroutineScope.isActive) {
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (!containerReordering) {
            coroutineScope.cancel()
            // Detach outside a host reorder means the picker actually left the screen
            // (IME hidden or SYM closed): reopen fresh instead of resuming the search.
            resetSearchStateQuietly()
        }
    }

    /**
     * Wraps a host-side reordering of this view within its container (remove + re-add in
     * one step, e.g. stacking the software keyboard below). The transient detach must not
     * reset the search state.
     */
    fun reorderingWithinContainer(block: () -> Unit) {
        containerReordering = true
        try {
            block()
        } finally {
            containerReordering = false
        }
    }

    private fun resetSearchStateQuietly() {
        if (!isSearchPanelVisible) {
            if (searchQuery.isNotEmpty()) {
                searchQuery = ""
                lastSearchResults = emptyList()
                searchField.setText("")
            }
            return
        }
        // Quiet: no onSearchPanelVisibilityChanged notification, the view is off-screen.
        isSearchPanelVisible = false
        searchPanel.visibility = View.GONE
        searchToggleButton.background = createTabBackground(false)
        setSearchInputCaptureEnabled(false)
        searchQuery = ""
        lastSearchResults = emptyList()
        searchField.setText("")
    }

    private inner class SectionAdapter(private val columns: Int) :
        ListAdapter<SectionItem, RecyclerView.ViewHolder>(SectionItemDiffCallback()) {
        val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (getItemViewType(position)) {
                    VIEW_TYPE_HEADER -> columns
                    else -> 1
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is SectionItem.Header -> VIEW_TYPE_HEADER
                is SectionItem.Emoji -> VIEW_TYPE_EMOJI
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == VIEW_TYPE_HEADER) {
                // Minimal spacer between categories (no text, just 1dp height)
                val spacer = View(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(1f)
                    )
                }
                HeaderViewHolder(spacer)
            } else {
                val tv = TextView(parent.context).apply {
                    gravity = Gravity.CENTER
                    textSize = 28.8f
                    minHeight = emojiSize
                    minWidth = emojiSize
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                EmojiViewHolder(tv)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is SectionItem.Header -> {
                    // Nothing to bind - it's just a spacer
                }
                is SectionItem.Emoji -> {
                    (holder as EmojiViewHolder).textView.text = item.entry.base
                    holder.textView.setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
                    holder.textView.setOnClickListener {
                        onEmojiSelected(item.entry.base, item.categoryId)
                    }
                    holder.textView.setOnLongClickListener {
                        if (item.entry.variants.isEmpty()) return@setOnLongClickListener false
                        showVariantsPopup(holder.textView, item.entry, item.categoryId)
                        true
                    }
                }
            }
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)
    private class EmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    private class SearchEmojiViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private inner class SearchAdapter :
        ListAdapter<EmojiSearchRepository.EmojiSearchResult, SearchEmojiViewHolder>(SearchResultDiffCallback()) {
        val spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchEmojiViewHolder {
            val tv = TextView(parent.context).apply {
                gravity = Gravity.CENTER
                textSize = 28.8f
                minHeight = emojiSize
                minWidth = emojiSize
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            return SearchEmojiViewHolder(tv)
        }

        override fun onBindViewHolder(holder: SearchEmojiViewHolder, position: Int) {
            val item = getItem(position)
            holder.textView.text = item.entry.base
            holder.textView.setTextColor(themeOverride?.textAndIcons ?: Color.WHITE)
            holder.textView.setOnClickListener {
                onEmojiSelected(item.entry.base, item.categoryId)
            }
            holder.textView.setOnLongClickListener {
                if (item.entry.variants.isEmpty()) return@setOnLongClickListener false
                showVariantsPopup(holder.textView, item.entry, item.categoryId)
                true
            }
        }

        override fun getItemViewType(position: Int): Int = VIEW_TYPE_EMOJI
    }

    private sealed class SectionItem {
        data class Header(val categoryId: String, val title: String) : SectionItem()
        data class Emoji(val categoryId: String, val entry: EmojiRepository.EmojiEntry) : SectionItem()
    }

    private class SectionItemDiffCallback : DiffUtil.ItemCallback<SectionItem>() {
        override fun areItemsTheSame(oldItem: SectionItem, newItem: SectionItem): Boolean {
            return when {
                oldItem is SectionItem.Header && newItem is SectionItem.Header ->
                    oldItem.categoryId == newItem.categoryId
                oldItem is SectionItem.Emoji && newItem is SectionItem.Emoji ->
                    oldItem.categoryId == newItem.categoryId && oldItem.entry.base == newItem.entry.base
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: SectionItem, newItem: SectionItem): Boolean {
            return oldItem == newItem
        }
    }

    private class SearchResultDiffCallback :
        DiffUtil.ItemCallback<EmojiSearchRepository.EmojiSearchResult>() {
        override fun areItemsTheSame(
            oldItem: EmojiSearchRepository.EmojiSearchResult,
            newItem: EmojiSearchRepository.EmojiSearchResult
        ): Boolean {
            return oldItem.entry.base == newItem.entry.base && oldItem.categoryId == newItem.categoryId
        }

        override fun areContentsTheSame(
            oldItem: EmojiSearchRepository.EmojiSearchResult,
            newItem: EmojiSearchRepository.EmojiSearchResult
        ): Boolean {
            return oldItem == newItem
        }
    }

    private data class ScrollAnchor(val position: Int, val offset: Int)

    private fun rebuildIndexCaches(items: List<SectionItem>, categoryIds: List<String>? = null) {
        val headers = mutableMapOf<String, Int>()
        val ids = categoryIds?.toMutableList() ?: ArrayList(items.size)
        if (categoryIds == null) {
            items.forEach { item ->
                ids.add(item.categoryId())
            }
        }
        items.forEachIndexed { index, item ->
            if (item is SectionItem.Header) {
                headers[item.categoryId] = index
            }
        }
        sectionItems = items
        headerPositions = headers
        itemCategoryIds = ids
    }

    private fun buildRecentsItems(recentCategory: EmojiRepository.EmojiCategory): List<SectionItem> {
        val recentsTitle = recentCategory.displayNameRes?.let { context.getString(it) }
            ?: EmojiRepository.RECENTS_CATEGORY_ID
        val items = ArrayList<SectionItem>(recentCategory.emojis.size + 1)
        items.add(SectionItem.Header(EmojiRepository.RECENTS_CATEGORY_ID, recentsTitle))
        recentCategory.emojis.forEach { entry ->
            items.add(SectionItem.Emoji(EmojiRepository.RECENTS_CATEGORY_ID, entry))
        }
        return items
    }

    private fun buildAllCategories(recentCategory: EmojiRepository.EmojiCategory?): List<EmojiRepository.EmojiCategory> {
        return if (recentCategory == null) {
            regularCategories
        } else {
            listOf(recentCategory) + regularCategories
        }
    }

    private fun captureScrollAnchor(): ScrollAnchor? {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return null
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION) return null
        val topView = recyclerView.getChildAt(0)
        val offset = topView?.top ?: 0
        return ScrollAnchor(firstVisible, offset)
    }

    private fun restoreScrollAnchor(anchor: ScrollAnchor, positionDelta: Int) {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
        val targetPosition = (anchor.position + positionDelta).coerceAtLeast(0)
        if (targetPosition >= sectionAdapter.itemCount) return
        lm.scrollToPositionWithOffset(targetPosition, anchor.offset)
    }

    private fun requestRecentsRefresh(requireTop: Boolean, requireNotRecents: Boolean) {
        markRecentsRefreshPending(requireTop, requireNotRecents)
        maybeApplyPendingRecentsRefresh()
    }

    private fun markRecentsRefreshPending(requireTop: Boolean, requireNotRecents: Boolean) {
        pendingRecentsRefresh = true
        pendingRecentsRefreshRequiresTop = pendingRecentsRefreshRequiresTop || requireTop
        pendingRecentsRefreshRequiresNotRecents = pendingRecentsRefreshRequiresNotRecents || requireNotRecents
    }

    private fun maybeApplyPendingRecentsRefresh() {
        if (!pendingRecentsRefresh) return
        if (scrollState != RecyclerView.SCROLL_STATE_IDLE) return
        val requiresNotRecents = pendingRecentsRefreshRequiresNotRecents
        val requiresTop = pendingRecentsRefreshRequiresTop && !requiresNotRecents
        if (requiresTop && !isNearTop()) return
        if (requiresNotRecents &&
            selectedCategoryId == EmojiRepository.RECENTS_CATEGORY_ID) {
            return
        }
        pendingRecentsRefresh = false
        pendingRecentsRefreshRequiresTop = false
        pendingRecentsRefreshRequiresNotRecents = false
        refreshRecentsFromStorage(allowInsertOrRemove = isNearTop())
    }

    private fun isNearTop(): Boolean {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return false
        val firstVisible = lm.findFirstVisibleItemPosition()
        return firstVisible != RecyclerView.NO_POSITION && firstVisible <= recentsApplyTopThreshold
    }

    private fun isAtAbsoluteTop(): Boolean {
        val lm = recyclerView.layoutManager as? GridLayoutManager ?: return false
        val firstVisible = lm.findFirstVisibleItemPosition()
        if (firstVisible != 0) return false
        val firstView = lm.findViewByPosition(0) ?: return false
        return firstView.top >= recyclerView.paddingTop
    }

    private fun SectionItem.categoryId(): String {
        return when (this) {
            is SectionItem.Header -> categoryId
            is SectionItem.Emoji -> categoryId
        }
    }

    companion object {
        private const val COMPACT_HEIGHT_DP = 177f
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_EMOJI = 1

        fun configuredHeightPx(context: Context): Int {
            val compactHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                COMPACT_HEIGHT_DP,
                context.resources.displayMetrics
            ).toInt()
            return if (SettingsManager.getEmojiPickerExpandedHeight(context)) {
                (compactHeight * 1.5f).toInt()
            } else {
                compactHeight
            }
        }
    }
}
