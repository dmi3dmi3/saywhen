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
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.dmi3dmi3.saywhen.BuildConfig
import com.dmi3dmi3.saywhen.R
import com.dmi3dmi3.saywhen.calendar.CalendarInfo
import com.dmi3dmi3.saywhen.calendar.CalendarRepository
import com.dmi3dmi3.saywhen.calendar.pickDefaultCalendar
import com.dmi3dmi3.saywhen.target.targetsOf
import com.dmi3dmi3.saywhen.tasks.TaskListInfo
import com.dmi3dmi3.saywhen.tasks.TasksRepository
import com.dmi3dmi3.saywhen.widget.SayWhenWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REPO_URL = "https://github.com/dmi3dmi3/saywhen"

internal enum class SettingsPage { DOORS, TARGET, LANGUAGES, UNSAID, TIME, LOOKS }

@Composable
fun SettingsScreen(onOpenQuickAdd: (prefill: String?) -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val settingsOrNull by repo.settings.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var calendars by remember { mutableStateOf<List<CalendarInfo>?>(null) }
    fun loadCalendars() {
        calendars = CalendarRepository(context.contentResolver).writableCalendars()
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) loadCalendars() }

    val tasksRepo = remember { TasksRepository(context) }
    var taskLists by remember { mutableStateOf<List<TaskListInfo>?>(null) }
    var tasksStatus by remember { mutableStateOf<TasksStatus?>(null) }
    val tasksPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            tasksStatus = null
            scope.launch { repo.setTasksEnabled(true) }
        } else {
            tasksStatus = TasksStatus.NO_ACCESS
        }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) loadCalendars() else permission.launch(Manifest.permission.READ_CALENDAR)
    }

    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler(enabled = page != null) { page = null }

    val settings = settingsOrNull ?: return

    LaunchedEffect(settings.tasksEnabled) {
        val usable = settings.tasksEnabled && tasksRepo.hasPermissions()
        if (settings.tasksEnabled) tasksStatus = if (usable) null else TasksStatus.NO_ACCESS
        taskLists = if (usable) {
            withContext(Dispatchers.IO) { runCatching { tasksRepo.writableLists() }.getOrNull() }
        } else {
            null
        }
    }

    fun setTasks(on: Boolean) {
        when {
            !on -> {
                tasksStatus = null
                scope.launch { repo.setTasksEnabled(false) }
            }
            !tasksRepo.isInstalled() -> tasksStatus = TasksStatus.NOT_INSTALLED
            tasksRepo.hasPermissions() -> {
                tasksStatus = null
                scope.launch { repo.setTasksEnabled(true) }
            }
            else -> tasksPermissions.launch(TasksRepository.PERMISSIONS)
        }
    }

    val launchPaths = remember { LaunchPaths(context) }
    val widgetManager = remember { context.getSystemService(AppWidgetManager::class.java) }
    var resumes by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose { }
    }
    var iconEnabled by remember(page, resumes) { mutableStateOf(launchPaths.isEnabled(LaunchPaths.LAUNCHER)) }
    var insertEnabled by remember(page, resumes) { mutableStateOf(launchPaths.isEnabled(LaunchPaths.INSERT)) }
    val widgetPinned = remember(page, resumes) {
        widgetManager?.getAppWidgetIds(ComponentName(context, SayWhenWidgetProvider::class.java))?.isNotEmpty() == true
    }

    val targets = targetsOf(calendars, taskLists, settings.tasksEnabled)
    val selectedTarget = targets.find { it.key == settings.defaultTarget }
    val exampleAlias = remember(targets, calendars, settings.defaultTarget, settings.targetAliases) {
        val key = selectedTarget?.key
            ?: calendars?.let { pickDefaultCalendar(it) }?.let { calendarKey(it.id) }
        key?.let { effectiveAliases(targets, settings.targetAliases)[it] }
    }

    val uiLanguage = stringResource(R.string.date_locale)
    LaunchedEffect(settings.parserLanguages) {
        if (settings.parserLanguages != null) repo.migrateParserLanguages(uiLanguage)
    }
    val (parserOn, parserOff) = settings.parserLanguages?.let { migrateParserLanguages(it, uiLanguage) }
        ?: (settings.parserOn to settings.parserOff)
    val signals = remember { readLanguageSignals(context) }
    val enabledLanguages = parserOrder(uiLanguage, parserOn, parserOff, signals.system, signals.keyboard)
    val autoLanguages = autoLanguages(uiLanguage, signals.system, signals.keyboard)

    when (page) {
        null -> Overview(
            settings = settings,
            targetName = selectedTarget?.name,
            exampleAlias = exampleAlias,
            enabledLanguages = enabledLanguages,
            doors = listOfNotNull(
                stringResource(R.string.doors_widget_short).takeIf { widgetPinned },
                stringResource(R.string.doors_icon_short).takeIf { iconEnabled },
                stringResource(R.string.doors_insert_short).takeIf { insertEnabled },
            ),
            onOpenQuickAdd = onOpenQuickAdd,
            onOpen = { page = it },
        )
        SettingsPage.DOORS -> PageScaffold(R.string.settings_doors, onBack = { page = null }) {
            DoorsPage(
                canPinWidget = widgetManager?.isRequestPinAppWidgetSupported == true,
                iconEnabled = iconEnabled,
                insertEnabled = insertEnabled,
                onPinWidget = {
                    widgetManager?.requestPinAppWidget(
                        ComponentName(context, SayWhenWidgetProvider::class.java), null, null,
                    )
                },
                onSetIcon = {
                    launchPaths.setEnabled(LaunchPaths.LAUNCHER, it)
                    iconEnabled = it
                },
                onSetInsert = {
                    launchPaths.setEnabled(LaunchPaths.INSERT, it)
                    insertEnabled = it
                },
            )
        }
        SettingsPage.TARGET -> PageScaffold(R.string.settings_target, onBack = { page = null }) {
            TargetPage(
                calendars = calendars,
                taskLists = taskLists,
                tasksEnabled = settings.tasksEnabled,
                tasksStatus = tasksStatus,
                tasksNoDueDate = settings.tasksNoDueDate,
                selectedKey = settings.defaultTarget,
                manualAliases = settings.targetAliases,
                onSelect = { key -> scope.launch { repo.setDefaultTarget(key) } },
                onAlias = { key, alias -> scope.launch { repo.setTargetAlias(key, alias) } },
                onRequestPermission = { permission.launch(Manifest.permission.READ_CALENDAR) },
                onSetTasks = ::setTasks,
                onSetNoDueDate = { scope.launch { repo.setTasksNoDueDate(it) } },
            )
        }
        SettingsPage.LANGUAGES -> PageScaffold(R.string.settings_languages, onBack = { page = null }) {
            LanguagesPage(
                enabled = enabledLanguages,
                uiLanguage = uiLanguage,
                parserOn = parserOn,
                parserOff = parserOff,
                signals = signals,
                onToggle = { code, on -> scope.launch { repo.setParserLanguage(code, on, autoLanguages) } },
            )
        }
        SettingsPage.UNSAID -> PageScaffold(R.string.settings_unsaid, onBack = { page = null }) {
            UnsaidPage(
                durationMinutes = settings.defaultDurationMinutes,
                reminderMinutes = settings.defaultReminderMinutes,
                onSetDuration = { scope.launch { repo.setDefaultDuration(it) } },
                onSetReminder = { scope.launch { repo.setDefaultReminder(it) } },
            )
        }
        SettingsPage.TIME -> PageScaffold(R.string.settings_time, onBack = { page = null }) {
            TimePage(
                windowStart = settings.activityStartHour,
                windowEnd = settings.activityEndHour,
                dotTime = settings.dotTime,
                onSetWindow = { s, e -> scope.launch { repo.setActivityWindow(s, e) } },
                onSetDotTime = { scope.launch { repo.setDotTime(it) } },
            )
        }
        SettingsPage.LOOKS -> PageScaffold(R.string.settings_looks, onBack = { page = null }) {
            LooksPage(
                theme = settings.theme,
                nodEnabled = settings.nodEnabled,
                hapticEnabled = settings.hapticEnabled,
                onSetTheme = { scope.launch { repo.setTheme(it) } },
                onSetNod = { scope.launch { repo.setNodEnabled(it) } },
                onSetHaptic = { scope.launch { repo.setHapticEnabled(it) } },
            )
        }
    }
}

@Composable
private fun Overview(
    settings: Settings,
    targetName: String?,
    exampleAlias: String?,
    enabledLanguages: List<String>,
    doors: List<String>,
    onOpenQuickAdd: (String?) -> Unit,
    onOpen: (SettingsPage) -> Unit,
) {
    val hourUnit = stringResource(R.string.unit_hours)
    val minuteUnit = stringResource(R.string.unit_minutes)
    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painterResource(R.drawable.wordmark),
            contentDescription = stringResource(R.string.app_name),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(stringResource(R.string.hero_slogan), style = MaterialTheme.typography.bodyLarge)

        val examples = listOfNotNull(
            stringResource(R.string.example_phrase_1).let { it to it },
            stringResource(R.string.example_phrase_2).let { it to it },
            exampleAlias?.let {
                stringResource(R.string.example_phrase_3, shortAlias(it)) to stringResource(R.string.example_phrase_3, it)
            },
        )
        for ((shown, prefill) in examples) {
            Surface(
                onClick = { onOpenQuickAdd(prefill) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("\"$shown\"", Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
            }
        }
        OutlinedButton(onClick = { onOpenQuickAdd(null) }) {
            Text(stringResource(R.string.action_try))
        }

        val reminder = when (val m = settings.defaultReminderMinutes) {
            null -> stringResource(R.string.summary_reminder_off)
            0 -> stringResource(R.string.summary_reminder_at_event)
            else -> stringResource(R.string.summary_reminder_before, durationLabel(m, hourUnit, minuteUnit))
        }
        val uiLanguage = stringResource(R.string.date_locale)
        GroupCard {
            ValueRow(
                R.string.settings_looks,
                summaryOf(
                    stringResource(themeLabel(settings.theme)),
                    stringResource(languageLabels.getValue(uiLanguage)),
                ),
            ) { onOpen(SettingsPage.LOOKS) }
            RowDivider()
            ValueRow(R.string.settings_doors, listSummary(doors, stringResource(R.string.doors_none))) { onOpen(SettingsPage.DOORS) }
            RowDivider()
            ValueRow(R.string.settings_target, targetName ?: stringResource(R.string.settings_calendar_auto)) { onOpen(SettingsPage.TARGET) }
            RowDivider()
            ValueRow(
                R.string.settings_languages,
                languagesSummary(enabledLanguages.map { stringResource(languageLabels.getValue(it)) }),
            ) { onOpen(SettingsPage.LANGUAGES) }
            RowDivider()
            ValueRow(
                R.string.settings_unsaid,
                summaryOf(durationLabel(settings.defaultDurationMinutes, hourUnit, minuteUnit), reminder),
            ) { onOpen(SettingsPage.UNSAID) }
            RowDivider()
            ValueRow(
                R.string.settings_time,
                timeSummary(settings.activityStartHour, settings.activityEndHour, settings.dotTime),
            ) { onOpen(SettingsPage.TIME) }
        }

        AboutFooter()
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
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
                if (Build.VERSION.SDK_INT < 33) {
                    Toast.makeText(context, R.string.version_copied, Toast.LENGTH_SHORT).show()
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = muted,
        )
        FlowRow(horizontalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AboutLink(R.string.about_source) { open(REPO_URL) }
                Text(" · ", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AboutLink(R.string.about_issues) { open("$REPO_URL/issues") }
                Text(" · ", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            AboutLink(R.string.about_license) { open("$REPO_URL/blob/main/LICENSE") }
        }
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
