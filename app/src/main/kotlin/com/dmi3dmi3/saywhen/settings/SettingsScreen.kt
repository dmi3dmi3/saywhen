package com.dmi3dmi3.saywhen.settings

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.dmi3dmi3.saywhen.BuildConfig
import com.dmi3dmi3.saywhen.R
import com.dmi3dmi3.saywhen.calendar.CalendarInfo
import com.dmi3dmi3.saywhen.calendar.CalendarRepository
import com.dmi3dmi3.saywhen.quickadd.ErrorAction
import com.dmi3dmi3.saywhen.quickadd.ErrorActionButton
import com.dmi3dmi3.saywhen.widget.SayWhenWidgetProvider
import kotlinx.coroutines.launch

private val DURATION_OPTIONS = listOf(30, 60, 90, 120)

// публичное зеркало (задача 22) — дом релизов и канал фидбека
private const val REPO_URL = "https://github.com/dmi3dmi3/saywhen"

/**
 * Главный экран по ярлыку (задачи 18, 19a): hero с wordmark и примерами +
 * настройки группами-карточками — всё в один экран. Календарь свёрнут в
 * строку-значение, полный список — в шторке (у людей бывает и десять
 * календарей). Выбранное состояние везде на контрастном primary (design.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenQuickAdd: (prefill: String?) -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    // initial = null: пока DataStore не отдал сохранённое, секции не рисуем —
    // иначе контролы мигают дефолтом и «перескакивают» на выбранное
    val settingsOrNull by repo.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var calendars by remember { mutableStateOf<List<CalendarInfo>?>(null) }
    var showCalendarSheet by remember { mutableStateOf(false) }

    fun loadCalendars() {
        calendars = CalendarRepository(context.contentResolver).writableCalendars()
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) loadCalendars() }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) loadCalendars() else permission.launch(Manifest.permission.READ_CALENDAR)
    }

    val settings = settingsOrNull ?: return

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // hero: имя → слоган → описание → примеры-доказательства → действия;
        // каждая строка тише предыдущей
        Icon(
            painterResource(R.drawable.wordmark),
            contentDescription = stringResource(R.string.app_name),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Column {
            Text(stringResource(R.string.hero_slogan), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.hero_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        for (example in listOf(R.string.example_phrase_1, R.string.example_phrase_2)) {
            val text = stringResource(example)
            Surface(
                onClick = { onOpenQuickAdd(text) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("«$text»", Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val widgetManager = remember { context.getSystemService(AppWidgetManager::class.java) }
            if (widgetManager?.isRequestPinAppWidgetSupported == true) {
                Button(onClick = {
                    widgetManager.requestPinAppWidget(
                        ComponentName(context, SayWhenWidgetProvider::class.java), null, null,
                    )
                }) { Text(stringResource(R.string.action_add_widget)) }
            }
            OutlinedButton(onClick = { onOpenQuickAdd(null) }) {
                Text(stringResource(R.string.action_try))
            }
        }

        val selectedCalendar = calendars?.find { it.id == settings.calendarId }
        Section(R.string.settings_calendar) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable { showCalendarSheet = true }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CalendarDot(selectedCalendar)
                Text(
                    calendarLabel(selectedCalendar),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Section(R.string.settings_duration) {
            val hourUnit = stringResource(R.string.unit_hours)
            val minuteUnit = stringResource(R.string.unit_minutes)
            SegmentedRow(
                options = DURATION_OPTIONS.map { it to durationLabel(it, hourUnit, minuteUnit) },
                selected = settings.defaultDurationMinutes,
                onSelect = { scope.launch { repo.setDefaultDuration(it) } },
            )
        }

        Section(R.string.settings_theme) {
            ThemeTiles(selected = settings.theme) { scope.launch { repo.setTheme(it) } }
        }

        Section(R.string.settings_behavior) {
            SwitchRow(R.string.setting_nod, settings.nodEnabled) {
                scope.launch { repo.setNodEnabled(it) }
            }
            SwitchRow(R.string.setting_haptic, settings.hapticEnabled) {
                scope.launch { repo.setHapticEnabled(it) }
            }
        }

        // язык UI: per-app locale, выбор переживает перезапуск (autoStoreLocales);
        // парсер всегда двуязычный — переключается только интерфейс
        Section(R.string.settings_language) {
            val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            SegmentedRow(
                options = listOf(
                    "" to stringResource(R.string.language_system),
                    "ru" to stringResource(R.string.language_russian),
                    "en" to stringResource(R.string.language_english),
                ),
                selected = current,
                onSelect = {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it))
                },
            )
        }

        AboutFooter()
    }

    if (showCalendarSheet) {
        ModalBottomSheet(onDismissRequest = { showCalendarSheet = false }) {
            CalendarSheetContent(
                calendars = calendars,
                selectedId = settings.calendarId,
                onSelect = { id ->
                    scope.launch { repo.setCalendarId(id) }
                    showCalendarSheet = false
                },
                onRequestPermission = { permission.launch(Manifest.permission.READ_CALENDAR) },
            )
        }
    }
}

/**
 * Подвал About (задача 21): версия (тап копирует — для баг-репортов), ссылки
 * на зеркало и privacy-факт. Тихо, вне карточек: справочное не спорит с
 * настройками за внимание. Единственный канал связи — Issues (решение
 * владельца); браузер открывает система, само приложение в сеть не ходит.
 */
@Composable
private fun AboutFooter() {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "SayWhen ${BuildConfig.VERSION_NAME}",
            Modifier.clickable(onClickLabel = stringResource(R.string.about_copy_version)) {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                    ClipData.newPlainText(
                        "SayWhen",
                        "SayWhen ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    ),
                )
                // подтверждение копирования на 13+ показывает система
            },
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AboutLink(R.string.about_source) { open(REPO_URL) }
            Text(" · ", style = MaterialTheme.typography.bodySmall, color = muted)
            AboutLink(R.string.about_issues) { open("$REPO_URL/issues") }
            Text(" · ", style = MaterialTheme.typography.bodySmall, color = muted)
            AboutLink(R.string.about_license) { open("$REPO_URL/blob/main/LICENSE") }
        }
        // строка-факт и одновременно дверь к полным privacy notes в зеркале;
        // остаётся приглушённой — подвал не растёт на четвёртую ссылку
        Text(
            stringResource(R.string.about_privacy),
            Modifier.clickable(
                onClickLabel = stringResource(R.string.about_privacy_details),
                role = Role.Button,
            ) { open("$REPO_URL/blob/main/PRIVACY.md") },
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
    }
}

@Composable
private fun AboutLink(labelRes: Int, onClick: () -> Unit) {
    Text(
        stringResource(labelRes),
        Modifier.clickable(role = Role.Button, onClick = onClick),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Строка-переключатель: текст и тумблер — одна цель фокуса (toggleable). */
@Composable
private fun SwitchRow(labelRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // ≥48dp: пассивный Switch (onCheckedChange = null) не резервирует
            // touch-target сам — держит строка, она и есть цель
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(labelRes),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Секция настроек: тихая подпись снаружи + группа-карточка (design.md). */
@Composable
private fun Section(labelRes: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            stringResource(labelRes),
            Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(10.dp), content = content)
        }
    }
}

/** Взаимоисключающая тройка-четвёрка; выбранное — контрастный primary. */
@Composable
private fun <T> SegmentedRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                icon = {},  // галочка съедает ширину, выбор и так виден заливкой
                label = { Text(label, maxLines = 1) },
            )
        }
    }
}

/**
 * Плитки тем: каждая показывает мини-палитру своей темы (не текущей!) —
 * честное превью; «системная» — обе половины суток. Сетка готова к росту
 * числа тем (перенос строк за счёт weight).
 */
@Composable
private fun ThemeTiles(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
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
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun BoxScope.AccentDot(color: Color) {
    Box(
        Modifier.align(Alignment.BottomStart).padding(5.dp).size(10.dp)
            .background(color, CircleShape),
    )
}

@Composable
private fun CalendarDot(cal: CalendarInfo?) {
    val color = if (cal != null && cal.color != 0) Color(cal.color or 0xFF000000.toInt())
                else MaterialTheme.colorScheme.primary
    Box(Modifier.size(10.dp).background(color, CircleShape))
}

@Composable
private fun calendarLabel(cal: CalendarInfo?): String =
    if (cal == null) stringResource(R.string.settings_calendar_auto)
    else if (cal.name == cal.accountName) cal.name
    else "${cal.name} — ${cal.accountName}"

/** Список в шторке: радио + цвет календаря + «имя / аккаунт» двумя этажами. */
@Composable
private fun CalendarSheetContent(
    calendars: List<CalendarInfo>?,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onRequestPermission: () -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        Text(
            stringResource(R.string.settings_calendar),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        CalendarPickRow(
            name = stringResource(R.string.settings_calendar_auto),
            account = null,
            color = null,
            selected = selectedId == null,
            onSelect = { onSelect(null) },
        )
        when (val list = calendars) {
            null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_calendar_permission),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                )
                // повторный запрос; при «навсегда» рядом — путь через настройки приложения
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
                    CalendarPickRow(
                        name = cal.name,
                        account = cal.accountName.takeIf { it != cal.name },
                        color = cal.color.takeIf { it != 0 },
                        selected = selectedId == cal.id,
                        onSelect = { onSelect(cal.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarPickRow(
    name: String,
    account: String?,
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
        // пассивный: цель фокуса — вся строка, иначе она двоится
        RadioButton(selected = selected, onClick = null)
        if (color != null) {
            Box(
                Modifier.padding(end = 8.dp).size(10.dp)
                    .background(Color(color or 0xFF000000.toInt()), CircleShape),
            )
        }
        Column(Modifier.padding(vertical = 6.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (account != null) {
                Text(
                    account,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
