package ph.pusoalalay.app.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    var language: String
        get() = prefs.getString("language", "fil") ?: "fil"
        set(value) { prefs.edit().putString("language", value).apply() }
    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding", false)
        set(value) { prefs.edit().putBoolean("onboarding", value).apply() }
    var reminderEnabled: Boolean
        get() = prefs.getBoolean("reminderEnabled", false)
        set(value) { prefs.edit().putBoolean("reminderEnabled", value).apply() }
    var reminderHour: Int
        get() = prefs.getInt("reminderHour", 19)
        set(value) { prefs.edit().putInt("reminderHour", value).apply() }
    /** The signed-in local account, or null when signed out. */
    var currentUserId: String?
        get() = prefs.getString("currentUserId", null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove("currentUserId") else editor.putString("currentUserId", value)
            editor.apply()
        }
    var reminderMinute: Int
        get() = prefs.getInt("reminderMinute", 0)
        set(value) { prefs.edit().putInt("reminderMinute", value).apply() }
}

fun localizedContext(base: Context): Context {
    val config = Configuration(base.resources.configuration)
    config.setLocale(Locale.forLanguageTag(Settings(base).language))
    return base.createConfigurationContext(config)
}
