package com.fixlens.app.ui.screens

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Tactile feedback helpers (view-level haptics; no vibration permission
 * needed for these feedback constants). Used on step confirmation,
 * verification PASS, completion, and the safety stop so key moments are
 * felt as well as seen and spoken.
 */

fun View.hapticTick() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

fun View.hapticConfirm() {
    if (Build.VERSION.SDK_INT >= 30) {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

fun View.hapticReject() {
    if (Build.VERSION.SDK_INT >= 30) {
        performHapticFeedback(HapticFeedbackConstants.REJECT)
    } else {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}
