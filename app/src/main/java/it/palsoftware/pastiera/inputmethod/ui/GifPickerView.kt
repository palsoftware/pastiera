package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.gif.KlipyGifClient
import it.palsoftware.pastiera.gif.KlipyGifResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
    private val resultAdapter = GifResultAdapter(onGifSelected)
    private val fixedHeight = dpToPx(177f)
    private val smallPadding = dpToPx(8f)
    private val previewSpacing = dpToPx(8f)
    private val columns = 3
    private var searchJob: Job? = null
    private var searchQuery = ""
    private var searchInputCaptureEnabled = true
    private var coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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
        vertical.addView(recyclerView)
        vertical.addView(attributionView)

        addView(vertical)
        addView(loadingView)
        addView(emptyView)

        refresh()
    }

    fun refresh() {
        if (!gifClient.hasConfiguredApiKey()) {
            showMessage(context.getString(R.string.gif_picker_missing_api_key))
            return
        }
        if (searchQuery.isBlank()) {
            resultAdapter.submitList(emptyList())
            showMessage(context.getString(R.string.gif_picker_empty_prompt))
            return
        }
        applySearchNow()
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
        coroutineScope.cancel()
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
            resultAdapter.submitList(emptyList())
            showMessage(context.getString(R.string.gif_picker_empty_prompt))
            return
        }

        loadingView.visibility = View.VISIBLE
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE

        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    gifClient.search(searchQuery)
                }
                loadingView.visibility = View.GONE
                if (results.isEmpty()) {
                    resultAdapter.submitList(emptyList())
                    showMessage(context.getString(R.string.gif_picker_no_results))
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    resultAdapter.submitList(results)
                    scrollToTop()
                }
            } catch (e: CancellationException) {
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
    private val onGifSelected: (KlipyGifResult) -> Unit
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
        return GifResultViewHolder(container, preview, title, onGifSelected)
    }

    override fun onBindViewHolder(holder: GifResultViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

private class GifResultViewHolder(
    itemView: View,
    private val previewView: ImageView,
    private val titleView: TextView,
    private val onGifSelected: (KlipyGifResult) -> Unit
) : RecyclerView.ViewHolder(itemView) {
    fun bind(item: KlipyGifResult) {
        titleView.text = item.title
        itemView.setOnClickListener { onGifSelected(item) }
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun loadInto(imageView: ImageView, url: String) {
        imageView.tag = url
        bitmapCache.get(url)?.let {
            imageView.setImageBitmap(it)
            return
        }
        imageView.setImageDrawable(null)
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) { downloadBitmap(url) }
            if (imageView.tag == url && bitmap != null) {
                bitmapCache.put(url, bitmap)
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun downloadBitmap(url: String): Bitmap? {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
