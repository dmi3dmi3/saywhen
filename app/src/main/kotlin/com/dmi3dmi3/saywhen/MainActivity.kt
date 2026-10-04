package com.dmi3dmi3.saywhen

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.dmi3dmi3.saywhen.quickadd.QuickAddActivity
import com.dmi3dmi3.saywhen.settings.SettingsScreen
import com.dmi3dmi3.saywhen.ui.SayWhenTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SayWhenTheme {
                Surface(
                    Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Box(Modifier.statusBarsPadding()) {
                        SettingsScreen(onOpenQuickAdd = { prefill ->
                            startActivity(
                                Intent(this@MainActivity, QuickAddActivity::class.java)
                                    .putExtra(QuickAddActivity.EXTRA_PREFILL, prefill),
                            )
                        })
                    }
                }
            }
        }
    }
}
