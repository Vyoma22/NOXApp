package com.example.noxapp

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.view.View
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.RecyclerView
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapActivityTest {
    private fun mapIntent() = Intent(ApplicationProvider.getApplicationContext(), MapActivity::class.java)
        .putExtra(MapActivity.EXTRA_CENTER_LAT, 43.9483414)
        .putExtra(MapActivity.EXTRA_CENTER_LNG, -78.8982025)
        .putExtra(MapActivity.EXTRA_RADIUS_KM, 3.5)

    @Test
    fun untouchedBackReturnsNoSelection() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            onView(withId(R.id.radiusLabel)).check(matches(withText("Radius: 3.5 km")))
            pressBackUnconditionally()
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertFalse(scenario.result.resultData.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, true))
        }
    }

    @Test
    fun seeAllReturnsCurrentAreaAfterRecreation() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            scenario.recreate()
            onView(withId(R.id.radiusLabel)).check(matches(withText("Radius: 3.5 km")))
            onView(withId(R.id.mapAreaAction)).perform(click())
            val result = scenario.result.resultData
            assertTrue(result.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, false))
            assertEquals(3.5, result.getDoubleExtra(MapActivity.EXTRA_RADIUS_KM, 0.0), 0.0)
            assertEquals(43.9483414, result.getDoubleExtra(MapActivity.EXTRA_CENTER_LAT, 0.0), 0.0)
        }
    }

    @Test
    fun landscapeKeepsAreaActionReachable() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            onView(withId(R.id.mapItemsScroll)).check { view, error ->
                if (error != null) throw error
                assertTrue("Area panel must expose a complete touch target",
                    view.height - view.paddingTop >= 48 * view.resources.displayMetrics.density)
            }
            onView(withId(R.id.mapAreaAction)).perform(click())
            assertTrue(scenario.result.resultData.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, false))
        }
    }

    @Test
    fun keyboardSliderSelectionSurvivesRecreation() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            scenario.onActivity {
                it.findViewById<View>(R.id.radiusSlider).apply {
                    isFocusableInTouchMode = true
                    assertTrue(requestFocus())
                }
            }
            onView(withId(R.id.radiusSlider)).perform(pressKey(KeyEvent.KEYCODE_DPAD_RIGHT))
            onView(withId(R.id.radiusLabel)).check(matches(withText("Radius: 4 km")))
            scenario.recreate()
            pressBackUnconditionally()
            assertTrue(scenario.result.resultData.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, false))
            assertEquals(4.0, scenario.result.resultData.getDoubleExtra(MapActivity.EXTRA_RADIUS_KM, 0.0), 0.0)
        }
    }

    @Test
    fun profileNavigationReturnsAreaBeforeOpeningProfile() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            onView(withId(R.id.mNavProfile)).perform(click())
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals("profile", scenario.result.resultData.getStringExtra("navigation"))
            assertFalse(scenario.result.resultData.getBooleanExtra(MapActivity.EXTRA_HAS_SELECTION, true))
            assertEquals(3.5, scenario.result.resultData.getDoubleExtra(MapActivity.EXTRA_RADIUS_KM, 0.0), 0.0)
        }
    }

    @Test
    fun homeNavigationReturnsAreaBeforeClearingBrowse() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            onView(withId(R.id.mNavHome)).perform(click())
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals("home", scenario.result.resultData.getStringExtra("navigation"))
        }
    }

    @Test
    fun preferenceModeBackDiscardsWithoutResult() {
        val intent = mapIntent().putExtra(MapActivity.EXTRA_MODE, MapActivity.MODE_PREFERENCE)
        ActivityScenario.launchActivityForResult<MapActivity>(intent).use { scenario ->
            onView(withText("Save preference")).check(matches(isDisplayed()))
            pressBackUnconditionally()
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
    }

    @Test
    fun preferenceModeOmitsItemBrowsing() {
        val intent = mapIntent().putExtra(MapActivity.EXTRA_MODE, MapActivity.MODE_PREFERENCE)
        ActivityScenario.launchActivityForResult<MapActivity>(intent).use { scenario ->
            onView(withText("Items in This Area")).check(matches(not(isDisplayed())))
            onView(withId(R.id.mapItemsRecycler)).check(matches(not(isDisplayed())))
            onView(withId(R.id.searchAreaButton)).check(matches(not(isDisplayed())))
            scenario.onActivity {
                assertNull(it.findViewById<RecyclerView>(R.id.mapItemsRecycler).adapter)
            }
            scenario.recreate()
            onView(withText("Save preference")).check(matches(isDisplayed()))
            onView(withText("Items in This Area")).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun searchAreaIsGroupedBelowRadiusSlider() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            scenario.onActivity {
                val card = it.findViewById<View>(R.id.radiusCard)
                val slider = card.findViewById<View>(R.id.radiusSlider)
                val action = card.findViewById<View>(R.id.searchAreaButton)
                assertTrue("Search action must be in the radius card", action != null)
                assertTrue("Search action must be below the slider", action.top >= slider.bottom)
            }
        }
    }

    @Test
    fun centerPinTargetsViewportCenterInBothOrientations() {
        ActivityScenario.launchActivityForResult<MapActivity>(mapIntent()).use { scenario ->
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                scenario.onActivity { it.requestedOrientation = orientation }
                onView(withContentDescription("Map center")).check(matches(isDisplayed()))
                scenario.onActivity {
                    val map = it.findViewById<View>(R.id.campusMap)
                    val pin = it.findViewById<View>(R.id.mapCenterPin)
                    assertEquals(map.x + map.width / 2f, pin.x + pin.width / 2f, 1f)
                    assertEquals(map.y + map.height / 2f, pin.y + pin.height, 1f)
                    assertFalse("The pin must not intercept map gestures", pin.isClickable)
                }
            }
        }
    }

    @Test
    fun landscapePreferenceKeepsSaveReachableWithoutItems() {
        val intent = mapIntent().putExtra(MapActivity.EXTRA_MODE, MapActivity.MODE_PREFERENCE)
        ActivityScenario.launchActivityForResult<MapActivity>(intent).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            onView(withText("Save preference")).check(matches(isDisplayed()))
            onView(withText("Items in This Area")).check(matches(not(isDisplayed())))
        }
    }
}
