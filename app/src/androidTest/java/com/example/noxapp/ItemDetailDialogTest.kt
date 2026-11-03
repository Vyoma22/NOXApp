package com.example.noxapp

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.noxapp.models.Listing
import com.example.noxapp.ui.ItemDetailDialog
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.containsString

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
}
