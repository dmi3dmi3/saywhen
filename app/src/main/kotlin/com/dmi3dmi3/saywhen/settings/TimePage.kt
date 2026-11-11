package com.dmi3dmi3.saywhen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dmi3dmi3.saywhen.R
import com.dmi3dmi3.saywhen.parser.resolveTwelveHour
import kotlin.math.roundToInt

@Composable
internal fun TimePage(
    windowStart: Int,
    windowEnd: Int,
    dotTime: Boolean,
    onSetWindow: (Int, Int) -> Unit,
    onSetDotTime: (Boolean) -> Unit,
) {
    Section(R.string.activity_window) {
        var range by remember {
            mutableStateOf(windowStart.toFloat()..windowEnd.toFloat())
        }
        val start = range.start.roundToInt()
        val end = range.endInclusive.roundToInt()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "$start:00", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "$end:00", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RangeSlider(
            value = range,
            onValueChange = { r ->
                val s = r.start.roundToInt()
                val e = r.endInclusive.roundToInt()
                if (e - s >= 1) range = s.toFloat()..e.toFloat()
            },
            valueRange = 0f..24f,
            steps = 23,
            onValueChangeFinished = { onSetWindow(start, end) },
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .semantics { stateDescription = "$start:00 – $end:00" },
        )
        Text(
            stringResource(
                R.string.activity_window_examples,
                resolveTwelveHour(9, start until end),
                resolveTwelveHour(7, start until end),
            ),
            Modifier.padding(start = 6.dp, top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
        )
        Hint(R.string.activity_window_hint)
    }
    GroupCard {
        SwitchRow(R.string.dot_time, dotTime, hintRes = R.string.dot_time_hint, onCheckedChange = onSetDotTime)
    }
}
