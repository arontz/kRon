package app.kron.lyrics.recognition.shazam

/**
 * Test vectors made by running shazamio-core 2.1.0's own fingerprinting (MIT) on two deterministic 4-second synthetic
 * signals, 16 kHz mono 16-bit: a sweep, and a mix of tones, a beat and noise. `.peaks` lists "band frame magnitude
 * refinedBin" per line; `.uri` is the signature data URI the reference produced for the same signal.
 */
object ReferenceVectors {
    val names = listOf("sweep", "mix")

    private fun bytes(name: String): ByteArray =
        ReferenceVectors::class.java.getResourceAsStream("/shazam/$name")!!.use { it.readBytes() }

    fun uri(name: String): String = String(bytes("$name.uri")).trim()

    fun wavSamples(name: String): ShortArray {
        val b = bytes("$name.wav")
        val samples = ShortArray((b.size - 44) / 2)
        for (i in samples.indices) samples[i] = ((b[44 + i * 2].toInt() and 0xff) or (b[45 + i * 2].toInt() shl 8)).toShort()
        return samples
    }

    /** Peaks per band, each band's list in frame order, as the reference listed them. */
    fun peaks(name: String): Map<FrequencyBand, List<FrequencyPeak>> =
        String(bytes("$name.peaks")).lineSequence().filter { it.isNotBlank() }
            .map { line ->
                val (band, frame, magnitude, bin) = line.trim().split(' ').map { it.toInt() }
                FrequencyBand.fromId(band) to FrequencyPeak(frame, magnitude, bin)
            }
            .groupBy({ it.first }, { it.second })
}
