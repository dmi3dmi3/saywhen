package com.dmi3dmi3.saywhen.quickadd

import android.os.Bundle
import android.provider.CalendarContract
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.dmi3dmi3.saywhen.ui.SayWhenTheme

class QuickAddActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PREFILL = "prefill"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SayWhenTheme {
                QuickAddScreen(
                    onClose = { finish() },
                    prefill = prefillFor(
                        action = intent.action,
                        prefill = intent.getStringExtra(EXTRA_PREFILL),
                        insertTitle = intent.getStringExtra(CalendarContract.Events.TITLE),
                    ),
                )
            }
        }
    }
}
