package ph.pusoalalay.app

import android.app.Application
import ph.pusoalalay.app.data.*

class PusoApplication : Application() {
    val database by lazy { HealthDatabase.create(this) }
    val cipher by lazy { RecordCipher() }
    val settings by lazy { Settings(this) }
}
