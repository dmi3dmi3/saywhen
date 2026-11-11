@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dmi3dmi3.saywhen.settings

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dmi3dmi3.saywhen.R

internal val languageLabels = mapOf(
    "ru" to R.string.language_russian, "en" to R.string.language_english,
    "it" to R.string.language_italian, "es" to R.string.language_spanish,
    "de" to R.string.language_german, "fr" to R.string.language_french,
)

internal fun currentUiLanguage(): String =
    AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(",").substringBefore("-")

@Composable
internal fun PageScaffold(titleRes: Int, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.a11y_back))
            }
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
internal fun ValueRow(labelRes: Int, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text("›", Modifier.clearAndSetSemantics {}, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 6.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@Composable
internal fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(10.dp), content = content)
    }
}

@Composable
internal fun Section(labelRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            stringResource(labelRes),
            Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GroupCard(content)
    }
}

@Composable
internal fun RadioRow(
    name: String,
    detail: String?,
    color: Int?,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 8.dp))
        if (color != null) {
            Box(
                Modifier.padding(end = 8.dp).size(10.dp)
                    .background(Color(color or 0xFF000000.toInt()), CircleShape),
            )
        }
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun Hint(textRes: Int, modifier: Modifier = Modifier.padding(start = 6.dp, top = 5.dp)) {
    Text(
        stringResource(textRes),
        modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SwitchRow(
    label: String,
    checked: Boolean,
    hint: String? = null,
    enabled: Boolean = true,
    locked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled && !locked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 6.dp, vertical = if (hint == null) 0.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked, onCheckedChange = null, enabled = enabled && !locked,
            colors = if (locked) SwitchDefaults.colors(
                disabledCheckedThumbColor = MaterialTheme.colorScheme.onPrimary,
                disabledCheckedTrackColor = MaterialTheme.colorScheme.primary,
                disabledCheckedBorderColor = Color.Transparent,
                disabledCheckedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) else SwitchDefaults.colors(),
        )
    }
}

@Composable
internal fun SwitchRow(
    labelRes: Int,
    checked: Boolean,
    hintRes: Int? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) = SwitchRow(stringResource(labelRes), checked, hintRes?.let { stringResource(it) }, enabled, onCheckedChange = onCheckedChange)

@Composable
internal fun <T> OptionChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
internal fun ThemeTiles(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val context = LocalContext.current
    val light = remember {
        if (Build.VERSION.SDK_INT >= 31) dynamicLightColorScheme(context) else lightColorScheme()
    }
    val dark = remember {
        if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else darkColorScheme()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeTile(R.string.theme_system, selected == ThemeMode.SYSTEM, Modifier.weight(1f),
            onClick = { onSelect(ThemeMode.SYSTEM) }) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().background(light.surface))
                Box(Modifier.weight(1f).fillMaxHeight().background(dark.surface))
            }
        }
        ThemeTile(R.string.theme_light, selected == ThemeMode.LIGHT, Modifier.weight(1f),
            onClick = { onSelect(ThemeMode.LIGHT) }) {
            Box(Modifier.fillMaxSize().background(light.surface))
            AccentDot(light.primary)
        }
        ThemeTile(R.string.theme_dark, selected == ThemeMode.DARK, Modifier.weight(1f),
            onClick = { onSelect(ThemeMode.DARK) }) {
            Box(Modifier.fillMaxSize().background(dark.surface))
            AccentDot(dark.primary)
        }
    }
}

@Composable
private fun ThemeTile(
    labelRes: Int,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    swatch: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val border =
        if (selected) 2.dp to MaterialTheme.colorScheme.primary
        else 1.dp to MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier
            .border(border.first, border.second, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(30.dp)
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        ) {
            Box(Modifier.fillMaxSize().padding(0.5.dp), content = { swatch() })
        }
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BoxScope.AccentDot(color: Color) {
    Box(
        Modifier.align(Alignment.BottomStart).padding(5.dp).size(10.dp)
            .background(color, CircleShape),
    )
}

internal fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}
