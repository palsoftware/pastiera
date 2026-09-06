package it.palsoftware.pastiera.inputmethod

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import it.palsoftware.pastiera.clipboard.ClipboardHistoryManager
import it.palsoftware.pastiera.inputmethod.ui.ClipboardHistoryView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ClipboardRoundedLayoutTest {
    @Test
    fun roundedModeUsesSharedCloseButPreservesCardSpacing() {
        val context = RuntimeEnvironment.getApplication()
        val view = ClipboardHistoryView(context, ClipboardHistoryManager(context))
        fun descendants(group: ViewGroup): List<View> = (0 until group.childCount).flatMap {
            val child = group.getChildAt(it)
            listOf(child) + if (child is ViewGroup) descendants(child) else emptyList()
        }
        val children = descendants(view)
        val recycler = children.filterIsInstance<RecyclerView>().single()
        val close = children.filterIsInstance<ImageView>().single()
        val originalPadding = recycler.paddingBottom
        val originalSidePadding = recycler.paddingLeft
        view.configureRoundedLayout(true)
        assertEquals(View.GONE, close.visibility)
        assertEquals(originalSidePadding, recycler.paddingLeft)
        assertEquals(originalSidePadding, recycler.paddingRight)
        assertEquals(originalSidePadding, recycler.paddingTop)
        assertEquals(originalPadding, recycler.paddingBottom)
        view.configureRoundedLayout(false)
        assertEquals(View.VISIBLE, close.visibility)
        assertTrue(recycler.paddingLeft > 0)
        assertEquals(originalPadding, recycler.paddingBottom)
    }
}
