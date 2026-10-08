package mattecarra.accapp.utils

import androidx.annotation.StringRes
import mattecarra.accapp.R
import mattecarra.accapp.models.AccHealthIssue

object AccHealthText {
    @StringRes fun message(issue: AccHealthIssue): Int = when (issue) {
        AccHealthIssue.UNAVAILABLE -> R.string.troubleshoot_unavailable
        AccHealthIssue.FIXED_LIMIT_MODULE -> R.string.troubleshoot_fixed_limits
        AccHealthIssue.DIFFERENT_MODULE -> R.string.troubleshoot_different_module
        AccHealthIssue.CALIBRATION_PENDING -> R.string.troubleshoot_calibration_pending
        AccHealthIssue.DAEMON_STOPPED -> R.string.troubleshoot_stopped
        AccHealthIssue.CHARGING_ABOVE_LIMIT -> R.string.troubleshoot_above_limit
    }
}
