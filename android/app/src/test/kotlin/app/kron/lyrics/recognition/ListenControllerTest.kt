package app.kron.lyrics.recognition

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListenControllerTest {
    private val match = Recognition.Match("Warriors", "Imagine Dragons", "Album", 152_000)

    private class FakeRecorder(
        var result: ByteArray? = ByteArray(10),
        private val progress: List<Pair<Long, Float>> = emptyList(),
        private val gate: CompletableDeferred<Unit>? = null,
    ) : MicRecorder {
        var calls = 0
        override suspend fun record(durationMs: Long, onProgress: (Long, Float) -> Unit): ByteArray? {
            calls++
            progress.forEach { onProgress(it.first, it.second) }
            gate?.await()
            return result
        }
    }

    private class FakeRecognizer(var result: Recognition) : SongRecognizer {
        var calls = 0
        var lastToken: String? = null
        override suspend fun recognize(wav: ByteArray, token: String): Recognition {
            calls++
            lastToken = token
            return result
        }
    }

    private class Env(
        scope: TestScope,
        val recorder: FakeRecorder = FakeRecorder(),
        val recognizer: FakeRecognizer = FakeRecognizer(Recognition.NoMatch),
        var token: String? = "tok",
        var micGranted: Boolean = true,
    ) {
        var permissionRequests = 0
        var matches = mutableListOf<Pair<Recognition.Match, Long>>()
        val controller = ListenController(
            scope = scope.backgroundScope,
            token = { token },
            hasMicPermission = { micGranted },
            requestMicPermission = { permissionRequests++ },
            recorder = recorder,
            recognizer = recognizer,
            elapsedNow = { 5_000L },
            onMatch = { m, start -> matches += m to start },
        )
        val state get() = controller.state.value
    }

    @Test
    fun withoutATokenItAsksForOneAndRecordsNothing() = runTest {
        val e = Env(this, token = null)
        e.controller.listen()
        runCurrent()
        assertEquals(ListenState.NeedsToken, e.state)
        assertEquals(0, e.recorder.calls)
        assertEquals(0, e.permissionRequests)
        e.token = "   "
        e.controller.listen()
        assertEquals(ListenState.NeedsToken, e.state)
    }

    @Test
    fun withoutMicrophonePermissionItAsksFirstAndRecordsOnlyOnceGranted() = runTest {
        val e = Env(this, micGranted = false)
        e.controller.listen()
        runCurrent()
        assertEquals(ListenState.NeedsPermission, e.state)
        assertEquals(1, e.permissionRequests)
        assertEquals(0, e.recorder.calls)

        e.recognizer.result = match
        e.controller.onPermissionResult(granted = true)
        runCurrent()
        assertEquals(1, e.recorder.calls)
        assertEquals(ListenState.Found(match), e.state)
    }

    @Test
    fun aRefusedPermissionIsTold() = runTest {
        val e = Env(this, micGranted = false)
        e.controller.listen()
        e.controller.onPermissionResult(granted = false)
        runCurrent()
        assertEquals(ListenState.PermissionDenied, e.state)
        assertEquals(0, e.recorder.calls)
    }

    @Test
    fun aMatchIsReportedWithTheMomentTheClipStarted() = runTest {
        val e = Env(this, recognizer = FakeRecognizer(match))
        e.controller.listen()
        runCurrent()
        assertEquals(ListenState.Found(match), e.state)
        assertEquals(listOf(match to 5_000L), e.matches)
        assertEquals("tok", e.recognizer.lastToken)
    }

    @Test
    fun noMatchIsTold() = runTest {
        val e = Env(this, recognizer = FakeRecognizer(Recognition.NoMatch))
        e.controller.listen()
        runCurrent()
        assertEquals(ListenState.NoMatch, e.state)
        assertEquals(emptyList<Any>(), e.matches)
    }

    @Test
    fun aFailureKeepsItsKind() = runTest {
        for (error in RecognitionError.entries) {
            val e = Env(this, recognizer = FakeRecognizer(Recognition.Failed(error)))
            e.controller.listen()
            runCurrent()
            assertEquals(ListenState.Failed(error), e.state)
        }
    }

    @Test
    fun anUnavailableMicrophoneIsTold() = runTest {
        val e = Env(this, recorder = FakeRecorder(result = null))
        e.controller.listen()
        runCurrent()
        assertEquals(ListenState.MicUnavailable, e.state)
        assertEquals(0, e.recognizer.calls)
    }

    @Test
    fun progressShowsWhileListening() = runTest {
        val gate = CompletableDeferred<Unit>()
        val e = Env(this, recorder = FakeRecorder(progress = listOf(5_000L to 0.3f), gate = gate))
        e.controller.listen()
        runCurrent()
        assertEquals(ListenState.Listening(progress = 0.5f, level = 0.3f), e.state)
        gate.complete(Unit)
        runCurrent()
        assertEquals(ListenState.NoMatch, e.state)
    }

    @Test
    fun tappingAgainWhileBusyDoesNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val e = Env(this, recorder = FakeRecorder(gate = gate))
        e.controller.listen()
        runCurrent()
        e.controller.listen()
        e.controller.listen()
        runCurrent()
        assertEquals(1, e.recorder.calls)
    }

    @Test
    fun cancellingStopsRecordingAndNeverCallsTheService() = runTest {
        val gate = CompletableDeferred<Unit>()
        val e = Env(this, recorder = FakeRecorder(gate = gate))
        e.controller.listen()
        runCurrent()
        assertEquals(true, e.state is ListenState.Listening)
        e.controller.cancel()
        gate.complete(Unit)
        runCurrent()
        assertEquals(ListenState.Idle, e.state)
        assertEquals(0, e.recognizer.calls)
        assertEquals(emptyList<Any>(), e.matches)
    }

    @Test
    fun youCanListenAgainAfterAResult() = runTest {
        val e = Env(this, recognizer = FakeRecognizer(Recognition.NoMatch))
        e.controller.listen()
        runCurrent()
        e.recognizer.result = match
        e.controller.listen()
        runCurrent()
        assertEquals(2, e.recorder.calls)
        assertEquals(ListenState.Found(match), e.state)
    }

    @Test
    fun resetReturnsToIdle() = runTest {
        val e = Env(this, recognizer = FakeRecognizer(match))
        e.controller.listen()
        runCurrent()
        e.controller.reset()
        assertEquals(ListenState.Idle, e.state)
    }
}
