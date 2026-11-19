package com.example.noxapp

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ActivityTransitionsTest {
    @Test
    fun allActivityThemesDisableOpenAndCloseAnimations() {
        assertInstantActivityTransitions()
    }

    @Test
    @Config(qualifiers = "night")
    fun darkModeKeepsActivityTransitionsInstant() {
        assertInstantActivityTransitions()
    }

    @Test
    fun bottomSheetKeepsItsOwnWindowAnimations() {
        val context = themedContext(R.style.Theme_NOXApp)
        val dialog = BottomSheetDialog(context)
        val attributes = intArrayOf(
            android.R.attr.windowEnterAnimation,
            android.R.attr.windowExitAnimation
        )
        val animations = dialog.context.obtainStyledAttributes(windowAnimationStyle(dialog.context), attributes)
        try {
            for (index in attributes.indices) {
                assertNotEquals("Bottom sheet motion must remain enabled", 0, animations.getResourceId(index, 0))
            }
        } finally {
            animations.recycle()
        }
    }

    private fun assertInstantActivityTransitions() {
        val themes = intArrayOf(
            R.style.Theme_NOXApp,
            R.style.Theme_NOXApp_Auth,
            R.style.Theme_NOXApp_Splash,
            R.style.Theme_NOXApp_MediaViewer
        )
        val attributes = intArrayOf(
            android.R.attr.activityOpenEnterAnimation,
            android.R.attr.activityOpenExitAnimation,
            android.R.attr.activityCloseEnterAnimation,
            android.R.attr.activityCloseExitAnimation
        )
        for (theme in themes) {
            val context = themedContext(theme)
            val animations = context.obtainStyledAttributes(windowAnimationStyle(context), attributes)
            try {
                for (index in attributes.indices) {
                    assertEquals(
                        "${context.resources.getResourceEntryName(theme)}: ${context.resources.getResourceEntryName(attributes[index])}",
                        0,
                        animations.getResourceId(index, 0)
                    )
                }
            } finally {
                animations.recycle()
            }
        }
    }

    private fun themedContext(theme: Int): Context =
        ContextThemeWrapper(ApplicationProvider.getApplicationContext(), theme)

    private fun windowAnimationStyle(context: Context): Int {
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.windowAnimationStyle))
        return try {
            val style = attributes.getResourceId(0, 0)
            assertNotEquals("Keep a window animation style instead of disabling all window effects", 0, style)
            style
        } finally {
            attributes.recycle()
        }
    }
}
