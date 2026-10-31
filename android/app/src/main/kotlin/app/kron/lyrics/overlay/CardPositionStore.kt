package app.kron.lyrics.overlay

import android.content.SharedPreferences

class CardPositionStore(private val prefs: SharedPreferences) {
    fun load(): Pos? =
        if (prefs.contains(KEY_X) && prefs.contains(KEY_Y)) Pos(prefs.getInt(KEY_X, 0), prefs.getInt(KEY_Y, 0)) else null

    fun save(p: Pos) {
        prefs.edit().putInt(KEY_X, p.x).putInt(KEY_Y, p.y).apply()
    }

    private companion object {
        const val KEY_X = "x"
        const val KEY_Y = "y"
    }
}
