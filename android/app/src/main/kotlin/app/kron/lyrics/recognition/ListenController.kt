package app.kron.lyrics.recognition

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Records from the microphone. Returns a WAV, or null when the microphone cannot be used. */
interface MicRecorder {
    suspend fun record(durationMs: Long, onProgress: (elapsedMs: Long, level: Float) -> Unit): ByteArray?
}

interface SongRecognizer {
    suspend fun recognize(wav: ByteArray, token: String): Recognition
}

sealed interface ListenState {
    data object Idle : ListenState
    data object NeedsToken : ListenState

    /** The system permission dialog is up. */
    data object NeedsPermission : ListenState
    data object PermissionDenied : ListenState
    data class Listening(val progress: Float, val level: Float) : ListenState
    data object Identifying : ListenState
    data class Found(val match: Recognition.Match) : ListenState
    data object NoMatch : ListenState
    data object MicUnavailable : ListenState
    data class Failed(val error: RecognitionError) : ListenState
}

/** About what the service analyzes in one request. */
const val LISTEN_MS = 10_000L

/**
 * The Listen button: checks there is a token and microphone permission, records, asks the service, and reports. All
 * outside things (microphone, service, permission, clock) are handed in, so the flow can be tested without them.
 */
class ListenController(
    private val scope: CoroutineScope,
    private val token: () -> String?,
    private val hasMicPermission: () -> Boolean,
    private val requestMicPermission: () -> Unit,
    private val recorder: MicRecorder,
    private val recognizer: SongRecognizer,
    private val elapsedNow: () -> Long,
    private val onMatch: (match: Recognition.Match, captureStartElapsedMs: Long) -> Unit,
) {
    private val _state = MutableStateFlow<ListenState>(ListenState.Idle)
    val state: StateFlow<ListenState> = _state

    private var job: Job? = null

    /** The Listen tap. Does nothing while a recording or a lookup is already running. */
    fun listen() {
        val s = _state.value
        if (s is ListenState.Listening || s is ListenState.Identifying) return
        if (token().isNullOrBlank()) {
            _state.value = ListenState.NeedsToken
            return
        }
        if (!hasMicPermission()) {
            _state.value = ListenState.NeedsPermission
            requestMicPermission()
            return
        }
        start()
    }

    /** The answer to the system permission dialog. */
    fun onPermissionResult(granted: Boolean) {
        if (_state.value != ListenState.NeedsPermission) return
        if (granted) start() else _state.value = ListenState.PermissionDenied
    }

    /** Stops a recording or lookup in progress, for example when the user leaves the app. */
    fun cancel() {
        job?.cancel()
        job = null
        val s = _state.value
        if (s is ListenState.Listening || s is ListenState.Identifying) _state.value = ListenState.Idle
    }

    fun reset() {
        cancel()
        _state.value = ListenState.Idle
    }

    private fun start() {
        val tok = token()?.trim().orEmpty()
        if (tok.isEmpty()) {
            _state.value = ListenState.NeedsToken
            return
        }
        val captureStart = elapsedNow()
        _state.value = ListenState.Listening(progress = 0f, level = 0f)
        job = scope.launch {
            val wav = recorder.record(LISTEN_MS) { elapsed, level ->
                _state.value = ListenState.Listening((elapsed.toFloat() / LISTEN_MS).coerceIn(0f, 1f), level)
            }
            if (wav == null) {
                _state.value = ListenState.MicUnavailable
                return@launch
            }
            _state.value = ListenState.Identifying
            when (val r = recognizer.recognize(wav, tok)) {
                is Recognition.Match -> {
                    _state.value = ListenState.Found(r)
                    onMatch(r, captureStart)
                }
                Recognition.NoMatch -> _state.value = ListenState.NoMatch
                is Recognition.Failed -> _state.value = ListenState.Failed(r.error)
            }
        }
    }
}
