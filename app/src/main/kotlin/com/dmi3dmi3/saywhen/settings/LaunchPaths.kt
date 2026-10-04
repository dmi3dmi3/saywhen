package com.dmi3dmi3.saywhen.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

internal class LaunchPaths(private val context: Context) {

    fun isEnabled(alias: String): Boolean =
        launchPathEnabled(context.packageManager.getComponentEnabledSetting(component(alias)))

    fun setEnabled(alias: String, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(alias),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    private fun component(alias: String) = ComponentName(context, alias)

    companion object {
        const val LAUNCHER = "com.dmi3dmi3.saywhen.quickadd.QuickAddLauncher"

        const val INSERT = "com.dmi3dmi3.saywhen.quickadd.QuickAddInsert"
    }
}

internal fun launchPathEnabled(state: Int): Boolean =
    state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
