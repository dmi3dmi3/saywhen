package com.dmi3dmi3.saywhen.target

import com.dmi3dmi3.saywhen.calendar.CalendarInfo
import com.dmi3dmi3.saywhen.settings.calendarKey
import com.dmi3dmi3.saywhen.tasks.TaskListInfo

sealed class TargetRef(val key: String) {
    data class Calendar(val id: Long) : TargetRef(calendarKey(id))
    data class TaskList(val id: Long) : TargetRef(tasksKey(id))
    data object Tasks : TargetRef(TASKS_KEY)
}

fun tasksKey(id: Long): String = "tasks/$id"

val TargetRef?.writesTask: Boolean
    get() = this is TargetRef.TaskList || this is TargetRef.Tasks

const val TASKS_KEY = "tasks"

fun parseTargetKey(key: String): TargetRef? {
    if (key == TASKS_KEY) return TargetRef.Tasks
    val parts = key.split('/', limit = 2)
    if (parts.size != 2) return null
    val id = parts[1].toLongOrNull() ?: return null
    return when (parts[0]) {
        "calendar" -> TargetRef.Calendar(id)
        "tasks" -> TargetRef.TaskList(id)
        else -> null
    }
}

data class TargetInfo(val key: String, val name: String, val account: String, val color: Int?)

val TASKS_ROOT = TargetInfo(TASKS_KEY, "Tasks.org", "tasks.org", null)

fun targetsOf(
    calendars: List<CalendarInfo>?,
    taskLists: List<TaskListInfo>?,
    tasksEnabled: Boolean,
): List<TargetInfo> =
    calendars.orEmpty().map { it.toTargetInfo() } +
        if (tasksEnabled && taskLists != null) listOf(TASKS_ROOT) + taskLists.map { it.toTargetInfo() } else emptyList()

fun CalendarInfo.toTargetInfo(): TargetInfo =
    TargetInfo(calendarKey(id), name, accountName, color.takeIf { it != 0 })
