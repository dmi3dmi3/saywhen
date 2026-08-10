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

/** Ярлык приложения ведёт в настройки; основной вход — виджет (задача 10). */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SayWhenTheme {
                // фон — на весь экран и под статус-бар (иначе в тёмной теме
                // сверху и снизу полосы фона окна); отступ — только у контента.
                // surfaceContainerLow: без крайностей чёрного/белого — те же
                // «серые» тона, что у окна ввода (фидбек владельца, v1.5.0)
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
