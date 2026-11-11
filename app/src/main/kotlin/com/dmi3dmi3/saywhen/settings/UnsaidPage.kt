package com.dmi3dmi3.saywhen.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dmi3dmi3.saywhen.R

internal val DURATION_OPTIONS = listOf(30, 60, 90, 120)

internal val REMINDER_OPTIONS = listOf(5, 10, 30, 60)

@Composable
internal fun UnsaidPage(
    durationMinutes: Int,
    reminderMinutes: Int?,
    onSetDuration: (Int) -> Unit,
    onSetReminder: (Int?) -> Unit,
) {
    val hourUnit = stringResource(R.string.unit_hours)
    val minuteUnit = stringResource(R.string.unit_minutes)
    Section(R.string.settings_duration) {
        for (minutes in DURATION_OPTIONS) {
            RadioRow(
                name = durationLabel(minutes, hourUnit, minuteUnit),
                detail = null, color = null,
                selected = durationMinutes == minutes,
                onSelect = { onSetDuration(minutes) },
            )
        }
    }
    Section(R.string.settings_reminder) {
        val options: List<Pair<Int?, String>> = listOf(
            null to stringResource(R.string.reminder_off),
            0 to stringResource(R.string.reminder_at_event),
        ) + REMINDER_OPTIONS.map { it to stringResource(R.string.reminder_before, durationLabel(it, hourUnit, minuteUnit)) }
        for ((value, label) in options) {
            RadioRow(
                name = label,
                detail = null, color = null,
                selected = reminderMinutes == value,
                onSelect = { onSetReminder(value) },
            )
        }
        Hint(R.string.reminder_hint)
    }
}
