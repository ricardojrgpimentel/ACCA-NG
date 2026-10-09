package mattecarra.accapp.activities

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mattecarra.accapp.BuildConfig
import mattecarra.accapp.R
import mattecarra.accapp.acc.Acc
import mattecarra.accapp.databinding.ActivityAboutBinding
import mattecarra.accapp.utils.RootShell

class AboutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAboutBinding
    private var versionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.aboutToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        listOf(binding.aboutBrand, binding.aboutMaintainedBy, binding.aboutOrigin).forEach {
            ViewCompat.setAccessibilityHeading(it, true)
        }

        val buildType = getString(if (BuildConfig.DEBUG) R.string.about_ng_debug else R.string.about_ng_release)
        binding.aboutBuildSummary.text = getString(R.string.about_ng_build_summary, BuildConfig.VERSION_NAME, buildType)
        binding.aboutAccaVersionTv.text = getString(R.string.about_ng_version_detail, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        binding.aboutMaintainerProfile.setOnClickListener { openUrl("https://github.com/ricardojrgpimentel") }
        binding.aboutProject.setOnClickListener { openUrl(PROJECT_URL) }
        binding.aboutIssues.setOnClickListener { openUrl("$PROJECT_URL/issues") }
        binding.aboutMatteProfile.setOnClickListener { openUrl("https://github.com/MatteCarra") }
        binding.aboutSquabbiProfile.setOnClickListener { openUrl("https://github.com/squabbi") }
        binding.aboutVr25Profile.setOnClickListener { openUrl("https://github.com/VR-25") }
        binding.aboutOriginalProject.setOnClickListener { openUrl("https://github.com/MatteCarra/AccA") }
        binding.aboutCommunity.setOnClickListener { openUrl("https://t.me/acc_group") }
        binding.aboutLicense.setOnClickListener { openUrl("$PROJECT_URL/blob/main/LICENSE") }

        bindDisclosure(binding.aboutTechnicalToggle, binding.aboutTechnicalContent,
            savedInstanceState?.getBoolean(TECHNICAL_EXPANDED) ?: false) { loadAccVersions() }
        bindDisclosure(binding.aboutLicensesToggle, binding.aboutLicensesContent,
            savedInstanceState?.getBoolean(LICENSES_EXPANDED) ?: false)
    }

    private fun bindDisclosure(button: MaterialButton, content: View, expanded: Boolean, onExpand: () -> Unit = {}) {
        fun render(isExpanded: Boolean) {
            button.isChecked = isExpanded
            content.isVisible = isExpanded
            button.setIconResource(if (isExpanded) R.drawable.ic_baseline_arrow_drop_up_24px else R.drawable.ic_baseline_arrow_drop_down_24px)
            ViewCompat.setStateDescription(button, getString(if (isExpanded) R.string.about_ng_expanded else R.string.about_ng_collapsed))
            if (isExpanded) onExpand()
        }
        render(expanded)
        // MaterialButton toggles its checked state before invoking the click listener.
        button.setOnClickListener {
            TransitionManager.beginDelayedTransition(binding.aboutContent, AutoTransition().setDuration(160))
            render(button.isChecked)
        }
    }

    private fun loadAccVersions() {
        if (versionJob != null) return
        versionJob = lifecycleScope.launch {
            val versions = withContext(Dispatchers.IO) {
                try {
                    val result = RootShell.exec("/dev/acca --version", timeoutSecs = 5)
                    val daemon = result.out.joinToString("\n").trim().takeIf { result.isSuccess && it.isNotBlank() }
                    // Avoid initializing ACC when it is unavailable. About remains usable without root.
                    daemon to daemon?.let { Acc.instance.version.toString() }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    null to null
                }
            }
            val unavailable = getString(R.string.about_ng_unavailable)
            binding.aboutAccDaemonVersionTv.text = versions.first ?: unavailable
            binding.aboutAccApiVersionTv.text = versions.second ?: unavailable
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(TECHNICAL_EXPANDED, binding.aboutTechnicalToggle.isChecked)
        outState.putBoolean(LICENSES_EXPANDED, binding.aboutLicensesToggle.isChecked)
        super.onSaveInstanceState(outState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openUrl(url: String) {
        try {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(this, R.string.toast_no_browser_installed, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val PROJECT_URL = "https://github.com/ricardojrgpimentel/ACCA-NG"
        private const val TECHNICAL_EXPANDED = "about.technical.expanded"
        private const val LICENSES_EXPANDED = "about.licenses.expanded"

        fun launch(context: Context) {
            context.startActivity(Intent(context, AboutActivity::class.java))
        }
    }
}
