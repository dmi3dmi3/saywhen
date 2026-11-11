package com.dmi3dmi3.saywhen.quickadd

import android.Manifest
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.Settings as SystemSettings
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.core.content.ContextCompat
import com.dmi3dmi3.saywhen.MainActivity
import com.dmi3dmi3.saywhen.R
import com.dmi3dmi3.saywhen.calendar.CalendarRepository
import com.dmi3dmi3.saywhen.calendar.CalendarWriter
import com.dmi3dmi3.saywhen.calendar.requestCalendarSync
import com.dmi3dmi3.saywhen.parser.MultilingualEventParser
import com.dmi3dmi3.saywhen.parser.ParsedEvent
import com.dmi3dmi3.saywhen.parser.TokenMatch
import com.dmi3dmi3.saywhen.settings.Settings
import com.dmi3dmi3.saywhen.settings.SettingsRepository
import com.dmi3dmi3.saywhen.settings.migrateParserLanguages
import com.dmi3dmi3.saywhen.settings.parserOrder
import com.dmi3dmi3.saywhen.settings.readLanguageSignals
import com.dmi3dmi3.saywhen.calendar.pickDefaultCalendar
import com.dmi3dmi3.saywhen.settings.TargetSuggestions
import com.dmi3dmi3.saywhen.settings.calendarKey
import com.dmi3dmi3.saywhen.settings.effectiveAliases
import com.dmi3dmi3.saywhen.settings.targetSuggestions
import com.dmi3dmi3.saywhen.target.TargetRef
import com.dmi3dmi3.saywhen.target.parseTargetKey
import com.dmi3dmi3.saywhen.target.targetsOf
import com.dmi3dmi3.saywhen.target.writesTask
import com.dmi3dmi3.saywhen.tasks.TaskListInfo
import com.dmi3dmi3.saywhen.tasks.TasksRepository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EDGE_INSET = 10.dp

@Composable
internal fun ErrorActionButton(action: ErrorAction) {
    val context = LocalContext.current
    TextButton(onClick = {
        val intent = when (action) {
            ErrorAction.APP_SETTINGS -> Intent(
                SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            )
            ErrorAction.ADD_ACCOUNT -> Intent(SystemSettings.ACTION_ADD_ACCOUNT)
        }
        context.startActivity(intent)
    }) {
        Text(
            stringResource(
                when (action) {
                    ErrorAction.APP_SETTINGS -> R.string.action_app_settings
                    ErrorAction.ADD_ACCOUNT -> R.string.action_add_account
                },
            ),
        )
    }
}

internal enum class ErrorAction { APP_SETTINGS, ADD_ACCOUNT }

private class NoCalendar : RuntimeException()
private class TaskListGone : RuntimeException()

internal data class Created(
    val id: Long,
    val title: String,
    val sourceText: String,
    val blocked: List<IntRange>,
    val target: TargetRef,
)

@Composable
fun QuickAddScreen(onClose: () -> Unit, prefill: String? = null) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val settings by settingsRepo.settings.collectAsState(initial = Settings())
    val uiLanguage = stringResource(R.string.date_locale)
    LaunchedEffect(settings.parserLanguages) {
        if (settings.parserLanguages != null) settingsRepo.migrateParserLanguages(uiLanguage)
    }
    val (parserOn, parserOff) = settings.parserLanguages?.let { migrateParserLanguages(it, uiLanguage) }
        ?: (settings.parserOn to settings.parserOff)
    val signals = remember { readLanguageSignals(context) }
    val calendars = remember {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) CalendarRepository(context.contentResolver).writableCalendars() else emptyList()
    }
    val tasksRepo = remember { TasksRepository(context) }
    var taskLists by remember { mutableStateOf<List<TaskListInfo>?>(null) }
    LaunchedEffect(settings.tasksEnabled) {
        taskLists = if (settings.tasksEnabled && tasksRepo.hasPermissions()) {
            withContext(Dispatchers.IO) { runCatching { tasksRepo.writableLists() }.getOrNull() }
        } else {
            null
        }
    }
    val targets = remember(calendars, taskLists, settings.tasksEnabled) {
        targetsOf(calendars, taskLists, settings.tasksEnabled)
    }
    val aliases = remember(targets, settings.targetAliases) { effectiveAliases(targets, settings.targetAliases) }
    val parser = remember(
        parserOn, parserOff, uiLanguage, signals, aliases,
        settings.activityStartHour, settings.activityEndHour, settings.dotTime,
    ) {
        MultilingualEventParser(
            parserOrder(uiLanguage, parserOn, parserOff, signals.system, signals.keyboard),
            settings.activityStartHour until settings.activityEndHour,
            targets = aliases.values.toSet(),
            dotTime = settings.dotTime,
        )
    }
    val defaultDuration = Duration.ofMinutes(settings.defaultDurationMinutes.toLong())
    var value by remember {
        mutableStateOf(TextFieldValue(prefill.orEmpty(), TextRange(prefill.orEmpty().length)))
    }
    var blocked by remember { mutableStateOf(emptyList<IntRange>()) }
    var error by remember { mutableStateOf<Pair<String, ErrorAction?>?>(null) }
    var created by remember { mutableStateOf<Created?>(null) }
    var autoClose by remember { mutableStateOf(false) }
    val parsed = remember(value.text, blocked, parser) { parser.parse(value.text, ZonedDateTime.now(), blocked) }
    fun targetKey(event: ParsedEvent): String? =
        chosenTargetKey(event.target, aliases, settings.defaultTarget)
            ?: pickDefaultCalendar(calendars)?.let { calendarKey(it.id) }
    val summaryTarget = remember(parsed.target, targets, aliases, settings.defaultTarget) {
        chosenTargetKey(parsed.target, aliases, settings.defaultTarget)
            ?.let { key -> targets.firstOrNull { it.key == key } }
            ?.let { info -> SummaryTarget(aliases.getValue(info.key), info.color, isTaskList = parseTargetKey(info.key).writesTask) }
    }
    val suggestions = remember(value, aliases) { targetSuggestions(value.text, value.selection.end, aliases.values) }
    val aliasColors = remember(targets, aliases) { targets.associate { aliases.getValue(it.key) to it.color } }

    val scope = rememberCoroutineScope()

    fun write(event: ParsedEvent, ref: TargetRef?, reminder: Int?): Pair<TargetRef, Long>? = when (ref) {
        is TargetRef.TaskList -> {
            if (taskLists.orEmpty().none { it.id == ref.id }) throw TaskListGone()
            tasksRepo.insert(event, ref.id, settings.tasksNoDueDate, reminder)?.let { ref to it }
        }
        TargetRef.Tasks -> {
            if (!tasksRepo.isInstalled()) throw TaskListGone()
            tasksRepo.insert(event, null, settings.tasksNoDueDate, reminder)?.let { ref to it }
        }
        else -> {
            val repo = CalendarRepository(context.contentResolver)
            val calendar = (ref as? TargetRef.Calendar)
                ?.let { c -> repo.writableCalendars().firstOrNull { it.id == c.id } }
                ?: repo.defaultCalendar()
                ?: throw NoCalendar()
            CalendarWriter(context.contentResolver)
                .insert(event, calendar.id, defaultDuration, reminderMinutes = reminder)
                ?.also { requestCalendarSync(calendar) }
                ?.let { TargetRef.Calendar(calendar.id) to it }
        }
    }

    fun create() {
        val event = parser.parse(value.text, ZonedDateTime.now(), blocked)
        val reminder = effectiveReminder(event, settings.defaultReminderMinutes)
        val ref = targetKey(event)?.let(::parseTargetKey)
        scope.launch {
            val outcome = runCatching { withContext(Dispatchers.IO) { write(event, ref, reminder) } }
            val done = outcome.getOrNull()
            val failure = outcome.exceptionOrNull()
            when {
                done != null -> {
                    created = Created(done.second, event.title, value.text, blocked, done.first)
                    value = TextFieldValue("")
                    blocked = emptyList()
                    error = null
                    autoClose = true
                }
                failure is NoCalendar ->
                    error = context.getString(R.string.error_no_calendar) to ErrorAction.ADD_ACCOUNT
                failure is TaskListGone ->
                    error = context.getString(R.string.error_no_tasks_app) to null
                failure is SecurityException ->
                    error = context.getString(R.string.error_tasks_access) to ErrorAction.APP_SETTINGS
                ref.writesTask ->
                    error = context.getString(R.string.error_tasks_insert) to null
                else ->
                    error = context.getString(R.string.error_insert_failed) to null
            }
        }
    }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) create()
        else error = context.getString(R.string.error_no_permission) to ErrorAction.APP_SETTINGS
    }

    fun createWithPermissions() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) create()
        else permissions.launch(
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.action_close),
                onClick = onClose,
            )
            .imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val autoCloseMillis = remember {
            val am = context.getSystemService(AccessibilityManager::class.java)
            if (Build.VERSION.SDK_INT >= 29 && am != null) {
                am.getRecommendedTimeoutMillis(
                    5000, AccessibilityManager.FLAG_CONTENT_CONTROLS,
                ).toLong()
            } else 5000L
        }
        LaunchedEffect(created, autoClose) {
            if (created != null && autoClose) {
                delay(autoCloseMillis)
                onClose()
            }
        }
        val view = LocalView.current
        LaunchedEffect(created) {
            if (created != null && settings.hapticEnabled) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.KEYBOARD_TAP,
                )
            }
        }
        InputConstruction(
            value = value,
            onValueChange = { raw ->
                val new = if ('\n' in raw.text) {
                    val cleaned = raw.text.replace("\n", "")
                    if (cleaned == value.text) {
                        if (value.text.isNotBlank()) createWithPermissions()
                        null
                    } else {
                        raw.copy(text = cleaned, selection = TextRange(cleaned.length))
                    }
                } else {
                    raw
                }
                if (new != null) {
                    val (next, nextBlocked) = applyEdit(value, new, blocked, parsed.matches)
                    value = next
                    blocked = nextBlocked
                    error = null
                    autoClose = false
                }
            },
            parsed = parsed,
            defaultDuration = defaultDuration,
            defaultReminderMinutes = settings.defaultReminderMinutes,
            nodEnabled = settings.nodEnabled,
            error = error,
            created = created,
            target = summaryTarget,
            tasksNoDueDate = settings.tasksNoDueDate,
            suggestions = suggestions,
            aliasColors = aliasColors,
            onSuggestion = { alias ->
                suggestions?.let { s ->
                    val before = value.text.substring(0, s.range.first)
                    val after = value.text.substring(s.range.last + 1)
                    val inserted = "#$alias "
                    value = TextFieldValue(before + inserted + after, TextRange(before.length + inserted.length))
                }
            },
            onCreate = ::createWithPermissions,
            onOpenCreated = {
                created?.let { done ->
                    val isTask = done.target.writesTask
                    try {
                        context.startActivity(
                            if (isTask) {
                                tasksRepo.viewIntent(done.id)
                            } else {
                                Intent(Intent.ACTION_VIEW).setDataAndType(
                                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, done.id),
                                    "vnd.android.cursor.item/event",
                                )
                            },
                        )
                        onClose()
                    } catch (_: ActivityNotFoundException) {
                        error = context.getString(
                            if (isTask) R.string.error_no_tasks_app else R.string.error_no_calendar_app,
                        ) to null
                    }
                }
            },
            onUndo = {
                created?.let { done ->
                    if (done.target.writesTask) {
                        scope.launch { withContext(Dispatchers.IO) { runCatching { tasksRepo.delete(done.id) } } }
                    } else {
                        CalendarWriter(context.contentResolver).delete(done.id)
                    }
                    value = TextFieldValue(done.sourceText, TextRange(done.sourceText.length))
                    blocked = done.blocked
                    created = null
                    autoClose = false
                }
            },
        )
    }
}

@Composable
private fun InputConstruction(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    parsed: ParsedEvent,
    defaultDuration: Duration,
    defaultReminderMinutes: Int?,
    nodEnabled: Boolean,
    error: Pair<String, ErrorAction?>?,
    created: Created?,
    target: SummaryTarget?,
    tasksNoDueDate: Boolean,
    suggestions: TargetSuggestions?,
    aliasColors: Map<String, Int?>,
    onSuggestion: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenCreated: () -> Unit,
    onUndo: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val fieldInteraction = remember { MutableInteractionSource() }
    val focused by fieldInteraction.collectIsFocusedAsState()
    val borderColor =
        if (focused) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant

    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(12.dp)
            .pointerInput(Unit) { detectTapGestures {} },
    ) {
        WordmarkTab(created = created, nodEnabled = nodEnabled, borderColor = borderColor)
        InputField(
            value = value,
            onValueChange = onValueChange,
            matches = parsed.matches,
            focusRequester = focusRequester,
            interaction = fieldInteraction,
            borderColor = borderColor,
            onDone = { if (value.text.isNotBlank()) onCreate() },
        )
        Tray(
            parsed = parsed,
            text = value.text,
            defaultDuration = defaultDuration,
            defaultReminderMinutes = defaultReminderMinutes,
            error = error,
            created = created,
            target = target,
            tasksNoDueDate = tasksNoDueDate,
            suggestions = suggestions,
            aliasColors = aliasColors,
            onSuggestion = onSuggestion,
            createEnabled = value.text.isNotBlank(),
            onCreate = onCreate,
            onOpenCreated = onOpenCreated,
            onUndo = onUndo,
        )
    }
}

@Composable
private fun WordmarkTab(created: Created?, nodEnabled: Boolean, borderColor: Color) {
    val context = LocalContext.current
    val nod = remember { Animatable(0f) }
    LaunchedEffect(created) {
        if (created != null && nodEnabled && ValueAnimator.areAnimatorsEnabled()) {
            nod.animateTo(2f, tween(80))
            nod.animateTo(
                0f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
        }
    }
    Surface(
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .padding(start = EDGE_INSET)
            .zIndex(1f)
            .offset { IntOffset(0, 1.dp.toPx().roundToInt() + nod.value.dp.toPx().roundToInt()) },
    ) {
        Icon(
            painterResource(R.drawable.wordmark),
            contentDescription = stringResource(R.string.app_name),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(
                    onClickLabel = stringResource(R.string.action_open_settings),
                    role = Role.Button,
                ) { context.startActivity(Intent(context, MainActivity::class.java)) }
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .size(58.6.dp, 12.dp),
        )
    }
}

@Composable
private fun InputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    matches: List<TokenMatch>,
    focusRequester: FocusRequester,
    interaction: MutableInteractionSource,
    borderColor: Color,
    onDone: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val pillColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val placeholder = stringResource(R.string.quickadd_placeholder)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyLarge
            .copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        onTextLayout = { layout = it },
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .semantics { if (value.text.isEmpty()) contentDescription = placeholder },
        decorationBox = { inner ->
            Box(
                Modifier
                    .background(MaterialTheme.colorScheme.surfaceBright, shape)
                    .border(1.5.dp, borderColor, shape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                if (value.text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.drawBehind { drawPills(layout, matches, pillColor) }) { inner() }
            }
        },
    )
}

private fun DrawScope.drawPills(
    layout: TextLayoutResult?,
    matches: List<TokenMatch>,
    color: Color,
) {
    layout ?: return
    val text = layout.layoutInput.text
    val sorted = matches.map { it.range }.filter { it.last < text.length }.sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (r in sorted) {
        val prev = merged.lastOrNull()
        if (prev != null && (prev.last + 1 until r.first).all { text[it] == ' ' }) {
            merged[merged.size - 1] = prev.first..maxOf(prev.last, r.last)
        } else {
            merged += r
        }
    }
    val radius = CornerRadius(6.dp.toPx())
    val inset = 2.dp.toPx()
    for (m in merged) {
        val startLine = layout.getLineForOffset(m.first)
        val endLine = layout.getLineForOffset(m.last)
        for (line in startLine..endLine) {
            val left =
                if (line == startLine) layout.getBoundingBox(m.first).left
                else layout.getLineLeft(line)
            val right =
                if (line == endLine) layout.getBoundingBox(m.last).right
                else layout.getLineRight(line)
            val top = layout.getLineTop(line)
            val bottom = layout.getLineBottom(line)
            drawRoundRect(
                color = color,
                topLeft = Offset(left - 2.dp.toPx(), top + inset),
                size = Size(right - left + 4.dp.toPx(), bottom - top - 2 * inset),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun Tray(
    parsed: ParsedEvent,
    text: String,
    defaultDuration: Duration,
    defaultReminderMinutes: Int?,
    error: Pair<String, ErrorAction?>?,
    created: Created?,
    target: SummaryTarget?,
    tasksNoDueDate: Boolean,
    suggestions: TargetSuggestions?,
    aliasColors: Map<String, Int?>,
    onSuggestion: (String) -> Unit,
    createEnabled: Boolean,
    onCreate: () -> Unit,
    onOpenCreated: () -> Unit,
    onUndo: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(horizontal = EDGE_INSET).fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            error?.let { (message, action) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        message,
                        Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    action?.let { ErrorActionButton(it) }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.weight(1f).heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
                    if (created != null && text.isBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.quickadd_created, created.title),
                                Modifier
                                    .weight(1f)
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                                    .clickable(
                                        onClickLabel = stringResource(R.string.action_open_event),
                                        role = Role.Button,
                                        onClick = onOpenCreated,
                                    )
                                    .padding(end = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = onUndo) {
                                Text(stringResource(R.string.quickadd_undo))
                            }
                        }
                    } else if (suggestions != null) {
                        TargetSuggestionsRow(suggestions.options, aliasColors, onSuggestion)
                    } else {
                        SummaryLine(parsed, text, defaultDuration, defaultReminderMinutes, target, tasksNoDueDate)
                    }
                }
                Button(onClick = onCreate, enabled = createEnabled) {
                    Text(stringResource(R.string.quickadd_create))
                }
            }
        }
    }
}

@Composable
private fun TargetSuggestionsRow(
    options: List<String>,
    colors: Map<String, Int?>,
    onPick: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (alias in options) {
            Surface(
                onClick = { onPick(alias) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    colors[alias]?.let { c ->
                        Text("● ", color = Color(c or 0xFF000000.toInt()), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("#$alias", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(
    parsed: ParsedEvent,
    text: String,
    defaultDuration: Duration,
    defaultReminderMinutes: Int?,
    target: SummaryTarget?,
    tasksNoDueDate: Boolean,
) {
    val labels = SummaryLabels(
        today = stringResource(R.string.summary_today),
        tomorrow = stringResource(R.string.summary_tomorrow),
        allDay = stringResource(R.string.summary_all_day),
        locale = Locale.forLanguageTag(stringResource(R.string.date_locale)),
        datePattern = stringResource(R.string.summary_date_pattern),
        timePattern = stringResource(R.string.summary_time_pattern),
        hourUnit = stringResource(R.string.unit_hours),
        minuteUnit = stringResource(R.string.unit_minutes),
        reminderAtEvent = stringResource(R.string.summary_reminder_at_event),
        reminderBefore = stringResource(R.string.summary_reminder_before),
        noDueDate = stringResource(R.string.summary_no_due_date),
    )
    val summary = remember(parsed, text, defaultDuration, defaultReminderMinutes, labels, target, tasksNoDueDate) {
        previewSummary(
            parsed, text, defaultDuration, defaultReminderMinutes, labels,
            ZonedDateTime.now().toLocalDate(), target, tasksNoDueDate,
        )
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val normal = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            painterResource(
                when (summary.icon) {
                    SummaryIcon.CALENDAR -> R.drawable.ic_summary_calendar
                    SummaryIcon.REPEAT -> R.drawable.ic_summary_repeat
                },
            ),
            contentDescription = null,
            tint = muted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            buildAnnotatedString {
                summary.segments.forEachIndexed { i, seg ->
                    if (i > 0) append('\u2003')
                    seg.dotColor?.let { dot ->
                        pushStyle(SpanStyle(color = Color(dot or 0xFF000000.toInt())))
                        append("●\u00A0")
                        pop()
                    }
                    pushStyle(SpanStyle(color = if (seg.isAssumed) muted else normal))
                    append(seg.text.replace(' ', '\u00A0'))
                    pop()
                }
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
