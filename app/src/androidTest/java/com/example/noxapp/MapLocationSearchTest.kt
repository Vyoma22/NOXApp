package com.example.noxapp

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matcher
import org.hamcrest.Matchers.containsString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapLocationSearchTest {
    private fun mapIntent() = Intent(ApplicationProvider.getApplicationContext(), MapActivity::class.java)
        .putExtra(MapActivity.EXTRA_CENTER_LAT, 43.9483414)
        .putExtra(MapActivity.EXTRA_CENTER_LNG, -78.8982025)
        .putExtra(MapActivity.EXTRA_RADIUS_KM, 3.5)

    @Test
    fun blankSearchKeepsAreaUnchanged() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            onView(withHint("Search for a place or address")).perform(replaceText("   "), closeSoftKeyboard())
            onView(withContentDescription("Search location")).perform(click())
            onView(withHint("Search for a place or address"))
                .check(matches(hasErrorText("Enter a place or address")))
            pressBackUnconditionally()
            assertFalse(scenario.result.resultData.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, true))
            // Delta 0.001 (~110m tolerance) accounts for projection precision drift
            assertEquals(43.9483414, scenario.result.resultData.getDoubleExtra(MapActivity.EXTRA_CENTER_LAT, 0.0), 0.001)
        }
    }

    @Test
    fun searchQuerySurvivesRecreation() {
        ActivityScenario.launch<MapActivity>(mapIntent()).use { scenario ->
            onView(withHint("Search for a place or address")).perform(replaceText("Ottawa, Ontario"), closeSoftKeyboard())
            scenario.recreate()
            onView(withHint("Search for a place or address")).check(matches(withText("Ottawa, Ontario")))
        }
    }

    @Test
    fun locationResultMovesMapAndBrowseReturnsThatLocation() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            onView(withHint("Search for a place or address"))
                .perform(replaceText("Parliament Hill, Ottawa, Ontario, Canada"), closeSoftKeyboard())
            onView(withContentDescription("Search location")).perform(click())

            waitFor(withText("Choose a location"))
            onView(withText(containsString("Ottawa"))).inRoot(isDialog()).perform(click())

            // Wait for the dialog to dismiss and the activity window to regain focus
            waitFor(withId(R.id.searchAreaButton))

            onView(withId(R.id.searchAreaButton)).perform(scrollTo(), click())

            val result = scenario.result.resultData
            assertTrue(result.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, false))
            assertEquals(45.424, result.getDoubleExtra(MapActivity.EXTRA_CENTER_LAT, 0.0), 0.02)
            assertEquals(-75.699, result.getDoubleExtra(MapActivity.EXTRA_CENTER_LNG, 0.0), 0.02)
            assertEquals(3.5, result.getDoubleExtra(MapActivity.EXTRA_RADIUS_KM, 0.0), 0.0)
        }
    }
    @Test
    fun choosingLocationDoesNotApplyBrowseAreaUntilRequested() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            onView(withHint("Search for a place or address"))
                .perform(replaceText("Parliament Hill, Ottawa, Ontario, Canada"), closeSoftKeyboard())
            onView(withContentDescription("Search location")).perform(click())
            waitFor(withText("Choose a location"))
            onView(withText(containsString("Ottawa"))).inRoot(isDialog()).perform(click())
            pressBackUnconditionally()
            assertFalse(scenario.result.resultData.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, true))
        }
    }

    @Test
    fun unmatchedSearchShowsFeedbackWithoutChangingArea() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            onView(withHint("Search for a place or address"))
                .perform(replaceText("zzzxqv987654321nowhere"), closeSoftKeyboard())
            onView(withContentDescription("Search location")).perform(click())
            waitFor(withText("No locations found. Try a different search."))
            pressBackUnconditionally()
            assertFalse(scenario.result.resultData.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, true))
            // Delta 0.001 accounts for projection precision drift
            assertEquals(43.9483414, scenario.result.resultData.getDoubleExtra(MapActivity.EXTRA_CENTER_LAT, 0.0), 0.001)
        }
    }

    private fun waitFor(matcher: Matcher<View>) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                onView(matcher).check(matches(isDisplayed()))
                return
            } catch (_: NoMatchingViewException) {
                SystemClock.sleep(50)
            } catch (_: AssertionError) {
                SystemClock.sleep(50)
            }
        }
        onView(matcher).check(matches(isDisplayed()))
    }
}