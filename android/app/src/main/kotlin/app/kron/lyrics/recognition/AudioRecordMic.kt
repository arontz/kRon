package app.kron.lyrics.recognition

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import app.kron.lyrics.log.LogKind
import app.kron.lyrics.log.activityLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Records from the microphone into memory: 44.1 kHz, mono, 16-bit. The plain MIC source is used on purpose, because
 * the voice-recognition source applies speech-tuned noise suppression that can hurt music. The audio never touches
 * the disk, and the recorder is always released, including when the recording is cancelled.
 */
class AudioRecordMic : MicRecorder {
    @SuppressLint("MissingPermission") // the caller checks the permission before recording
    override suspend fun record(durationMs: Long, onProgress: (Long, Float) -> Unit): ByteArray? = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return@withContext null
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
            )
        } catch (_: SecurityException) {
            return@withContext null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withContext null
        }
        try {
            activityLog.add(LogKind.MEDIA, "Recording ${durationMs / 1000} s from the microphone (kept in memory only)")
            recorder.startRecording()
            val total = (SAMPLE_RATE * 2L * durationMs / 1000).toInt()
            val out = ByteArrayOutputStream(total)
            val buffer = ByteArray(minBuffer)
            var read = 0
            while (read < total) {
                coroutineContext.ensureActive()
                val n = recorder.read(buffer, 0, minOf(buffer.size, total - read))
                if (n <= 0) return@withContext null
                out.write(buffer, 0, n)
                read += n
                onProgress(read * 1000L / (SAMPLE_RATE * 2), rmsLevel(buffer, n))
            }
            pcm16ToWav(out.toByteArray(), SAMPLE_RATE)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
    }
}
