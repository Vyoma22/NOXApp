package com.example.noxapp

import android.content.Context
import android.content.Intent
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemovedPickupPreferenceUiTest {
    @Test
    fun profileLayoutOmitsSettingsAction() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_NOXApp)
            val root = LayoutInflater.from(context).inflate(R.layout.activity_profile, null)
            val actionId = root.resources.getIdentifier("profileSettingsButton", "id", context.packageName)

            assertTrue("profileSettingsButton must be removed", actionId == 0)
        }
    }

    @Test
    fun mapActivityOmitsSavePreferenceAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MapActivity::class.java)
            .putExtra(MapActivity.EXTRA_CENTER_LAT, 43.9483414)
            .putExtra(MapActivity.EXTRA_CENTER_LNG, -78.8982025)
            .putExtra(MapActivity.EXTRA_RADIUS_KM, 3.5)

        ActivityScenario.launch<MapActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val actionId = activity.resources.getIdentifier("savePreferenceButton", "id", activity.packageName)
                assertTrue("savePreferenceButton must be removed", actionId == 0)
            }
        }
    }
}
