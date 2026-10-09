package mattecarra.accapp.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.getActionButton
import com.afollestad.materialdialogs.actions.setActionButtonEnabled
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.input
import com.google.android.material.floatingactionbutton.FloatingActionButton
import mattecarra.accapp.R
import mattecarra.accapp._interface.OnScriptClickListener
import mattecarra.accapp.adapters.ScriptListAdapter
import mattecarra.accapp.databinding.*
import mattecarra.accapp.models.AccaScript
import mattecarra.accapp.utils.LogExt
import mattecarra.accapp.utils.ScopedFragment
import mattecarra.accapp.viewmodel.ScriptsViewModel
import mattecarra.accapp.viewmodel.ScriptRunState

class ScriptesFragment : ScopedFragment(), OnScriptClickListener
{
    companion object
    {
        fun newInstance() = ScriptesFragment()
    }

    lateinit var mContext: Context
    private lateinit var mScriptsViewModel: ScriptsViewModel
    private lateinit var mScriptesAdapter: ScriptListAdapter
    private var scriptDialog: MaterialDialog? = null
    private var scriptDialogBinding: MdRunScriptBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        return ScriptsFragmentBinding.inflate(inflater, container, false).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        LogExt().d(javaClass.simpleName, "onViewCreated()")

        val binding = ScriptsFragmentBinding.bind(view)

        mContext = requireContext()

        mScriptesAdapter = ScriptListAdapter(mContext)
        mScriptesAdapter.setOnClickListener(this)

        binding.scriptsRecyclerView.adapter = mScriptesAdapter
        binding.scriptsRecyclerView.layoutManager = LinearLayoutManager(mContext)

        mScriptsViewModel = ViewModelProvider(this).get(ScriptsViewModel::class.java)

        // Observe data
        mScriptsViewModel.getLiveData().observe(viewLifecycleOwner, Observer { scripts ->

            if (scripts.isEmpty())
            {
                binding.scriptsEmptyTextview.visibility = View.VISIBLE
                binding.scriptsRecyclerView.visibility = View.GONE
            }
            else
            {
                binding.scriptsEmptyTextview.visibility = View.GONE
                binding.scriptsRecyclerView.visibility = View.VISIBLE
            }
            mScriptesAdapter.setScripts(scripts)
        })

        view.findViewById<FloatingActionButton>(R.id.scripts_addBtn_fab).setOnClickListener{ onAddScript() }

        mScriptsViewModel.scriptRunState.observe(viewLifecycleOwner) { state ->
            if (state == null) dismissScriptDialog() else showScriptDialog(state)
        }
    }

    override fun onScriptClick(script: AccaScript)
    {
        mScriptsViewModel.previewScript(script)
    }

    private fun showScriptDialog(state: ScriptRunState) {
        if (scriptDialog == null) {
            val binding = MdRunScriptBinding.inflate(layoutInflater)
            scriptDialogBinding = binding
            scriptDialog = MaterialDialog(mContext).show {
                noAutoDismiss()
                title(text = state.script.scName)
                customView(view = binding.root, scrollable = true)
                positiveButton(R.string.script_run) { mScriptsViewModel.runPreparedScript() }
                negativeButton(R.string.command_close) {
                    mScriptsViewModel.closeScriptPreview()
                }
                setOnDismissListener {
                    scriptDialog = null
                    scriptDialogBinding = null
                    mScriptsViewModel.closeScriptPreview()
                }
            }
        }
        val dialog = scriptDialog ?: return
        val binding = scriptDialogBinding ?: return
        dialog.title(text = state.script.scName)
        binding.mdDescription.text = state.script.scDescription
        binding.mdDescription.visibility = if (state.script.scDescription.isBlank()) View.GONE else View.VISIBLE
        binding.mdRunContent.text = state.script.scBody
        binding.mdStatusText.text = when {
            state.running -> getString(R.string.script_running)
            state.exitCode == null -> getString(R.string.script_run_preview)
            state.exitCode == 0 -> getString(R.string.script_run_success)
            else -> getString(R.string.script_run_failed, state.exitCode)
        }
        binding.mdStatusPb.visibility = if (state.running) View.VISIBLE else View.GONE
        binding.mdStatusImageView.visibility = if (state.exitCode != null) View.VISIBLE else View.GONE
        binding.mdStatusImageView.setImageResource(if (state.exitCode == 0)
            R.drawable.ic_outline_check_circle_24px else R.drawable.ic_outline_error_outline_24px)
        binding.mdOutputLabel.visibility = if (state.exitCode != null) View.VISIBLE else View.GONE
        binding.mdOutContent.visibility = if (state.exitCode != null) View.VISIBLE else View.GONE
        binding.mdOutContent.text = state.output.ifBlank { getString(R.string.script_no_output) }
        dialog.getActionButton(WhichButton.POSITIVE).visibility =
            if (state.exitCode == null) View.VISIBLE else View.GONE
        dialog.setActionButtonEnabled(WhichButton.POSITIVE, !state.running && state.script.scBody.isNotBlank())
        dialog.setActionButtonEnabled(WhichButton.NEGATIVE, !state.running)
        dialog.cancelable(!state.running)
        dialog.cancelOnTouchOutside(!state.running)
    }

    private fun dismissScriptDialog() {
        // Detaching the view must not clear an execution that survives rotation.
        scriptDialog?.setOnDismissListener(null)
        scriptDialog?.dismiss()
        scriptDialog = null
        scriptDialogBinding = null
    }

    override fun onDestroyView() {
        dismissScriptDialog()
        super.onDestroyView()
    }

    fun onAddScript()
    {
        MaterialDialog(mContext).show {

            noAutoDismiss()
            title(text = getString(R.string.new_script))
            val binding = ScriptNeweditDialogBinding.inflate(layoutInflater)
            customView(view = binding.root)
            var script = AccaScript(0,"","","","",0)

            positiveButton { dialog ->

                if (binding.scriptNameEd.text?.isBlank() != false) {
                    binding.scriptNameEd.requestFocus() ; return@positiveButton }

                script.scName = binding.scriptNameEd.text.toString()
                script.scDescription = binding.scriptDescriptionEd.text.toString()
                script.scBody = binding.scriptBodyTextEd.text.toString()

                mScriptsViewModel.copyScript(script)
                dismiss()
            }

            negativeButton { dismiss() }
        }
    }

    override fun onEditScript(script: AccaScript)
    {
        MaterialDialog(mContext).show {

            noAutoDismiss()
            title(text = script.scName)
            val binding = ScriptNeweditDialogBinding.inflate(layoutInflater)
            customView(view = binding.root)
            binding.scriptNameEd.setText(script.scName)
            binding.scriptDescriptionEd.setText(script.scDescription)
            binding.scriptBodyTextEd.setText(script.scBody)

            positiveButton { dialog ->

                if (binding.scriptNameEd.text?.isBlank() != false) {
                    binding.scriptNameEd.requestFocus() ; return@positiveButton }

                script.scName = binding.scriptNameEd.text.toString()
                script.scDescription = binding.scriptDescriptionEd.text.toString()
                script.scBody = binding.scriptBodyTextEd.text.toString()

                mScriptsViewModel.updateScript(script)
                dismiss()
            }

            negativeButton { dismiss() }
        }
    }

    override fun onRenameScript(script: AccaScript)
    {
        // Rename the selected script
        MaterialDialog(mContext).show {
            title(R.string.script_name)
            message(R.string.dialog_script_name_message)
            input(prefill = script.scName) { _, text ->
                script.scName = text.toString()
                mScriptsViewModel.updateScript(script)
            }
            positiveButton(R.string.save)
            negativeButton(android.R.string.cancel)
        }
    }

    override fun onCopyScript(script: AccaScript)
    {
        MaterialDialog(mContext).show {

            noAutoDismiss()
            title(text = getString(R.string.menu_option_copy))
            val binding = ScriptNeweditDialogBinding.inflate(layoutInflater)
            customView(view = binding.root)
            binding.scriptNameEd.setText(script.scName)
            binding.scriptDescriptionEd.setText(script.scDescription)
            binding.scriptBodyTextEd.setText(script.scBody)

            positiveButton { dialog ->

                if (binding.scriptNameEd.text?.isBlank() != false) {
                    binding.scriptNameEd.requestFocus() ; return@positiveButton }

                script.scName = binding.scriptNameEd.text.toString()
                script.scDescription = binding.scriptDescriptionEd.text.toString()
                script.scBody = binding.scriptBodyTextEd.text.toString()

                mScriptsViewModel.copyScript(script)
                dismiss()
            }

            negativeButton { dismiss() }
        }
    }

    override fun onDeleteScript(script: AccaScript)
    {
        mScriptsViewModel.deleteScript(script)
        Toast.makeText(mContext, "deleteScript:\n"+script.scName, Toast.LENGTH_SHORT).show()
    }

}
