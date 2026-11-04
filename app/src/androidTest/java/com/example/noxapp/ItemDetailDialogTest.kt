package com.example.noxapp

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.noxapp.models.Listing
import com.example.noxapp.ui.ItemDetailDialog
import org.hamcrest.Matchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemDetailDialogTest {
    @Test
    fun sharedDialogDisplaysListingAndCloses() {
        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            scenario.onActivity {
                ItemDetailDialog.show(it, Listing(
                    title = "Test textbook", price = 24.99, condition = "Used",
                    category = "Books", description = "Engineering", pickupLocation = "Durham College",
                    sellerName = "Sarah K."
                ))
            }
            onView(withId(R.id.detailTitle)).inRoot(isDialog()).check(matches(withText("Test textbook")))
            onView(withId(R.id.detailPrice)).inRoot(isDialog()).check(matches(withText("$24")))
            onView(withId(R.id.detailCondition)).inRoot(isDialog()).check(matches(withText("Used")))
            onView(withId(R.id.detailCategory)).inRoot(isDialog()).check(matches(withText("Books")))
            onView(withId(R.id.detailSellerInitial)).inRoot(isDialog()).check(matches(withText("S")))
            onView(withId(R.id.detailSellerName)).inRoot(isDialog()).check(matches(withText(containsString("Sarah K."))))
            onView(withId(R.id.detailSellerRating)).inRoot(isDialog()).check(matches(withText("New seller")))
            onView(withId(R.id.detailDescription)).inRoot(isDialog()).check(matches(withText("Engineering")))
            onView(withId(R.id.detailLocation)).inRoot(isDialog()).check(matches(withText("Pickup: Durham College")))
            onView(withId(R.id.detailClose)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.detailTitle)).check(doesNotExist())
        }
    }

    /** Fake URLs never load (no network in this test); the gallery behaviour is view-only. */
    @Test
    fun multiImageGalleryPagesWithArrowsAndCounter() {
        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            scenario.onActivity {
                ItemDetailDialog.show(it, Listing(
                    title = "Camera kit", price = 120.0, condition = "Used",
                    category = "Electronics", description = "Three photos",
                    pickupLocation = "Library", sellerName = "Alex M.",
                    imageUrls = listOf(
                        "https://example.com/one.jpg",
                        "https://example.com/two.jpg",
                        "https://example.com/three.jpg"
                    )
                ))
            }
            onView(withId(R.id.detailMediaCounter)).inRoot(isDialog())
                .check(matches(withText("1 / 3")))
            onView(withId(R.id.detailMediaNext)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.detailMediaCounter)).inRoot(isDialog())
                .check(matches(withText("2 / 3")))
            onView(withId(R.id.detailMediaPrev)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.detailMediaCounter)).inRoot(isDialog())
                .check(matches(withText("1 / 3")))
            onView(withId(R.id.detailClose)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.detailTitle)).check(doesNotExist())
        }
    }

    @Test
    fun singleImageShowsGalleryButHidesControls() {
        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            scenario.onActivity {
                ItemDetailDialog.show(it, Listing(
                    title = "Desk lamp", sellerName = "Alex M.",
                    imageUrls = listOf("https://example.com/lamp.jpg")
                ))
            }
            onView(withId(R.id.detailMediaPager)).inRoot(isDialog())
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            listOf(
                R.id.detailMediaCounter,
                R.id.detailMediaPrev,
                R.id.detailMediaNext,
                R.id.detailMediaThumbnails
            ).forEach { id ->
                onView(withId(id)).inRoot(isDialog())
                    .check(matches(withEffectiveVisibility(Visibility.GONE)))
            }
            onView(withId(R.id.detailClose)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.detailTitle)).check(doesNotExist())
        }
    }
}
