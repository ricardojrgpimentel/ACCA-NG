package mattecarra.accapp.models

import androidx.annotation.StringRes
import mattecarra.accapp.R

enum class ProfilePreset(@StringRes val nameRes: Int, @StringRes val descriptionRes: Int,
                         val resume: Int, val pause: Int) {
    DAILY(R.string.preset_daily_name, R.string.preset_daily_help, 75, 80),
    PLUGGED(R.string.preset_plugged_name, R.string.preset_plugged_help, 50, 60),
    TRAVEL(R.string.preset_travel_name, R.string.preset_travel_help, 90, 95);

    // Presets only change capacity limits; device-specific settings stay intact.
    fun applyTo(config: AccConfig): AccConfig = config.copy(
        configCapacity = config.configCapacity.copy(resume = resume, pause = pause)
    )

    companion object {
        fun matching(capacity: AccConfig.ConfigCapacity): ProfilePreset? =
            values().firstOrNull { it.resume == capacity.resume && it.pause == capacity.pause }
    }
}
