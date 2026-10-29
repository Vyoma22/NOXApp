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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            onView(withId(R.id.mapAreaAction)).check(matches(withText("Save preference")))
            pressBackUnconditionally()
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
    }
}
