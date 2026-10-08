package mattecarra.accapp.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mattecarra.accapp.utils.CommandTrace
import mattecarra.accapp.utils.CommandTraceContext
import mattecarra.accapp.utils.CommandTraceStep

data class AccCommandState(
    @StringRes val label: Int = 0,
    val running: Boolean = false,
    val successful: Boolean? = null,
    val steps: List<CommandTraceStep> = emptyList(),
    val detailsExpanded: Boolean = false,
    val reviewResult: Boolean = false
)

class AccCommandViewModel : ViewModel() {
    private val mutableState = MutableLiveData(AccCommandState())
    val state: LiveData<AccCommandState> = mutableState
    private var lastOperation: (suspend () -> Boolean)? = null
    private var lastLabel = 0
    private var lastResult: AccCommandState? = null
    private var completionHandled = false

    fun execute(@StringRes label: Int, operation: suspend () -> Boolean) {
        if (state.value?.running == true) return
        val expanded = state.value?.detailsExpanded == true
        lastOperation = operation
        lastLabel = label
        lastResult = null
        completionHandled = false
        mutableState.value = AccCommandState(label, running = true, detailsExpanded = expanded)
        viewModelScope.launch {
            val trace = CommandTrace()
            val updates = launch {
                trace.steps.collect { steps ->
                    mutableState.value?.takeIf { it.running }?.let {
                        mutableState.value = it.copy(steps = steps)
                    }
                }
            }
            val successful = try {
                withContext(CommandTraceContext(trace)) { operation() }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                false
            } finally {
                updates.cancel()
            }
            val finalExpanded = state.value?.detailsExpanded == true
            val result = AccCommandState(label, successful = successful, steps = trace.steps.value,
                detailsExpanded = finalExpanded, reviewResult = !successful || finalExpanded)
            lastResult = result
            mutableState.value = result
        }
    }

    fun toggleDetails() {
        mutableState.value?.let { mutableState.value = it.copy(detailsExpanded = !it.detailsExpanded) }
    }

    fun reviewLastResult() {
        if (state.value?.running == true) return
        lastResult?.let { mutableState.value = it.copy(detailsExpanded = true, reviewResult = true) }
    }

    /** UI refresh and completion feedback are delivered once, including after recreation. */
    fun claimCompletion(): Boolean {
        if (state.value?.successful == null || completionHandled) return false
        completionHandled = true
        return true
    }

    fun retry() { lastOperation?.let { execute(lastLabel, it) } }

    fun consumeResult() {
        if (state.value?.running != true) {
            if (state.value?.successful == true) lastOperation = null
            mutableState.value = AccCommandState()
        }
    }
}
