package com.dmi3dmi3.saywhen.tasks

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.dmi3dmi3.saywhen.parser.ParsedEvent
import com.dmi3dmi3.saywhen.target.TargetInfo
import com.dmi3dmi3.saywhen.target.tasksKey

data class TaskListInfo(val id: Long, val title: String, val account: String, val color: Int) {
    fun toTargetInfo(): TargetInfo = TargetInfo(tasksKey(id), title, account, color.takeIf { it != 0 })
}

internal fun versionAtLeast(version: String, minimum: String): Boolean {
    fun parts(v: String) = v.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val a = parts(version)
    val b = parts(minimum)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return true
}

class TasksRepository(private val context: Context) {

    companion object {
        const val AUTHORITY = "org.tasks.api"
        const val READ = "org.tasks.permission.READ_TASKS"
        const val WRITE = "org.tasks.permission.WRITE_TASKS"
        const val MIN_VERSION = "15.12"
        val PERMISSIONS = arrayOf(READ, WRITE)
        private val BASE: Uri = Uri.parse("content://$AUTHORITY/v0")
        private val TASKS: Uri = Uri.withAppendedPath(BASE, "tasks")
        private val REMINDERS: Uri = Uri.withAppendedPath(BASE, "reminders")
        private val LISTS: Uri = Uri.withAppendedPath(BASE, "lists")
        private val ACCOUNTS: Uri = Uri.withAppendedPath(BASE, "accounts")
    }

    fun isInstalled(): Boolean {
        val provider = context.packageManager.resolveContentProvider(AUTHORITY, 0) ?: return false
        val version = runCatching {
            context.packageManager.getPackageInfo(provider.packageName, 0).versionName
        }.getOrNull() ?: return false
        return versionAtLeast(version, MIN_VERSION)
    }

    fun hasPermissions(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun writableLists(): List<TaskListInfo>? = retry {
        val accounts = mutableMapOf<Long, String>()
        context.contentResolver.query(ACCOUNTS, arrayOf("_id", "name"), null, null, null)?.use { c ->
            while (c.moveToNext()) accounts[c.getLong(0)] = c.getString(1)
        }
        val uri = LISTS.buildUpon()
            .appendQueryParameter("access", "owner").appendQueryParameter("access", "read_write")
            .appendQueryParameter("limit", "500").build()
        context.contentResolver.query(uri, arrayOf("_id", "title", "color", "account_id"), null, null, null)
            ?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(TaskListInfo(c.getLong(0), c.getString(1), accounts[c.getLong(3)].orEmpty(), c.getInt(2)))
                    }
                }
            }
    }

    fun insert(event: ParsedEvent, listId: Long?, noDueDate: Boolean, reminderMinutes: Int?): Long? = retry {
        val task = ContentProviderOperation.newInsert(TASKS).apply {
            taskValues(event, listId, noDueDate).forEach { (column, value) ->
                when (value) {
                    is Long -> withValue(column, value)
                    is Int -> withValue(column, value)
                    else -> withValue(column, value.toString())
                }
            }
        }.build()
        val ops = arrayListOf(task)
        reminderMinutes?.let { minutes ->
            ops += ContentProviderOperation.newInsert(REMINDERS)
                .withValueBackReference("task_id", 0)
                .withValue("type", "relative_due")
                .withValue("offset_ms", reminderOffsetMs(minutes))
                .build()
        }
        val results = context.contentResolver.applyBatch(AUTHORITY, ops)
        results.firstOrNull()?.uri?.let { ContentUris.parseId(it) }
    }

    fun delete(taskId: Long): Boolean =
        retry { context.contentResolver.delete(ContentUris.withAppendedId(TASKS, taskId), null, null) } == 1

    fun viewIntent(taskId: Long): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(TASKS, taskId))

    private fun <T> retry(block: () -> T?): T? {
        repeat(2) { attempt ->
            try {
                block()?.let { return it }
            } catch (e: SecurityException) {
                throw e
            } catch (_: RuntimeException) {
            }
            if (attempt == 0) Thread.sleep(300)
        }
        return null
    }
}
