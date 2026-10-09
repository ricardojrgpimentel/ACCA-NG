package mattecarra.accapp.acc

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModuleInstallerTest {
    private fun select(module: String, sources: Map<String, String>, missingInstaller: Boolean = false): Pair<Int, String> {
        val root = Files.createTempDirectory("module-selection-").toFile()
        try {
            sources.forEach { (name, properties) ->
                val dir = root.resolve(name).apply { mkdirs() }
                dir.resolve("module.prop").writeText(properties)
                if (!missingInstaller) dir.resolve("install.sh").writeText("exit 0\n")
            }
            val installer = File("src/main/res/raw/install").readText()
            val function = "select_source() {" + installer.substringAfter("select_source() {").substringBefore("\nsrc_dir=")
            val process = ProcessBuilder("sh", "-c", "$function\nselect_source $module")
                .directory(root).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            return process.waitFor() to output
        } finally { root.deleteRecursively() }
    }

    @Test fun djsCleanupDoesNotRemoveAccSources() {
        val root = Files.createTempDirectory("module-cleanup-").toFile()
        try {
            for (name in listOf("djs-2021", "acc-ng-v1", "ACC-NG-v1")) root.resolve(name).mkdir()
            val installer = File("src/main/res/raw/install").readText()
            val function = "copy_log() {" + installer.substringAfter("copy_log() {").substringBefore("\ntrap copy_log EXIT")
            val process = ProcessBuilder("sh", "-c", "module_id=djs\n$function\ncopy_log")
                .directory(root).redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor())
            assertEquals(false, root.resolve("djs-2021").exists())
            assertEquals(true, root.resolve("acc-ng-v1").exists())
            assertEquals(true, root.resolve("ACC-NG-v1").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun bundledDjsInstallsWithoutNgMarkerEvenWhenAccIsPresent() {
        assertEquals(0 to "djs-2021.12.14", select("djs", mapOf(
            "djs-2021.12.14" to "id=djs\n", "acc-ng-v1" to "id=acc\nngApiVersion=1\n")))
    }
    @Test fun bundledAndGithubNgArchivesAreAccepted() {
        for (name in listOf("acc-ng-v1", "ACC-NG-v1")) {
            assertEquals(0 to name, select("acc", mapOf(name to "id=acc\nngApiVersion=1\n")))
        }
    }
    @Test fun upstreamAccCannotReplaceNg() {
        assertNotEquals(0, select("acc", mapOf("acc-2023" to "id=acc\n")).first)
    }
    @Test fun moduleIdentityMustMatchDirectoryAndRequest() {
        assertNotEquals(0, select("djs", mapOf("djs-2021" to "id=acc\nngApiVersion=1\n")).first)
        assertNotEquals(0, select("acc", mapOf("djs-2021" to "id=acc\nngApiVersion=1\n")).first)
    }
    @Test fun ambiguousArchivesAreRejected() {
        assertNotEquals(0, select("djs", mapOf("djs-one" to "id=djs\n", "djs-two" to "id=djs\n")).first)
    }
    @Test fun missingInstallScriptIsRejected() {
        assertNotEquals(0, select("djs", mapOf("djs-2021" to "id=djs\n"), true).first)
    }
}
