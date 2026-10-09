package mattecarra.accapp.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mattecarra.accapp.R
import mattecarra.accapp.acc.Acc
import mattecarra.accapp.databinding.ActivityAccTroubleshootBinding
import mattecarra.accapp.models.AccHealthIssue
import mattecarra.accapp.utils.AccHealthText
import mattecarra.accapp.utils.ScopedAppActivity
import mattecarra.accapp.viewmodel.AccTroubleshootViewModel
import mattecarra.accapp.viewmodel.AccCommandState

class AccTroubleshootActivity : ScopedAppActivity() {
    private val model: AccTroubleshootViewModel by viewModels()
    private lateinit var binding: ActivityAccTroubleshootBinding

    override fun commandResultMessage(state: AccCommandState): CharSequence? =
        if (state.label == R.string.troubleshoot_calibrate || state.label == R.string.troubleshoot_restore)
            model.resultMessage.value?.let { getString(it) }
        else null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccTroubleshootBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.troubleshootToolbar.setTitle(R.string.troubleshoot_title)
        binding.troubleshootToolbar.setNavigationIcon(R.drawable.ic_arrow_back_24px)
        binding.troubleshootToolbar.navigationIcon?.setTint(MaterialColors.getColor(
            binding.troubleshootToolbar, com.google.android.material.R.attr.colorOnSurface))
        binding.troubleshootToolbar.navigationContentDescription = getString(androidx.appcompat.R.string.abc_action_bar_up_description)
        binding.troubleshootToolbar.setNavigationOnClickListener { finish() }
        binding.troubleshootRefresh.setOnClickListener {
            runAccCommand(R.string.command_read_status) { model.refresh(); model.report.value?.snapshot?.available == true }
        }
        binding.troubleshootCalibrate.setOnClickListener {
            MaterialDialog(this).show {
                title(R.string.troubleshoot_calibrate)
                message(R.string.troubleshoot_calibration_instructions)
                positiveButton(R.string.troubleshoot_calibrate_now) {
                    runAccCommand(R.string.troubleshoot_calibrate) { model.calibrate() }
                }
                negativeButton(android.R.string.cancel)
            }
        }
        binding.troubleshootRestore.setOnClickListener {
            MaterialDialog(this).show {
                title(R.string.troubleshoot_restore)
                message(R.string.troubleshoot_restore_confirmation)
                positiveButton(R.string.troubleshoot_restore) {
                    runAccCommand(R.string.troubleshoot_restore) { model.restore() }
                }
                negativeButton(android.R.string.cancel)
            }
        }
        binding.troubleshootCopy.setOnClickListener {
            val text = listOf(binding.troubleshootSummary.text, binding.troubleshootDetails.text,
                binding.troubleshootResult.text).joinToString("\n\n")
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.troubleshoot_title), text))
            Snackbar.make(binding.root, R.string.troubleshoot_copied, Snackbar.LENGTH_SHORT).show()
        }
        model.report.observe(this) { render() }
        model.working.observe(this) { render() }
        model.resultMessage.observe(this) { render() }
        model.backup.observe(this) { render() }
        accCommands.state.observe(this) { render() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (accCommands.state.value?.running != true && model.working.value != true) model.refresh()
                    delay(5000)
                }
            }
        }
    }

    private fun render() {
        val report = model.report.value
        val s = report?.snapshot
        val busy = model.working.value == true || accCommands.state.value?.running == true
        binding.troubleshootProgress.isVisible = busy || report == null
        binding.troubleshootSummary.text = report?.let {
            if (it.issues.isEmpty()) getString(R.string.troubleshoot_no_issue)
            else it.issues.joinToString("\n\n") { issue -> getString(AccHealthText.message(issue)) }
        } ?: getString(R.string.acc_daemon_status_reading_status)
        val unknown = getString(R.string.battery_data_unavailable)
        binding.troubleshootDetails.text = if (s == null) "" else getString(R.string.troubleshoot_details,
            s.version?.toString() ?: unknown, s.capacity?.toString() ?: unknown,
            s.pause?.toString() ?: unknown, s.resume?.toString() ?: unknown,
            s.status ?: unknown, s.currentMa?.let { "%.0f".format(it) } ?: unknown,
            s.polarity?.takeIf { it in listOf("+", "-") } ?: unknown,
            s.switch?.takeUnless { it == "()" || it.isBlank() } ?: getString(R.string.automatic))
        binding.troubleshootRefresh.isEnabled = !busy
        binding.troubleshootCalibrate.isEnabled = !busy && s?.available == true && s.workaround && s.hasCurrentSensor
        binding.troubleshootRestore.isEnabled = !busy && s?.available == true && s.version == Acc.bundledVersion &&
            report.issues.any { it == AccHealthIssue.FIXED_LIMIT_MODULE || it == AccHealthIssue.DIFFERENT_MODULE }
        binding.troubleshootRestoreHelp.isVisible = s?.version != null && s.version != Acc.bundledVersion
        binding.troubleshootCopy.isEnabled = !busy && report != null
        binding.troubleshootResult.isVisible = model.resultMessage.value != null
        binding.troubleshootResult.text = listOfNotNull(
            model.resultMessage.value?.let { getString(it) },
            model.backup.value?.let { getString(R.string.troubleshoot_backup, it) }).joinToString("\n\n")
    }
}
