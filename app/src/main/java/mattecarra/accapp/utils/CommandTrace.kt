package mattecarra.accapp.utils

import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

data class CommandTraceStep(
    val number: Int,
    val command: String,
    val running: Boolean = true,
    val exitCode: Int? = null,
    val error: String? = null
)

/** In-memory trace of one user action; never includes unrelated root reads. */
class CommandTrace {
    private val mutableSteps = MutableStateFlow<List<CommandTraceStep>>(emptyList())
    val steps: StateFlow<List<CommandTraceStep>> = mutableSteps
    private var nextNumber = 1

    @Synchronized
    fun started(command: String): Int {
        val number = nextNumber++
        mutableSteps.value = (mutableSteps.value + CommandTraceStep(number, bounded(command, 6000)))
            .takeLast(64)
        return number
    }

    @Synchronized
    fun finished(number: Int, exitCode: Int?, error: String? = null) {
        mutableSteps.value = mutableSteps.value.map { step ->
            if (step.number != number) step else step.copy(running = false, exitCode = exitCode,
                error = error?.takeIf { it.isNotBlank() }?.let { bounded(it, 1200) })
        }
    }

    private fun bounded(value: String, length: Int): String =
        if (value.length <= length) value else value.take(length) + "\n…"
}

/** Propagates the trace across coroutine dispatchers, restoring worker threads afterward. */
class CommandTraceContext(private val trace: CommandTrace?) :
    ThreadContextElement<CommandTrace?>, AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CommandTraceContext> {
        private val active = ThreadLocal<CommandTrace?>()
        fun current(): CommandTrace? = active.get()
    }

    override fun updateThreadContext(context: CoroutineContext): CommandTrace? =
        active.get().also { active.set(trace) }

    override fun restoreThreadContext(context: CoroutineContext, oldState: CommandTrace?) {
        if (oldState == null) active.remove() else active.set(oldState)
    }
}
