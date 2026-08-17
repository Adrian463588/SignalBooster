package com.signalbooster.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.signalbooster.app.presentation.navigation.AppNavigation
import com.signalbooster.app.platform.SettingsIntentFactory
import com.signalbooster.app.platform.SettingsLaunchResult
import com.signalbooster.app.ui.theme.SignalBoosterTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject
    lateinit var settingsIntentFactory: SettingsIntentFactory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SignalBoosterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        onOpenSettings = { destination ->
                            when (val result = settingsIntentFactory.launch(destination)) {
                                is SettingsLaunchResult.Started -> Unit
                                is SettingsLaunchResult.Unavailable -> showSettingsFailure(result.reason)
                                is SettingsLaunchResult.Failed -> showSettingsFailure(result.reason)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun showSettingsFailure(reason: String) {
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
    }
}
