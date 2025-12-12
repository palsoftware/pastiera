package it.palsoftware.pastiera.inputmethod.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.clipboard.ClipboardHistoryEntry
import it.palsoftware.pastiera.clipboard.ClipboardHistoryManager

/**
 * Lightweight clipboard history UI that runs inside the SYM layout page.
 * Header + RecyclerView so we keep the fixed height + scrollable behavior.
 */
class ClipboardHistoryView(
    context: Context,
    private val clipboardHistoryManager: ClipboardHistoryManager
) : FrameLayout(context) {

    private val adapter = ClipboardHistoryAdapter()
    private val recyclerView: RecyclerView
    private val emptyStateView: TextView
    private val clearButton: TextView
    private var currentInputConnection: InputConnection? = null
    private val entryHeightPx: Int
    private var scrollToTopPending: Boolean = false

        init {
        // Use FrameLayout for two-level layout: header on top, scrollable content below
        setBackgroundColor(Color.TRANSPARENT)
        
        // Fixed height for the entire view - anchored to bottom above LEDs
        val fixedHeight = dpToPx(320f)
        setPadding(0, 0, 0, 0)
        entryHeightPx = dpToPx(64f)

        val smallPadding = dpToPx(8f)

        // Header positioned at top of the container
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
            // Minimal padding
            setPadding(smallPadding, dpToPx(8f), smallPadding, dpToPx(4f))
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }

        val titleText = TextView(context).apply {
            text = context.getString(R.string.clipboard_history_title)
            textSize = 12f
            setTextColor(Color.argb(180, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        clearButton = TextView(context).apply {
            text = context.getString(R.string.clipboard_clear_all)
            textSize = 12f
            setTextColor(Color.parseColor("#FF6B6B"))
            isClickable = true
            isFocusable = true
            val padding = dpToPx(8f)
            setPadding(padding, padding / 2, padding, padding / 2)
            setOnClickListener {
                clipboardHistoryManager.clearHistory()
                refresh()
            }
        }

        header.addView(titleText)
        header.addView(clearButton)
        addView(header)

        // Scrollable content container below the header (margin set after header is measured)
        val scrollContainer = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }

        emptyStateView = TextView(context).apply {
            text = context.getString(R.string.clipboard_empty_state)
            textSize = 14f
            setTextColor(Color.argb(128, 255, 255, 255))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 3, RecyclerView.VERTICAL, false)
            adapter = this@ClipboardHistoryView.adapter
            clipToPadding = false  // Important: allows scrolling into padding area
            overScrollMode = View.OVER_SCROLL_ALWAYS
            setHasFixedSize(false)
            // Add minimal horizontal/top padding and bottom padding (double entry height)
            // This ensures we can always scroll to the last entry, even when new rows are added
            setPadding(smallPadding, smallPadding, smallPadding, entryHeightPx * 2)
            isNestedScrollingEnabled = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Spacing between items (no outer left/right padding)
        val spanCount = 3
        val spacingPx = dpToPx(4f)
        recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val pos = parent.getChildAdapterPosition(view)
                if (pos == RecyclerView.NO_POSITION) return

                val column = pos % spanCount

                // Inner spacing only, no outer edge padding
                outRect.left = if (column == 0) 0 else spacingPx / 2
                outRect.right = if (column == spanCount - 1) 0 else spacingPx / 2
                outRect.top = spacingPx / 2
                outRect.bottom = spacingPx / 2
                
                // No special handling for last row - padding is handled by RecyclerView padding
            }
        })

        scrollContainer.addView(emptyStateView)
        scrollContainer.addView(recyclerView)
        addView(scrollContainer)

        // After layout, place the scroll container below the real header height
        header.post {
            val lp = scrollContainer.layoutParams as? FrameLayout.LayoutParams ?: return@post
            lp.topMargin = header.height
            scrollContainer.layoutParams = lp
            scrollContainer.requestLayout()
        }
        
        // Set fixed height for the entire view to anchor it above LEDs
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            fixedHeight
        )

        refresh()
    }

    fun setInputConnection(connection: InputConnection?) {
        currentInputConnection = connection
    }

    fun refresh() {
        clipboardHistoryManager.prepareClipboardHistory()
        val entries = loadEntries()
        adapter.submitList(entries) {
            // Recalculate item decorations so spacing is correct after insertions
            recyclerView.invalidateItemDecorations()
            if (scrollToTopPending) {
                recyclerView.post {
                    recyclerView.scrollToPosition(0)
                    scrollToTopPending = false
                }
            }
        }
        val hasEntries = entries.isNotEmpty()
        emptyStateView.visibility = if (hasEntries) View.GONE else View.VISIBLE
        recyclerView.visibility = if (hasEntries) View.VISIBLE else View.GONE
        clearButton.isEnabled = hasEntries
    }

    private fun loadEntries(): List<ClipboardHistoryEntry> {
        // Return immutable snapshots to let DiffUtil detect changes (isPinned, timeStamp, text)
        val size = clipboardHistoryManager.getHistorySize()
        val entries = mutableListOf<ClipboardHistoryEntry>()
        for (index in 0 until size) {
            clipboardHistoryManager.getHistoryEntry(index)?.let { entries.add(it.copy()) }
        }
        return entries
    }

    private fun showClipboardContextMenu(view: View, entry: ClipboardHistoryEntry) {
        val menu = PopupMenu(context, view)
        val pinText = context.getString(R.string.clipboard_pin)
        val unpinText = context.getString(R.string.clipboard_unpin)
        val deleteText = context.getString(R.string.clipboard_delete)

        if (entry.isPinned) {
            menu.menu.add(unpinText)
        } else {
            menu.menu.add(pinText)
        }
        menu.menu.add(deleteText)

        menu.setOnMenuItemClickListener { item ->
            when (item.title.toString()) {
                pinText, unpinText -> {
                    clipboardHistoryManager.toggleClipPinned(entry.id)
                    scrollToTopPending = true
                    // Post refresh to ensure manager has updated the entry
                    recyclerView.post {
                        refresh()
                    }
                    true
                }
                deleteText -> {
                    val index = (0 until clipboardHistoryManager.getHistorySize()).firstOrNull { idx ->
                        clipboardHistoryManager.getHistoryEntry(idx)?.id == entry.id
                    }
                    index?.let {
                        clipboardHistoryManager.removeEntry(it, force = true)
                        refresh()
                    }
                    true
                }
                else -> false
            }
        }
        menu.show()
    }

    private fun onEntryClicked(entry: ClipboardHistoryEntry) {
        currentInputConnection?.commitText(entry.text, 1)
    }

    private fun createRoundedBackground(isPinned: Boolean = false): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            // Use different color for pinned entries (e.g., slightly yellow tint)
            val color = if (isPinned) {
                Color.argb(60, 7, 7, 212) // rgb(7 7 212) tint for pinned entries
            } else {
                Color.argb(40, 255, 255, 255) // Default white tint
            }
            setColor(color)
            cornerRadius = dpToPx(6f).toFloat()
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private inner class ClipboardHistoryViewHolder(
        itemView: View,
        val textView: TextView
    ) : RecyclerView.ViewHolder(itemView)

    private inner class ClipboardHistoryAdapter :
        ListAdapter<ClipboardHistoryEntry, ClipboardHistoryViewHolder>(DiffCallback) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardHistoryViewHolder {
            val container = FrameLayout(context).apply {
                // Default background, will be updated in onBindViewHolder based on isPinned
                background = createRoundedBackground(false)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    entryHeightPx
                )
                val padding = dpToPx(12f)
                setPadding(padding, padding, padding, padding)
                isClickable = true
                isFocusable = true
                isLongClickable = true
            }

            val textView = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                textSize = 14f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(Color.WHITE)
            }

            container.addView(textView)

            return ClipboardHistoryViewHolder(container, textView)
        }

        override fun onBindViewHolder(holder: ClipboardHistoryViewHolder, position: Int) {
            val entry = getItem(position)
            holder.textView.text = entry.text
            
            // Update background color based on pinned status
            holder.itemView.background = createRoundedBackground(entry.isPinned)

            holder.itemView.setOnClickListener {
                onEntryClicked(entry)
            }

            holder.itemView.setOnLongClickListener { view ->
                showClipboardContextMenu(view, entry)
                true
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ClipboardHistoryEntry>() {
        override fun areItemsTheSame(oldItem: ClipboardHistoryEntry, newItem: ClipboardHistoryEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ClipboardHistoryEntry, newItem: ClipboardHistoryEntry): Boolean {
            return oldItem.timeStamp == newItem.timeStamp &&
                oldItem.isPinned == newItem.isPinned &&
                oldItem.text == newItem.text
        }
    }
}

