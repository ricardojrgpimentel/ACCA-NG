package mattecarra.accapp.utils

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mattecarra.accapp.acc.AccNg
import java.net.URL

object GithubUtils {
    private fun read(url: String): String = URL(url).openConnection().apply {
        connectTimeout = 5000
        readTimeout = 5000
    }.getInputStream().bufferedReader().use { it.readText() }

    suspend fun getLatestAccCommit(branch: String = "main"): String? = withContext(Dispatchers.IO) {
        try {
            JsonParser.parseString(read("https://api.github.com/repos/${AccNg.repository}/commits/$branch"))
                .asJsonObject.get("sha").asString
        } catch (ignored: Exception) { null }
    }

    suspend fun listAccVersions(): List<String> = withContext(Dispatchers.IO) {
        val tags = try {
            JsonParser.parseString(read("https://api.github.com/repos/${AccNg.repository}/tags")).asJsonArray
        } catch (ignored: Exception) { JsonArray() }
        tags.map { it.asJsonObject["name"].asString }.filter {
            it.matches(Regex("^v[0-9]+\\.[0-9]+\\.[0-9]+-ng(?:[.-][A-Za-z0-9]+)*$"))
        }
    }
}
