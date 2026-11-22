package app.kron.lyrics.recognition

import android.content.SharedPreferences

/** The user's own AudD token, kept in this app's private storage. It is never logged and never put in an address. */
class AuddTokenStore(private val prefs: SharedPreferences) {
    var token: String?
        get() = prefs.getString(KEY, null)?.trim()?.takeIf { it.isNotEmpty() }
        set(value) {
            val clean = value?.trim()?.takeIf { it.isNotEmpty() }
            prefs.edit().apply { if (clean == null) remove(KEY) else putString(KEY, clean) }.apply()
        }

    private companion object {
        const val KEY = "audd_token"
    }
}
