package app.kron.lyrics.media

import app.kron.lyrics.model.PlaybackSnapshot
import app.kron.lyrics.model.Track
import app.kron.lyrics.recognition.Recognition

/** A song the microphone recognized, shown until [expiresAtMs] (on the elapsed-realtime clock). */
data class RecognizedSong(val snapshot: PlaybackSnapshot, val expiresAtMs: Long)

/**
 * The recognition service does not say how long the song is, and kRon cannot tell when the radio or TV stops, so a
 * heard song is shown for a fixed time and can be dismissed.
 */
const val RECOGNIZED_TTL_MS = 7 * 60_000L

/** Whether the heard song is what is being shown: nothing plays on the phone and it has not expired. */
fun heardSongShown(session: PlaybackSnapshot, recognized: RecognizedSong?, nowMs: Long): Boolean =
    session.track == null && recognized != null && nowMs < recognized.expiresAtMs

/** A song playing on this phone wins; otherwise a heard song that has not expired; otherwise what the session says. */
fun activeSnapshot(session: PlaybackSnapshot, recognized: RecognizedSong?, nowMs: Long): PlaybackSnapshot =
    if (heardSongShown(session, recognized, nowMs)) recognized!!.snapshot else session

/** Something playing on this phone replaces whatever was heard. */
fun recognizedAfterSession(session: PlaybackSnapshot, recognized: RecognizedSong?): RecognizedSong? =
    if (session.track != null) null else recognized

/**
 * The now-playing picture for a match. Assumes the reported position is where the recorded clip begins, so the
 * lyrics clock counts on from there starting at the moment recording began. If AudD turns out to mean another moment
 * of the clip, this is the one place to change.
 */
fun recognizedSnapshot(match: Recognition.Match, captureStartElapsedMs: Long): PlaybackSnapshot = PlaybackSnapshot(
    track = Track(match.title, match.artist, durationMs = 0),
    positionMs = match.positionMs,
    speed = 1f,
    playing = true,
    updatedAtMs = captureStartElapsedMs,
)
