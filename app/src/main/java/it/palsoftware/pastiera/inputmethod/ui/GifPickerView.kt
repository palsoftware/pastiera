package it.palsoftware.pastiera.inputmethod.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.LocalMediaPickerActivity
import it.palsoftware.pastiera.gif.KlipyGifClient
import it.palsoftware.pastiera.gif.KlipyGifResult
import it.palsoftware.pastiera.gif.KlipyMediaType
import it.palsoftware.pastiera.gif.LocalMediaRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.widget.Toast
import java.nio.ByteBuffer

class GifPickerView(
    context: Context,
    private val gifClient: KlipyGifClient,
    private val onGifSelected: (KlipyGifResult) -> Unit
) : FrameLayout(context) {

    private val searchField: EditText
    private val recyclerView: RecyclerView
    private val loadingView: ProgressBar
    private val emptyView: TextView
    private val attributionView: TextView
    private val mediaTypeTabs: LinearLayout
    private val localFolderBar: LinearLayout
    private val localFolderText: TextView
    private val previewOverlay: FrameLayout
    private val previewImageView: ImageView
    private val previewTitleView: TextView
    private val sendButton: TextView
    private val cancelButton: TextView
    private val resultAdapter = GifResultAdapter(
        onGifTapped = { showPreview(it) },
        onGifLongPressed = { copyGifLink(it) }
    )
    private val fixedHeight = dpToPx(312f)
    private val smallPadding = dpToPx(8f)
    private val previewSpacing = dpToPx(8f)
    private val columns = 3
    private var searchJob: Job? = null
    private var searchQuery = ""
    private var searchInputCaptureEnabled = true
    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var selectedGif: KlipyGifResult? = null
    private var selectedMediaType: KlipyMediaType = KlipyMediaType.GIF
    private var localFolderReceiverRegistered = false
    private val localMediaRepository = LocalMediaRepository(context)
    private val localFolderReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocalMediaPickerActivity.ACTION_FOLDER_SELECTED) {
                updateLocalFolderBar()
                if (selectedMediaType == KlipyMediaType.LOCAL) {
                    refresh()
                }
            }
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)

        val vertical = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, fixedHeight)
        }

        searchField = EditText(context).apply {
            hint = context.getString(R.string.gif_picker_search_placeholder)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(160, 255, 255, 255))
            textSize = 14f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setBackgroundColor(Color.argb(30, 255, 255, 255))
            val padH = dpToPx(10f)
            val padV = dpToPx(6f)
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(smallPadding, smallPadding, smallPadding, 0)
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
        }

        mediaTypeTabs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(smallPadding, smallPadding / 2, smallPadding, 0)
            }
        }
        KlipyMediaType.entries.forEach { type ->
            mediaTypeTabs.addView(buildMediaTypeTab(type))
        }

        localFolderBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(smallPadding, smallPadding / 2, smallPadding, 0)
            }
            visibility = View.GONE
        }
        localFolderText = TextView(context).apply {
            setTextColor(Color.argb(210, 255, 255, 255))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val chooseFolderButton = buildPreviewButton(context.getString(R.string.local_media_choose_folder)).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { openLocalFolderPicker() }
        }
        localFolderBar.addView(localFolderText)
        localFolderBar.addView(chooseFolderButton)

        recyclerView = RecyclerView(context).apply {
            overScrollMode = View.OVER_SCROLL_ALWAYS
            clipToPadding = false
            setPadding(smallPadding, smallPadding, smallPadding, smallPadding)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = GridLayoutManager(context, columns)
            adapter = resultAdapter
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.set(previewSpacing / 2, previewSpacing / 2, previewSpacing / 2, previewSpacing / 2)
                }
            })
        }

        loadingView = ProgressBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            visibility = View.GONE
        }

        emptyView = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.argb(180, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
        }

        attributionView = TextView(context).apply {
            text = context.getString(R.string.gif_picker_powered_by)
            textSize = 11f
            setTextColor(Color.argb(160, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(smallPadding, 0, smallPadding, smallPadding / 2)
            }
        }

        vertical.addView(searchField)
        vertical.addView(mediaTypeTabs)
        vertical.addView(localFolderBar)
        vertical.addView(recyclerView)
        vertical.addView(attributionView)

        addView(vertical)
        addView(loadingView)
        addView(emptyView)
        previewOverlay = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(232, 16, 16, 16))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }
        val previewContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setPadding(dpToPx(12f), dpToPx(12f), dpToPx(12f), dpToPx(12f))
        }
        previewImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.argb(35, 255, 255, 255))
        }
        previewTitleView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(10f)
            }
        }
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(12f)
            }
        }
        cancelButton = buildPreviewButton(context.getString(R.string.gif_picker_preview_cancel)).apply {
            setOnClickListener { hidePreview() }
        }
        sendButton = buildPreviewButton(context.getString(R.string.gif_picker_preview_send, selectedMediaType.singularName)).apply {
            setOnClickListener {
                selectedGif?.let(onGifSelected)
                hidePreview()
            }
        }
        buttonRow.addView(cancelButton)
        buttonRow.addView(sendButton)
        previewContent.addView(previewImageView)
        previewContent.addView(previewTitleView)
        previewContent.addView(buttonRow)
        previewOverlay.addView(previewContent)
        addView(previewOverlay)

        val filter = IntentFilter(LocalMediaPickerActivity.ACTION_FOLDER_SELECTED)
        ContextCompat.registerReceiver(context, localFolderReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        localFolderReceiverRegistered = true
        updateLocalFolderBar()

        refresh()
    }

    fun refresh() {
        ensureActiveScope()
        hidePreview()
        if (selectedMediaType != KlipyMediaType.LOCAL && !gifClient.hasConfiguredApiKey()) {
            showMessage(context.getString(R.string.gif_picker_missing_api_key))
            return
        }
        searchJob?.cancel()
        if (searchQuery.isBlank()) {
            applyTrending()
            return
        }
        applySearchNow()
    }

    fun resetToTrending() {
        ensureActiveScope()
        searchJob?.cancel()
        searchQuery = ""
        searchField.setText("")
        hidePreview()
        showMessage(context.getString(R.string.gif_picker_empty_prompt, selectedMediaType.displayName))
        applyTrending()
    }

    fun handleSearchKeyDown(event: KeyEvent): Boolean {
        if (!searchInputCaptureEnabled) return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                val text = searchField.text ?: return true
                if (text.isEmpty()) return true
                text.delete(text.length - 1, text.length)
                true
            }
            KeyEvent.KEYCODE_SPACE -> {
                appendSearchText(" ")
                true
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> true
            else -> {
                val unicode = event.unicodeChar
                if (unicode <= 0) return false
                val ch = unicode.toChar()
                if (Character.isISOControl(ch)) return false
                appendSearchText(ch.toString())
                true
            }
        }
    }

    fun shouldConsumeSearchKeyUp(event: KeyEvent): Boolean {
        if (!searchInputCaptureEnabled) return false
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return false
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

    fun disableSearchInputCapture() {
        searchInputCaptureEnabled = false
        searchField.clearFocus()
        searchField.alpha = 0.75f
    }

    fun scrollToTop() {
        recyclerView.post { recyclerView.scrollToPosition(0) }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        searchJob?.cancel()
        coroutineScope.cancel()
        if (localFolderReceiverRegistered) {
            runCatching { context.unregisterReceiver(localFolderReceiver) }
            localFolderReceiverRegistered = false
        }
    }

    private fun showPreview(item: KlipyGifResult) {
        selectedGif = item
        previewTitleView.text = item.title
        GifPreviewLoader.loadInto(previewImageView, item.gifUrl)
        sendButton.text = context.getString(R.string.gif_picker_preview_send, item.mediaType.singularName)
        previewOverlay.visibility = View.VISIBLE
    }

    private fun hidePreview() {
        selectedGif = null
        previewOverlay.visibility = View.GONE
    }

    private fun copyGifLink(item: KlipyGifResult) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val fallbackLink = item.shareUrl.ifBlank { item.gifUrl }
        clipboard.setPrimaryClip(ClipData.newPlainText("${item.mediaType.singularName} link", fallbackLink))
        Toast.makeText(context, context.getString(R.string.gif_picker_link_copied, item.mediaType.singularName), Toast.LENGTH_SHORT).show()
    }

    private fun buildMediaTypeTab(type: KlipyMediaType): TextView {
        return TextView(context).apply {
            text = type.displayName
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(dpToPx(12f), dpToPx(6f), dpToPx(12f), dpToPx(6f))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpToPx(2f)
                marginEnd = dpToPx(2f)
            }
            updateMediaTypeTabStyle(this, type == selectedMediaType)
            setOnClickListener {
                if (selectedMediaType == type) return@setOnClickListener
                selectedMediaType = type
                refreshMediaTypeTabs()
                updateLocalFolderBar()
                refresh()
            }
        }
    }

    private fun refreshMediaTypeTabs() {
        for (index in 0 until mediaTypeTabs.childCount) {
            val child = mediaTypeTabs.getChildAt(index) as? TextView ?: continue
            val type = KlipyMediaType.entries.getOrNull(index) ?: continue
            updateMediaTypeTabStyle(child, type == selectedMediaType)
        }
    }

    private fun updateMediaTypeTabStyle(view: TextView, selected: Boolean) {
        view.setBackgroundColor(if (selected) Color.argb(60, 255, 255, 255) else Color.argb(20, 255, 255, 255))
        view.alpha = if (selected) 1f else 0.78f
    }

    private fun updateLocalFolderBar() {
        val isLocal = selectedMediaType == KlipyMediaType.LOCAL
        localFolderBar.visibility = if (isLocal) View.VISIBLE else View.GONE
        if (!isLocal) return
        val folderUri = localMediaRepository.getSelectedFolderUri()
        localFolderText.text = if (folderUri == null) {
            context.getString(R.string.local_media_no_folder)
        } else {
            context.getString(R.string.local_media_folder_selected, folderUri.lastPathSegment ?: "folder")
        }
    }

    private fun openLocalFolderPicker() {
        val intent = Intent(context, LocalMediaPickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun buildPreviewButton(label: String): TextView {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            setBackgroundColor(Color.argb(38, 255, 255, 255))
            setPadding(dpToPx(18f), dpToPx(10f), dpToPx(18f), dpToPx(10f))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dpToPx(4f)
                marginEnd = dpToPx(4f)
            }
        }
    }

    private fun ensureActiveScope() {
        if (!coroutineScope.isActive) {
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            delay(180)
            applySearchNow()
        }
    }

    private fun appendSearchText(text: String) {
        val editable = searchField.text ?: return
        editable.append(text)
        searchField.setSelection(editable.length)
    }

    private fun applySearchNow() {
        if (searchQuery.isBlank()) {
            applyTrending()
            return
        }

        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    if (selectedMediaType == KlipyMediaType.LOCAL) {
                        localMediaRepository.getItems().filter {
                            it.title.contains(searchQuery, ignoreCase = true)
                        }
                    } else {
                        gifClient.search(searchQuery, mediaType = selectedMediaType)
                    }
                }
                loadingView.visibility = View.GONE
                if (results.isEmpty()) {
                    resultAdapter.submitList(emptyList())
                    showMessage(context.getString(R.string.gif_picker_no_results, selectedMediaType.displayName))
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    resultAdapter.submitList(results)
                    scrollToTop()
                }
            } catch (e: CancellationException) {
                loadingView.visibility = View.GONE
                throw e
            } catch (_: Exception) {
                loadingView.visibility = View.GONE
                resultAdapter.submitList(emptyList())
                showMessage(context.getString(R.string.gif_picker_error))
            }
        }
    }

    private fun applyTrending() {
        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    if (selectedMediaType == KlipyMediaType.LOCAL) {
                        localMediaRepository.getItems()
                    } else {
                        gifClient.trending(mediaType = selectedMediaType)
                    }
                }
                loadingView.visibility = View.GONE
                if (results.isEmpty()) {
                    resultAdapter.submitList(emptyList())
                    val message = if (selectedMediaType == KlipyMediaType.LOCAL && localMediaRepository.getSelectedFolderUri() == null) {
                        context.getString(R.string.local_media_choose_folder_prompt)
                    } else {
                        context.getString(R.string.gif_picker_empty_prompt, selectedMediaType.displayName)
                    }
                    showMessage(message)
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    resultAdapter.submitList(results)
                    scrollToTop()
                }
            } catch (e: CancellationException) {
                loadingView.visibility = View.GONE
                throw e
            } catch (_: Exception) {
                loadingView.visibility = View.GONE
                resultAdapter.submitList(emptyList())
                showMessage(context.getString(R.string.gif_picker_error))
            }
        }
    }

    private fun showMessage(message: String) {
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        loadingView.visibility = View.GONE
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}

private class GifResultAdapter(
    private val onGifTapped: (KlipyGifResult) -> Unit,
    private val onGifLongPressed: (KlipyGifResult) -> Unit
) : ListAdapter<KlipyGifResult, GifResultViewHolder>(GifResultDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifResultViewHolder {
        val context = parent.context
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(26, 255, 255, 255))
            val padding = dpToPx(context, 6f)
            setPadding(padding, padding, padding, padding)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val preview = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 72f)
            )
            setBackgroundColor(Color.argb(35, 255, 255, 255))
        }
        val title = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            maxLines = 2
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(context, 6f)
            }
        }
        container.addView(preview)
        container.addView(title)
        return GifResultViewHolder(container, preview, title, onGifTapped, onGifLongPressed)
    }

    override fun onBindViewHolder(holder: GifResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

private class GifResultViewHolder(
    itemView: View,
    private val previewView: ImageView,
    private val titleView: TextView,
    private val onGifTapped: (KlipyGifResult) -> Unit,
    private val onGifLongPressed: (KlipyGifResult) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    fun bind(item: KlipyGifResult) {
        titleView.text = item.title
        itemView.setOnClickListener { onGifTapped(item) }
        itemView.setOnLongClickListener {
            onGifLongPressed(item)
            true
        }
        GifPreviewLoader.loadInto(previewView, item.previewUrl)
    }
}

private object GifResultDiffCallback : DiffUtil.ItemCallback<KlipyGifResult>() {
    override fun areItemsTheSame(oldItem: KlipyGifResult, newItem: KlipyGifResult): Boolean = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: KlipyGifResult, newItem: KlipyGifResult): Boolean = oldItem == newItem
}

private object GifPreviewLoader {
    private val okHttpClient = OkHttpClient()
    private val bitmapCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024L / 16L).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val byteCache = object : LruCache<String, ByteArray>(8 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size / 1024
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun loadInto(imageView: ImageView, url: String) {
        imageView.tag = url
        (imageView.drawable as? Animatable)?.stop()
        if (url.startsWith("content://")) {
            setLocalPreview(imageView, url)
            return
        }
        byteCache.get(url)?.let { bytes ->
            setDecodedPreview(imageView, url, bytes)
            return
        }
        bitmapCache.get(url)?.let { bitmap ->
            imageView.setImageBitmap(bitmap)
            return
        }
        imageView.setImageDrawable(null)
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { downloadBytes(url) }
            if (imageView.tag == url && bytes != null) {
                byteCache.put(url, bytes)
                setDecodedPreview(imageView, url, bytes)
            }
        }
    }

    private fun setLocalPreview(imageView: ImageView, url: String) {
        try {
            val source = ImageDecoder.createSource(imageView.context.contentResolver, Uri.parse(url))
            val drawable = ImageDecoder.decodeDrawable(source)
            if (imageView.tag == url) {
                imageView.setImageDrawable(drawable)
                (drawable as? Animatable)?.start()
            }
        } catch (_: Exception) {
            imageView.setImageDrawable(null)
        }
    }

    private fun downloadBytes(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.bytes()
        }
    }

    private fun setDecodedPreview(imageView: ImageView, url: String, bytes: ByteArray) {
        val drawable = decodeDrawable(bytes)
        if (drawable != null) {
            if (imageView.tag == url) {
                imageView.setImageDrawable(drawable)
                (drawable as? Animatable)?.start()
            }
            return
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        bitmapCache.put(url, bitmap)
        if (imageView.tag == url) {
            imageView.setImageBitmap(bitmap)
        }
    }

    private fun decodeDrawable(bytes: ByteArray): Drawable? {
        return try {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            ImageDecoder.decodeDrawable(source)
        } catch (_: Exception) {
            null
        }
    }
}

private fun dpToPx(context: Context, dp: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        context.resources.displayMetrics
    ).toInt()
}
