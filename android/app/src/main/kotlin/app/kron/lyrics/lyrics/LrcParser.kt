package app.kron.lyrics.lyrics

import app.kron.lyrics.model.LrcLine

private val STAMP = Regex("""\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?]""")

/** Parses LRC text into lines sorted by time. A line with several timestamps becomes several lines. */
fun parseLrc(text: String): List<LrcLine> {
    val out = ArrayList<LrcLine>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        var rest = line
        val times = ArrayList<Long>()
        while (true) {
            val m = STAMP.matchAt(rest, 0) ?: break
            val (min, sec, frac) = m.destructured
            val fracMs = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').toLong()
            times += min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs
            rest = rest.substring(m.value.length)
        }
        if (times.isEmpty()) continue
        val body = rest.trim()
        for (t in times) out += LrcLine(t, body)
    }
    return out.sortedBy { it.timeMs }
}

/** Index of the last line whose time is <= [positionMs], or -1 before the first line. */
fun currentIndex(lines: List<LrcLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}
