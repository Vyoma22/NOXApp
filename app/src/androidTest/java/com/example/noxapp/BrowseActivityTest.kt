package com.example.noxapp

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.noxapp.adapters.PillChipAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** View-only tests deliberately never launch activities, authenticate, or access Firestore. */
@RunWith(AndroidJUnit4::class)
class BrowseActivityTest {
    private fun withLayout(configure: (View) -> Unit = {}, check: (View) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_NOXApp)
            val root = LayoutInflater.from(context).inflate(R.layout.activity_browse, null)
            root.findViewById<RecyclerView>(R.id.browseCategories).layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            configure(root)
            val density = context.resources.displayMetrics.density
            root.measure(
                View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            check(root)
        }
    }

    @Test
    fun narrowLayoutKeepsFiltersScrollableAndTouchTargetsAccessible() = withLayout(configure = {
        it.findViewById<TextView>(R.id.browsePriceFilter).text = "Price: $25 - $50"
        it.findViewById<TextView>(R.id.browseCategoryFilter).text = "Category: Electronics"
    }) { root ->
        val density = root.resources.displayMetrics.density
        val strip = root.findViewById<HorizontalScrollView>(R.id.browseFilterScroll)
        assertTrue(strip.getChildAt(0).measuredWidth > strip.measuredWidth)
        listOf(
            R.id.browseCategoryFilter, R.id.browsePriceFilter, R.id.browseConditionFilter,
            R.id.browseBackButton, R.id.browseMapButton
        ).forEach { id ->
            val target = root.findViewById<View>(id)
            assertTrue("Target $id must be at least 48dp tall", target.measuredHeight >= 48 * density)
        }
        listOf(R.id.browseBackButton, R.id.browseMapButton, R.id.browseClearRadius).forEach {
            assertFalse(root.findViewById<View>(it).contentDescription.isNullOrBlank())
        }
        assertTrue(root.findViewById<View>(R.id.browseClearRadius).minimumHeight >= 48 * density)
    }

    @Test
    fun initialLayoutHasRestorableScrollAndNoRadiusOrEmptyMessage() = withLayout { root ->
        val scroll = root.findViewById<NestedScrollView>(R.id.browseScroll)
        assertNotEquals(View.NO_ID, scroll.id)
        assertTrue(scroll.isSaveEnabled)
        assertFalse(root.findViewById<RecyclerView>(R.id.browseResults).isNestedScrollingEnabled)
        assertEquals(View.GONE, root.findViewById<View>(R.id.browseRadiusPill).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.browseEmptyState).visibility)
        assertEquals("Category", root.findViewById<TextView>(R.id.browseCategoryFilter).text.toString())
        assertEquals("Price", root.findViewById<TextView>(R.id.browsePriceFilter).text.toString())
        assertEquals("Condition", root.findViewById<TextView>(R.id.browseConditionFilter).text.toString())
        assertEquals("1 result", root.resources.getQuantityString(R.plurals.browse_result_count, 1, 1))
        assertEquals("0 results", root.resources.getQuantityString(R.plurals.browse_result_count, 0, 0))
        assertEquals("12 results", root.resources.getQuantityString(R.plurals.browse_result_count, 12, 12))
    }

    @Test
    fun initialCategoryRowSelectsOnlyAll() = withLayout { root ->
        val adapter = PillChipAdapter({}, {})
        val chips = bindChips(root, adapter)
        assertEquals(listOf("All", "Books", "Electronics", "Furniture", "More"), chips.map { it.text.toString() })
        assertEquals(listOf("All"), chips.filter { it.isSelected }.map { it.text.toString() })
        assertEquals(ContextCompat.getColor(root.context, R.color.white), chips.first().currentTextColor)
    }

    @Test
    fun moreExpandsInlineAndLessPreservesHiddenSelection() = withLayout { root ->
        var category: String? = null
        var expanded = false
        lateinit var adapter: PillChipAdapter
        adapter = PillChipAdapter(
            { category = it; adapter.updateState(category, expanded) },
            { expanded = !expanded; adapter.updateState(category, expanded) }
        )
        bindChips(root, adapter).last().performClick()
        var chips = bindChips(root, adapter)
        assertEquals(
            listOf("All", "Books", "Electronics", "Furniture", "Clothing", "Decor", "Accessories", "Tickets", "Other", "Less"),
            chips.map { it.text.toString() }
        )
        chips.single { it.text == "Tickets" }.performClick()
        chips = bindChips(root, adapter)
        assertEquals(listOf("Tickets"), chips.filter { it.isSelected }.map { it.text.toString() })
        chips.last().performClick()
        chips = bindChips(root, adapter)
        assertEquals("Tickets", category)
        assertEquals(5, chips.size)
        assertEquals(listOf("More"), chips.filter { it.isSelected }.map { it.text.toString() })
        assertTrue(chips.last().contentDescription.toString().contains("Tickets"))
        chips.last().performClick()
        assertEquals(listOf("Tickets"), bindChips(root, adapter).filter { it.isSelected }.map { it.text.toString() })
    }

    @Test
    fun externalCategorySelectionIsCaseInsensitiveAndAllClearsIt() = withLayout { root ->
        var category: String? = "books"
        lateinit var adapter: PillChipAdapter
        adapter = PillChipAdapter({ category = it; adapter.updateState(category, false) }, {})
        adapter.updateState(category, false)
        var chips = bindChips(root, adapter)
        assertEquals(listOf("Books"), chips.filter { it.isSelected }.map { it.text.toString() })
        chips.first().performClick()
        assertEquals(null, category)
        chips = bindChips(root, adapter)
        assertEquals(listOf("All"), chips.filter { it.isSelected }.map { it.text.toString() })
        adapter.updateState("decor", false)
        assertEquals(listOf("More"), bindChips(root, adapter).filter { it.isSelected }.map { it.text.toString() })
    }

    /**
     * Expanding and collapsing notifies the live RecyclerView with insert and remove ranges rather
     * than a blanket rebind. A wrong range still leaves the adapter looking right, so this scrolls
     * the row to the end and reads the labels RecyclerView itself mapped to each position.
     */
    @Test
    fun expandingAndCollapsingChipRowKeepsAdapterPositionsValid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_NOXApp)
            val root = LayoutInflater.from(context).inflate(R.layout.activity_browse, null)
            val chips = root.findViewById<RecyclerView>(R.id.browseCategories)
            chips.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            val adapter = PillChipAdapter({}, {})
            chips.adapter = adapter
            layout(root)

            adapter.updateState(null, true)
            layout(root)
            assertEquals(10, adapter.itemCount)
            chips.scrollToPosition(9)
            layout(root)
            assertEquals("Less", visibleChips(chips).last())
            assertEquals("Clothing", chipAt(adapter, chips, 4).text.toString())

            adapter.updateState("Tickets", false)
            layout(root)
            assertEquals(5, adapter.itemCount)
            chips.scrollToPosition(4)
            layout(root)
            assertEquals("More", visibleChips(chips).last())
            assertTrue(
                "A hidden selection is represented by More",
                chipAt(adapter, chips, 4).isSelected
            )
        }
    }

    private fun visibleChips(chips: RecyclerView): List<String> =
        (0 until chips.childCount).map { (chips.getChildAt(it) as TextView).text.toString() }

    private fun layout(root: View) {
        val density = root.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    // Binds [position] by hand: off-screen chips are never attached, so getChildAt cannot see them.
    private fun chipAt(adapter: PillChipAdapter, parent: RecyclerView, position: Int): TextView {
        val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(position))
        adapter.onBindViewHolder(holder, position)
        return holder.itemView as TextView
    }

    private fun bindChips(root: View, adapter: PillChipAdapter): List<TextView> {
        val parent = root.findViewById<RecyclerView>(R.id.browseCategories)
        return (0 until adapter.itemCount).map { position ->
            val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(position))
            adapter.onBindViewHolder(holder, position)
            holder.itemView as TextView
        }
    }
}
