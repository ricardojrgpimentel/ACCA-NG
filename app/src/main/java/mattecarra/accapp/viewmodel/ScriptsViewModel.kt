package mattecarra.accapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mattecarra.accapp.database.AccaRoomDatabase
import mattecarra.accapp.database.ScriptDao
import mattecarra.accapp.models.AccaScript
import mattecarra.accapp.utils.RootShell

data class ScriptRunState(
    val script: AccaScript,
    val running: Boolean = false,
    val exitCode: Int? = null,
    val output: String = ""
)

class ScriptsViewModel(application: Application) : AndroidViewModel(application)
{
    private val mListLiveData: LiveData<List<AccaScript>>
    private val mScriptDao: ScriptDao
    private val _scriptRunState = MutableLiveData<ScriptRunState?>(null)
    val scriptRunState: LiveData<ScriptRunState?> = _scriptRunState

    init
    {
        val accaDatabase = AccaRoomDatabase.getDatabase(application)
        mScriptDao = accaDatabase.scriptsDao()
        mListLiveData = mScriptDao.getAllScripts()
    }

    fun deleteScript(script: AccaScript) = viewModelScope.launch {
        mScriptDao.delete(script)
    }

    fun updateScript(script: AccaScript) = viewModelScope.launch {
        mScriptDao.update(script)
    }

    fun copyScript(script: AccaScript) = viewModelScope.launch {
        script.uid = 0
        mScriptDao.insert(script)
    }

    fun previewScript(script: AccaScript) {
        if (_scriptRunState.value?.running == true) return
        // Run the exact command reviewed, even if the list refreshes meanwhile.
        _scriptRunState.value = ScriptRunState(script.copy())
    }

    fun closeScriptPreview() {
        if (_scriptRunState.value?.running != true) _scriptRunState.value = null
    }

    fun runPreparedScript() {
        val prepared = _scriptRunState.value ?: return
        if (prepared.running || prepared.exitCode != null || prepared.script.scBody.isBlank()) return
        _scriptRunState.value = prepared.copy(running = true)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    // Quote the entire body so timeout covers multiline scripts,
                    // pipelines and shell builtins as well as single commands.
                    val shellResult = RootShell.execUserScript(prepared.script.scBody)
                    prepared.copy(exitCode = shellResult.code,
                        output = (shellResult.out + shellResult.err).joinToString("\n"))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    prepared.copy(exitCode = -1, output = ex.message ?: ex.javaClass.simpleName)
                }
            }
            _scriptRunState.value = result
            mScriptDao.update(result.script.copy(scExitCode = result.exitCode!!, scOutput = result.output))
        }
    }

    suspend fun getScripts(): List<AccaScript>
    {
        return mScriptDao.getScripts()
    }

    suspend fun getScriptById(id: Int): AccaScript?
    {
        //return if (id>0) mScriptDao.getScriptById(id) else null
        return mScriptDao.getScriptById(id)
    }

    fun getLiveData(): LiveData<List<AccaScript>>
    {
        return mListLiveData
    }
}
