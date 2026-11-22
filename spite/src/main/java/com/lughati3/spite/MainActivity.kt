package com.lughati3.spite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lughati3.spite.history.History
import com.lughati3.spite.history.PrefsHistoryStore
import com.lughati3.spite.ui.AppRoot
import com.lughati3.spite.ui.theme.TakiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        History.init(PrefsHistoryStore(applicationContext))
        enableEdgeToEdge()
        setContent {
            TakiTheme {
                AppRoot()
            }
        }
    }
}
