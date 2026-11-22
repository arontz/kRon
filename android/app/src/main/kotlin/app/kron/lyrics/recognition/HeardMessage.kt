package app.kron.lyrics.recognition

/**
 * The Activity log line for a match. Only the clip position is formatted; the title and artist are joined on as plain
 * text, because a `%` in a song name must never be read as a format instruction.
 */
fun heardLogMessage(m: Recognition.Match): String {
    val clipStart = "%d:%02d".format(m.positionMs / 60_000, m.positionMs / 1_000 % 60)
    return "AudD recognized \"" + m.title + "\" by " + m.artist.ifBlank { "unknown artist" } + ", clip started at " + clipStart
}
