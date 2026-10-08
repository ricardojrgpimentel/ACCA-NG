package mattecarra.accapp.utils

import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors

class CommandTraceTest {
    @Test fun snapshotsKeepCommandsAndTheirOwnResults() {
        val trace = CommandTrace()
        val first = trace.started("acc -s pause_capacity=80")
        val second = trace.started("acc -D")
        val inFlight = trace.steps.value
        trace.finished(second, 9)
        trace.finished(first, 0)
        assertTrue(inFlight.all { it.running })
        assertEquals(listOf(0, 9), trace.steps.value.map { it.exitCode })
        assertTrue(trace.steps.value.none { it.running })
        assertNull(trace.steps.value[1].error) // A state code is not necessarily a failure.
    }

    @Test fun interruptedCommandsRetainTheDiagnostic() {
        val trace = CommandTrace()
        val step = trace.started("acc -D start")
        trace.finished(step, null, "Root shell unavailable")
        assertFalse(trace.steps.value.single().running)
        assertNull(trace.steps.value.single().exitCode)
        assertEquals("Root shell unavailable", trace.steps.value.single().error)
    }

    @Test fun longActionsAndDiagnosticsAreBoundedWithoutMixingStepNumbers() {
        val trace = CommandTrace()
        repeat(80) { trace.started("command-$it") }
        trace.finished(1, 0) // Old dropped command must not update a different row.
        assertEquals(64, trace.steps.value.size)
        assertEquals(17, trace.steps.value.first().number)
        assertTrue(trace.steps.value.first().running)
        val step = trace.started("x".repeat(7000))
        trace.finished(step, 124, "y".repeat(2000))
        assertTrue(trace.steps.value.last().command.endsWith("\n…"))
        assertTrue(trace.steps.value.last().command.length <= 6002)
        assertTrue(trace.steps.value.last().error!!.length <= 1202)
        assertEquals(124, trace.steps.value.last().exitCode)
    }

    @Test fun traceSurvivesDispatcherChangesAndRestoresTheWorkerThread() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
            val trace = CommandTrace()
            assertNull(CommandTraceContext.current())
            withContext(CommandTraceContext(trace)) {
                assertSame(trace, CommandTraceContext.current())
                withContext(CommandTraceContext(null)) {
                    withContext(dispatcher) { assertNull(CommandTraceContext.current()) }
                }
                assertSame(trace, CommandTraceContext.current())
                withContext(dispatcher) {
                    delay(5)
                    assertSame(trace, CommandTraceContext.current())
                }
                assertSame(trace, CommandTraceContext.current())
            }
            assertNull(CommandTraceContext.current())
            withContext(dispatcher) { assertNull(CommandTraceContext.current()) }
        }
    }

    @Test fun nestedTracesRestoreTheOuterActionAfterFailure() = runBlocking {
        val outer = CommandTrace()
        val inner = CommandTrace()
        withContext(CommandTraceContext(outer)) {
            try {
                withContext(CommandTraceContext(inner)) {
                    assertSame(inner, CommandTraceContext.current())
                    throw IllegalStateException("test interruption")
                }
            } catch (ex: IllegalStateException) {
                assertSame(outer, CommandTraceContext.current())
            }
        }
        assertNull(CommandTraceContext.current())
    }

    @Test fun concurrentActionsAndBackgroundReadsStaySeparate() = runBlocking {
        Executors.newFixedThreadPool(2).asCoroutineDispatcher().use { dispatcher ->
            val a = CommandTrace()
            val b = CommandTrace()
            val first = async(dispatcher + CommandTraceContext(a)) {
                repeat(10) { i ->
                    val step = CommandTraceContext.current()!!.started("a-$i")
                    delay(1)
                    CommandTraceContext.current()!!.finished(step, 0)
                }
            }
            val second = async(dispatcher + CommandTraceContext(b)) {
                repeat(10) { i ->
                    val step = CommandTraceContext.current()!!.started("b-$i")
                    delay(1)
                    CommandTraceContext.current()!!.finished(step, 0)
                }
            }
            val background = async(dispatcher) {
                repeat(10) { delay(1); assertNull(CommandTraceContext.current()) }
            }
            first.await(); second.await(); background.await()
            assertTrue(a.steps.value.all { it.command.startsWith("a-") && !it.running })
            assertTrue(b.steps.value.all { it.command.startsWith("b-") && !it.running })
        }
    }
}
