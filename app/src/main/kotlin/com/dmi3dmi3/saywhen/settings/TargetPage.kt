@file:OptIn(ExperimentalMaterial3Api::class)

package com.dmi3dmi3.saywhen.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dmi3dmi3.saywhen.R
import com.dmi3dmi3.saywhen.calendar.CalendarInfo
import com.dmi3dmi3.saywhen.calendar.pickDefaultCalendar
import com.dmi3dmi3.saywhen.quickadd.ErrorAction
import com.dmi3dmi3.saywhen.quickadd.ErrorActionButton
import com.dmi3dmi3.saywhen.target.TASKS_KEY
import com.dmi3dmi3.saywhen.target.TASKS_ROOT
import com.dmi3dmi3.saywhen.target.targetsOf
import com.dmi3dmi3.saywhen.target.tasksKey
import com.dmi3dmi3.saywhen.tasks.TaskListInfo

internal enum class TasksStatus { NOT_INSTALLED, NO_ACCESS }

@Composable
internal fun TargetPage(
    calendars: List<CalendarInfo>?,
    taskLists: List<TaskListInfo>?,
    tasksEnabled: Boolean,
    tasksStatus: TasksStatus?,
    tasksNoDueDate: Boolean,
    selectedKey: String?,
    manualAliases: Map<String, String>,
    onSelect: (String?) -> Unit,
    onAlias: (String, String?) -> Unit,
    onRequestPermission: () -> Unit,
    onSetTasks: (Boolean) -> Unit,
    onSetNoDueDate: (Boolean) -> Unit,
) {
    val targets = targetsOf(calendars, taskLists, tasksEnabled)
    val derived = derivedAliases(targets)
    val effective = effectiveAliases(targets, manualAliases)

    @Composable
    fun aliasField(key: String) = AliasField(
        alias = effective.getValue(key),
        derived = derived.getValue(key),
        taken = effective.filterKeys { it != key }.values.toSet(),
        onCommit = { onAlias(key, it) },
    )

    Hint(R.string.target_hint, Modifier.padding(start = 6.dp))
    GroupCard {
        RadioRow(
            name = stringResource(R.string.settings_calendar_auto),
            detail = calendars?.let { pickDefaultCalendar(it) }?.name,
            color = null,
            selected = selectedKey == null,
            onSelect = { onSelect(null) },
        )
        when (val list = calendars) {
            null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_calendar_permission),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onRequestPermission) {
                    Text(stringResource(R.string.action_grant))
                }
                ErrorActionButton(ErrorAction.APP_SETTINGS)
            }
            else -> if (list.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.settings_no_calendars),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ErrorActionButton(ErrorAction.ADD_ACCOUNT)
                }
            } else {
                for (cal in list) {
                    val key = calendarKey(cal.id)
                    RadioRow(
                        name = cal.name,
                        detail = cal.accountName.takeIf { it != cal.name },
                        color = cal.color.takeIf { it != 0 },
                        selected = selectedKey == key,
                        onSelect = { onSelect(key) },
                    )
                    aliasField(key)
                }
            }
        }
    }

    GroupCard {
        SwitchRow(R.string.tasks_toggle, tasksEnabled, hintRes = R.string.tasks_toggle_hint, onCheckedChange = onSetTasks)
        tasksStatus?.let { status ->
            Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(
                        if (status == TasksStatus.NOT_INSTALLED) R.string.tasks_not_installed
                        else R.string.tasks_no_access,
                    ),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (status == TasksStatus.NO_ACCESS) ErrorActionButton(ErrorAction.APP_SETTINGS)
            }
        }
        if (tasksEnabled && taskLists != null) {
            RadioRow(
                name = TASKS_ROOT.name,
                detail = stringResource(R.string.tasks_root_hint),
                color = null,
                selected = selectedKey == TASKS_KEY,
                onSelect = { onSelect(TASKS_KEY) },
            )
            aliasField(TASKS_KEY)
            val lists = taskLists
            val manyAccounts = lists.map { it.account }.distinct().size > 1
            for (tl in lists) {
                val key = tasksKey(tl.id)
                RadioRow(
                    name = tl.title,
                    detail = tl.account.takeIf { manyAccounts && it.isNotEmpty() },
                    color = tl.color.takeIf { it != 0 },
                    selected = selectedKey == key,
                    onSelect = { onSelect(key) },
                )
                aliasField(key)
            }
        }
        SwitchRow(
            R.string.tasks_no_due_date, tasksNoDueDate,
            hintRes = R.string.tasks_no_due_date_hint, enabled = tasksEnabled, onCheckedChange = onSetNoDueDate,
        )
    }
}

@Composable
private fun AliasField(
    alias: String,
    derived: String,
    taken: Set<String>,
    onCommit: (String?) -> Unit,
) {
    var draft by remember(alias) { mutableStateOf(alias) }
    val normalized = normalizeAlias(draft)
    val duplicate = normalized in taken
    fun commit() {
        if (duplicate) return
        onCommit(if (normalized.isEmpty() || normalized == derived) null else normalized)
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 30.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("#", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val interaction = remember { MutableInteractionSource() }
        val colors = OutlinedTextFieldDefaults.colors()
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 28.dp)
                .onFocusChanged { if (!it.isFocused && draft != alias) commit() },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            interactionSource = interaction,
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = draft,
                    innerTextField = inner,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = interaction,
                    isError = duplicate,
                    supportingText = if (duplicate) ({ Text(stringResource(R.string.target_alias_duplicate)) }) else null,
                    colors = colors,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = true, isError = duplicate, interactionSource = interaction, colors = colors,
                        )
                    },
                )
            },
        )
    }
}
