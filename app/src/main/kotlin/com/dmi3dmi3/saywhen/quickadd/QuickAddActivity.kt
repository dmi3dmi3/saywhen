package com.dmi3dmi3.saywhen.quickadd

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import com.dmi3dmi3.saywhen.ui.SayWhenTheme

/** Полупрозрачное окно быстрого ввода; открывается виджетом (задача 10). */
class QuickAddActivity : AppCompatActivity() {

    companion object {
        /** Предзаполнение поля — тапабельные примеры главного экрана (задача 18). */
        const val EXTRA_PREFILL = "prefill"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SayWhenTheme {
                QuickAddScreen(
                    onClose = { finish() },
                    prefill = intent.getStringExtra(EXTRA_PREFILL),
                )
            }
        }
    }
}
