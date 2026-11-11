package com.dmi3dmi3.saywhen.quickadd

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.dmi3dmi3.saywhen.MainActivity
import com.dmi3dmi3.saywhen.R

class CreateShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shortcut = ShortcutInfoCompat.Builder(this, SHORTCUT_ID)
            .setShortLabel(getString(R.string.widget_label))
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher_add))
            .setActivity(ComponentName(this, MainActivity::class.java))
            .setIntent(Intent(Intent.ACTION_MAIN).setClass(this, QuickAddActivity::class.java))
            .build()
        setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, shortcut))
        finish()
    }

    private companion object {
        const val SHORTCUT_ID = "quick_add"
    }
}
