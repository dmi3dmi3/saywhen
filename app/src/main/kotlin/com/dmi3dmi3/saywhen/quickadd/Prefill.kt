package com.dmi3dmi3.saywhen.quickadd

import android.content.Intent

internal fun prefillFor(action: String?, prefill: String?, insertTitle: String?): String? =
    prefill ?: insertTitle.takeIf { action == Intent.ACTION_INSERT }
        ?.trim()?.takeIf { it.isNotEmpty() }?.let { "$it " }
