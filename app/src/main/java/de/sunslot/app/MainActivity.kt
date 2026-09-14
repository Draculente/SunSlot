package de.sunslot.app

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.sunslot.app.settings.SettingsActivity
import de.sunslot.app.worker.SyncWeatherWorker

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncWeatherWorker.schedulePeriodic(applicationContext)

        setContent {
            MaterialTheme {
                HomeScreen(
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onAddWidget = { openWidgetPicker() }
                )
            }
        }
    }

    private fun openWidgetPicker() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, de.sunslot.app.widget.OutdoorWidgetReceiver::class.java)

        if (appWidgetManager.isRequestPinAppWidgetSupported) {
            val accepted = appWidgetManager.requestPinAppWidget(componentName, null, null)
            if (accepted) {
                Toast.makeText(this, "Widget auf deinem Homescreen platzieren…", Toast.LENGTH_LONG).show()
            } else {
                showManualAddHint()
            }
        } else {
            showManualAddHint()
        }
    }

    private fun showManualAddHint() {
        Toast.makeText(
            this,
            "Homescreen gedrückt halten → Widgets → SunSlot auswählen",
            Toast.LENGTH_LONG
        ).show()
    }
}

@Composable
private fun HomeScreen(
    onOpenSettings: () -> Unit,
    onAddWidget: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SunSlot",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Das Widget zeigt dir die besten Zeitfenster für Outdoor-Aktivitäten der nächsten 3 Tage an.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onAddWidget,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Widget hinzufügen")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Standort-Einstellungen")
            }
        }
    }
}
