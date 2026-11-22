package app.kron.lyrics.recognition

import android.content.SharedPreferences

/** Which service the Listen button uses. AudD is the default: it is official and supported. */
enum class RecognizerChoice { Audd, Shazam }

/** Reads a saved choice; anything missing or unknown means AudD. */
fun recognizerChoiceFrom(saved: String?): RecognizerChoice = RecognizerChoice.entries.firstOrNull { it.name == saved } ?: RecognizerChoice.Audd

class RecognizerChoiceStore(private val prefs: SharedPreferences) {
    var choice: RecognizerChoice
        get() = recognizerChoiceFrom(prefs.getString(KEY, null))
        set(value) {
            prefs.edit().putString(KEY, value.name).apply()
        }

    private companion object {
        const val KEY = "recognizer"
    }
}
