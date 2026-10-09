package mattecarra.accapp.utils

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.google.android.material.snackbar.Snackbar
import mattecarra.accapp.R
import mattecarra.accapp.databinding.AccCommandDialogBinding
import mattecarra.accapp.viewmodel.AccCommandState
import mattecarra.accapp.viewmodel.AccCommandViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.coroutines.CoroutineContext

abstract class ScopedAppActivity: AppCompatActivity(), CoroutineScope {
    val accCommands: AccCommandViewModel by lazy {
        ViewModelProvider(this).get(AccCommandViewModel::class.java)
    }
    private var commandDialog: MaterialDialog? = null
    private var commandDialogBinding: AccCommandDialogBinding? = null
    protected lateinit var job: Job
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        job = Job()
        accCommands.state.observe(this) { state ->
            if (state.running || state.reviewResult) {
                showCommandDialog(state)
            } else {
                dismissCommandDialog()
            }
            state.successful?.let { successful ->
                if (accCommands.claimCompletion()) {
                    onAccCommandCompleted(successful)
                    if (!state.reviewResult) {
                        Snackbar.make(window.decorView, R.string.command_completed, Snackbar.LENGTH_LONG)
                            .setDuration(10000)
                            .setAction(R.string.command_review_details) { accCommands.reviewLastResult() }
                            .show()
                        accCommands.consumeResult()
                    }
                }
            }
        }
    }

    private fun showCommandDialog(state: AccCommandState) {
        if (commandDialog == null) {
            val dialogBinding = AccCommandDialogBinding.inflate(layoutInflater)
            commandDialogBinding = dialogBinding
            dialogBinding.commandDetailsToggle.setOnClickListener { accCommands.toggleDetails() }
            dialogBinding.commandClose.setOnClickListener { accCommands.consumeResult() }
            dialogBinding.commandRetry.setOnClickListener { accCommands.retry() }
            commandDialog = MaterialDialog(this).show {
                customView(view = dialogBinding.root, noVerticalPadding = true)
                cancelable(false)
                cancelOnTouchOutside(false)
            }
        }
        val ui = commandDialogBinding ?: return
        commandDialog?.title(if (state.running) state.label else if (state.successful == true)
            R.string.command_completed else R.string.command_failed_title)
        ui.commandProgress.isVisible = state.running
        ui.commandMessage.text = if (!state.running) commandResultMessage(state) ?: getString(
            if (state.successful == true) R.string.command_result_ready else R.string.command_failed_message)
            else getString(R.string.command_executing)
        ui.commandMessage.setPaddingRelative(if (state.running) (16 * resources.displayMetrics.density).toInt() else 0,
            0, 0, 0)
        ui.commandDetailsToggle.setText(if (state.detailsExpanded) R.string.command_hide_details else R.string.command_show_details)
        ui.commandDetailsContainer.isVisible = state.detailsExpanded
        ui.commandResultActions.isVisible = !state.running
        ui.commandRetry.isVisible = state.successful == false

        val followLatest = ui.commandDetailsScroll.scrollY + ui.commandDetailsScroll.height >=
            ui.commandDetailsText.height - (24 * resources.displayMetrics.density).toInt()
        val details = if (state.steps.isEmpty()) getString(if (state.running)
            R.string.command_trace_waiting else R.string.command_trace_empty) else buildString {
            state.steps.forEach { step ->
                append(when {
                    step.running -> getString(R.string.command_trace_running, step.number)
                    step.exitCode != null -> getString(R.string.command_trace_finished, step.number, step.exitCode)
                    else -> getString(R.string.command_trace_interrupted, step.number)
                })
                append('\n').append(step.command)
                step.error?.let { append('\n').append(getString(R.string.command_trace_output, it)) }
                append("\n\n")
            }
        }.trimEnd()
        if (ui.commandDetailsText.text.toString() != details) {
            ui.commandDetailsText.text = details
            if (state.running && state.detailsExpanded && followLatest) ui.commandDetailsScroll.post {
                ui.commandDetailsScroll.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    protected open fun commandResultMessage(state: AccCommandState): CharSequence? = null

    private fun dismissCommandDialog() {
        commandDialog?.dismiss()
        commandDialog = null
        commandDialogBinding = null
    }

    open fun runAccCommand(@StringRes label: Int, operation: suspend () -> Boolean) {
        accCommands.execute(label, operation)
    }

    protected open fun onAccCommandCompleted(successful: Boolean) {}

    override fun onDestroy() {
        dismissCommandDialog()
        super.onDestroy()
        job.cancel()
    }
}
