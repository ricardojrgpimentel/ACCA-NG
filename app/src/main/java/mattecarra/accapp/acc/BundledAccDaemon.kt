package mattecarra.accapp.acc

import android.content.Context
import mattecarra.accapp.R
import java.io.DataInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/** Read the daemon from the existing bundle so recovery and its fingerprint
 * cannot silently drift away from the version shipped by the app. */
object BundledAccDaemon {
    @Volatile private var cached: ByteArray? = null

    @Synchronized fun bytes(context: Context): ByteArray {
        cached?.let { return it }
        DataInputStream(GZIPInputStream(context.resources.openRawResource(R.raw.acc_bundle))).use { input ->
            val header = ByteArray(512)
            while (true) {
                input.readFully(header)
                if (header.all { it == 0.toByte() }) break
                val name = header.copyOfRange(0, 100).toString(Charsets.UTF_8).substringBefore('\u0000')
                val size = header.copyOfRange(124, 136).toString(Charsets.US_ASCII)
                    .trim('\u0000', ' ').toLongOrNull(8) ?: throw IOException("Invalid ACC bundle")
                if (name.endsWith("/install/accd.sh") && header[156].toInt() in listOf(0, 48)) {
                    if (size !in 1..1_048_576) throw IOException("Invalid ACC daemon size")
                    return ByteArray(size.toInt()).also { input.readFully(it); cached = it }
                }
                var remaining = ((size + 511) / 512) * 512
                while (remaining > 0) {
                    val skipped = input.skip(remaining)
                    if (skipped == 0L) { input.readByte(); remaining-- } else remaining -= skipped
                }
            }
        }
        throw IOException("ACC daemon missing from bundle")
    }

    fun hash(context: Context): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes(context)).joinToString("") { "%02x".format(it.toInt() and 255) }
}
