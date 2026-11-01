package com.example.noxapp.ui

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.view.children

private const val ENTER_DURATION_MS = 280L
private const val ENTER_STAGGER_CAP_MS = 240L

/**
 * Respects the system "Remove animations" setting. Without this guard a disabled animator can
 * leave a view stranded at its pre-animation alpha.
 */
private fun animationsEnabled(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ValueAnimator.areAnimatorsEnabled()
    } else {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) != 0f
    }
}

/**
 * Fades and lifts each child into place, one after another.
 *
 * Call it when a list first receives data; running it on every filter keystroke turns polish
 * into flicker. Children are captured on the next frame so a caller can notify its adapter first.
 */
fun ViewGroup.animateChildrenIn(staggerMs: Long = 40L, riseDp: Float = 12f) {
    if (!animationsEnabled(context)) {
        children.forEach {
            it.animate().cancel()
            it.alpha = 1f
            it.translationY = 0f
        }
        return
    }
    post {
        val rise = riseDp * resources.displayMetrics.density
        children.forEachIndexed { index, child ->
            child.animate().cancel()
            child.alpha = 0f
            child.translationY = rise
            child.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((index * staggerMs).coerceAtMost(ENTER_STAGGER_CAP_MS))
                .setDuration(ENTER_DURATION_MS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}

/** Scales up from slightly small so a tap lands with a spring instead of a hard state flip. */
fun View.pop() {
    if (!animationsEnabled(context)) {
        scaleX = 1f
        scaleY = 1f
        return
    }
    animate().cancel()
    scaleX = 0.8f
    scaleY = 0.8f
    animate()
        .scaleX(1f)
        .scaleY(1f)
        .setDuration(260L)
        .setInterpolator(OvershootInterpolator(2.5f))
        .start()
}

/** Shows or hides with a short fade, so empty and error states do not snap in or out. */
fun View.fadeVisibility(visible: Boolean) {
    if (!animationsEnabled(context)) {
        animate().cancel()
        alpha = 1f
        visibility = if (visible) View.VISIBLE else View.GONE
        return
    }
    animate().cancel()
    if (visible) {
        if (visibility != View.VISIBLE) {
            visibility = View.VISIBLE
            alpha = 0f
        }
        animate().alpha(1f).setDuration(180L).setInterpolator(DecelerateInterpolator()).start()
    } else if (visibility == View.VISIBLE) {
        animate().alpha(0f).setDuration(120L).withEndAction {
            // A fade-in that started during this fade-out already restored the view.
            if (alpha == 0f) visibility = View.GONE
        }.start()
    }
}
