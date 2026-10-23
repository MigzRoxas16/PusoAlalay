package ph.pusoalalay.app

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import ph.pusoalalay.app.data.localizedContext
import ph.pusoalalay.app.reminders.Reminders
import ph.pusoalalay.app.ui.PusoApp
import ph.pusoalalay.app.ui.PusoTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(localizedContext(newBase))
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent health records from appearing in screenshots and the recent-app preview.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        Reminders.channel(this)
        Reminders.schedule(this)
        setContent {
            PusoTheme {
                val model: HealthViewModel = viewModel()
                PusoApp(model, (application as PusoApplication).settings, onLanguageChanged = { recreate() })
            }
        }
    }
}
