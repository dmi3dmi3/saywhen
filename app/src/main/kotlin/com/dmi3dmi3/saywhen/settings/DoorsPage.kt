package com.dmi3dmi3.saywhen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dmi3dmi3.saywhen.R

@Composable
internal fun DoorsPage(
    canPinWidget: Boolean,
    iconEnabled: Boolean,
    insertEnabled: Boolean,
    onPinWidget: () -> Unit,
    onSetIcon: (Boolean) -> Unit,
    onSetInsert: (Boolean) -> Unit,
) {
    GroupCard {
        if (canPinWidget) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.door_widget), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.door_widget_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(onClick = onPinWidget) { Text(stringResource(R.string.action_add)) }
            }
        }
        SwitchRow(R.string.launch_icon, iconEnabled, hintRes = R.string.launch_icon_hint, onCheckedChange = onSetIcon)
        SwitchRow(R.string.launch_insert, insertEnabled, hintRes = R.string.launch_insert_hint, onCheckedChange = onSetInsert)
    }
}
