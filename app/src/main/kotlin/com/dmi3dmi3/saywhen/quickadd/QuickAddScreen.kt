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
import com.dmi3dmi3.saywhen.settings.parserOrder
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// единый левый/правый инсет ярлычка и подноса относительно поля (мокапы 19b)
private val EDGE_INSET = 10.dp

/** Кнопка действия у ошибки: настройки приложения или добавление аккаунта. */
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

/** Действие при ошибке — тупиков «сообщение без кнопки» нет (задача 18). */
internal enum class ErrorAction { APP_SETTINGS, ADD_ACCOUNT }

/** Последнее созданное событие; исходная фраза и стоп-лист — для отмены с правкой. */
internal data class Created(
    val id: Long,
    val title: String,
    val sourceText: String,
    val blocked: List<IntRange>,
)

@Composable
fun QuickAddScreen(onClose: () -> Unit, prefill: String? = null) {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val settings by settingsRepo.settings.collectAsState(initial = Settings())
    // парсер из настроек: языки (30) + окно активности (30a)
    val uiLanguage = stringResource(R.string.date_locale)
    val parser = remember(
        settings.parserLanguages, uiLanguage,
        settings.activityStartHour, settings.activityEndHour,
    ) {
        MultilingualEventParser(
            parserOrder(uiLanguage, settings.parserLanguages),
            settings.activityStartHour until settings.activityEndHour,
        )
    }
    val defaultDuration = Duration.ofMinutes(settings.defaultDurationMinutes.toLong())
    var value by remember {
        mutableStateOf(TextFieldValue(prefill.orEmpty(), TextRange(prefill.orEmpty().length)))
    }
    var blocked by remember { mutableStateOf(emptyList<IntRange>()) }  // стоп-лист (задача 12)
    var error by remember { mutableStateOf<Pair<String, ErrorAction?>?>(null) }
    var created by remember { mutableStateOf<Created?>(null) }
    // автозакрытие живо, только пока после создания не было ввода (серийный ввод)
    var autoClose by remember { mutableStateOf(false) }
    val parsed = remember(value.text, blocked, parser) { parser.parse(value.text, ZonedDateTime.now(), blocked) }

    fun create() {
        val repo = CalendarRepository(context.contentResolver)
        // выбранный в настройках календарь; если он исчез — авто-каскад
        val calendar = settings.calendarId
            ?.let { id -> repo.writableCalendars().firstOrNull { it.id == id } }
            ?: repo.defaultCalendar()
        if (calendar == null) {
            error = context.getString(R.string.error_no_calendar) to ErrorAction.ADD_ACCOUNT
            return
        }
        val event = parser.parse(value.text, ZonedDateTime.now(), blocked)
        val id = CalendarWriter(context.contentResolver).insert(
            event, calendar.id, defaultDuration,
            reminderMinutes = effectiveReminder(event, settings.defaultReminderMinutes),
        )
        if (id == null) {
            error = context.getString(R.string.error_insert_failed) to null
        } else {
            requestCalendarSync(calendar)  // чтобы событие уехало на сервер сразу
            created = Created(id, event.title, value.text, blocked)
            value = TextFieldValue("")
            blocked = emptyList()
            error = null
            autoClose = true
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

    // тап по фону — отмена
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
        // одиночный сценарий: ничего не ввёл после создания → окно закрывается
        // само; таймаут — системный (скринридер/«время на действие» растягивают)
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
        // тактильное «создано» — отклик не глядя на экран, в пару к кивку
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
                // поле многострочное (длинные фразы переносятся), поэтому Enter
                // физической клавиатуры приходит переносом строки — превращаем
                // его в «Создать» (Enter = Create, чеклист serial-add)
                val new = if ('\n' in raw.text) {
                    val cleaned = raw.text.replace("\n", "")
                    if (cleaned == value.text) {
                        if (value.text.isNotBlank()) createWithPermissions()
                        null  // чистый Enter — текст не меняется
                    } else {
                        raw.copy(text = cleaned, selection = TextRange(cleaned.length))
                    }
                } else {
                    raw
                }
                if (new != null) {
                    // жесты исключения (тап/backspace) перехватываются здесь
                    val (next, nextBlocked) = applyEdit(value, new, blocked, parsed.matches)
                    value = next
                    blocked = nextBlocked
                    error = null
                    autoClose = false  // печатает серию — таймер снят насовсем
                }
            },
            parsed = parsed,
            defaultDuration = defaultDuration,
            defaultReminderMinutes = settings.defaultReminderMinutes,
            nodEnabled = settings.nodEnabled,
            error = error,
            created = created,
            onCreate = ::createWithPermissions,
            // тап по «Создано» — открыть событие глазами; уход в календарь
            // завершает сценарий, окно закрываем
            onOpenCreated = {
                created?.let { done ->
                    try {
                        context.startActivity(
                            // MIME явно: без него системе нужен getType() провайдера,
                            // невидимого без <queries> (Android 11+), и резолв уходит
                            // мимо календаря (ревью F-Droid)
                            Intent(Intent.ACTION_VIEW).setDataAndType(
                                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, done.id),
                                // у CalendarContract.Events нет константы item-типа
                                "vnd.android.cursor.item/event",
                            ),
                        )
                        onClose()
                    } catch (_: ActivityNotFoundException) {
                        error = context.getString(R.string.error_no_calendar_app) to null
                    }
                }
            },
            onUndo = {
                created?.let { done ->
                    CalendarWriter(context.contentResolver).delete(done.id)
                    // фраза и стоп-лист возвращаются в поле для правки
                    value = TextFieldValue(done.sourceText, TextRange(done.sourceText.length))
                    blocked = done.blocked
                    created = null
                    autoClose = false
                }
            },
        )
    }
}

/** Силуэт 19b: этикетка → поле (верхняя кромка) → поднос. */
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
    onCreate: () -> Unit,
    onOpenCreated: () -> Unit,
    onUndo: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // клавиатуру поднимаем после окончания анимации открытия (зум лаунчера
    // от виджета неотключаем) — иначе две анимации накладываются и дёргаются;
    // подъём карточки к клавиатуре дальше анимирует imePadding синхронно с IME
    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // фокус поля поднят сюда: рамка ярлычка красится тем же цветом, что рамка
    // поля — пересечение их контуров невидимо (фидбек владельца, v1.5.0)
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
            // глушим тапы, чтобы не проваливались в фон-«закрыть»; не clickable —
            // тот создаёт для скринридера громадную безымянную цель-пустышку
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
            createEnabled = value.text.isNotBlank(),
            onCreate = onCreate,
            onOpenCreated = onOpenCreated,
            onUndo = onUndo,
        )
    }
}

/**
 * Ярлычок-этикетка на рамке поля: единственное место имени, тап — настройки,
 * кивает на «создано» (гейты: настройка и системные анимации). Нижняя кромка
 * перекрывает рамку поля на толщину линии — плашка «сидит» на границе.
 */
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
    // не Surface(onClick): он раздувает layout до touch-цели 48dp и плашка
    // «отрывается» от поля — кликабельность внутри, без инфляции
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

/** Поле — верхняя кромка конструкции; пилюли подсветки рисуются под текстом. */
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
            // плейсхолдер — просто Text в decorationBox; для скринридера
            // связываем его с пустым полем вручную
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

/**
 * Скруглённые пилюли под распознанными диапазонами (19b, вместо квадратных
 * спанов): по границам строк из TextLayoutResult, многострочность —
 * посегментно. Смежные диапазоны (между ними только пробелы) сливаются в
 * одну пилюлю — граница «понял/не понял» остаётся единственным визуальным
 * событием, без ряби зазоров; вертикальный инсет отличает маркер от
 * системного выделения текста.
 */
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

/** Поднос: одна зона «итог + Создать», высота констант (резерв двух строк). */
@Composable
private fun Tray(
    parsed: ParsedEvent,
    text: String,
    defaultDuration: Duration,
    defaultReminderMinutes: Int?,
    error: Pair<String, ErrorAction?>?,
    created: Created?,
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
                        // появление ошибки озвучивается скринридером само
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
                    if (created != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.quickadd_created, created.title),
                                Modifier
                                    .weight(1f)
                                    // появление «создано» озвучивается само — весь
                                    // остальной фидбек (кивок, вибрация) невербальный
                                    .semantics { liveRegion = LiveRegionMode.Polite }
                                    // тап — открыть событие в календаре; «Отменить» — отдельная
                                    // цель; рипл оставлен — единственный мгновенный отклик тапа
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
                    } else {
                        SummaryLine(parsed, text, defaultDuration, defaultReminderMinutes)
                    }
                }
                Button(onClick = onCreate, enabled = createEnabled) {
                    Text(stringResource(R.string.quickadd_create))
                }
            }
        }
    }
}

/** Строка-итог: полное будущее событие; дефолты — приглушённым. */
@Composable
private fun SummaryLine(
    parsed: ParsedEvent,
    text: String,
    defaultDuration: Duration,
    defaultReminderMinutes: Int?,
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
    )
    val summary = remember(parsed, text, defaultDuration, defaultReminderMinutes, labels) {
        previewSummary(
            parsed, text, defaultDuration, defaultReminderMinutes, labels,
            ZonedDateTime.now().toLocalDate(),
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
                    if (i > 0) {
                        pushStyle(SpanStyle(color = muted))
                        append(" · ")
                        pop()
                    }
                    pushStyle(SpanStyle(color = if (seg.isDefault) muted else normal))
                    append(seg.text)
                    pop()
                }
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
