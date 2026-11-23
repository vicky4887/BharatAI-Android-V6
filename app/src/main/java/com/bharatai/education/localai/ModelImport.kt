package com.bharatai.education.localai

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** SAF selected model importer. No storage permission, internet or external server. */
object ModelImport {
    private const val MAX_MODEL_BYTES = 5L * 1024 * 1024 * 1024
    private const val SPACE_RESERVE = 150L * 1024 * 1024

    /** Import to app-private storage so native llama.cpp can mmap a genuine file path. */
    fun importFromUri(context: Context, uri: Uri): File {
        val dir = File(context.filesDir, "gguf_models").apply { mkdirs() }
        val tmp = File(dir, "bharatai-import.partial")
        val dst = File(dir, "BharatAI-V6.gguf")
        var total = 0L
        try {
            val src = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Cannot open selected file")
            src.use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4 || !magic.contentEquals(byteArrayOf(71, 71, 85, 70))) {
                    throw IOException("Not a GGUF file (header mismatch)")
                }
                FileOutputStream(tmp).use { output ->
                    output.write(magic)
                    total = 4
                    val buf = ByteArray(1024 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n == 0) continue
                        total += n
                        if (total > MAX_MODEL_BYTES) throw IOException("GGUF exceeds 5 GiB import limit")
                        if (tmp.usableSpace < SPACE_RESERVE + n) {
                            throw IOException("Insufficient free storage: keep 150 MiB spare")
                        }
                        output.write(buf, 0, n)
                    }
                    output.fd.sync()
                }
            }
            if (total < 1024 * 1024) throw IOException("GGUF file is unexpectedly small")
            if (dst.exists() && !dst.delete()) throw IOException("Cannot replace previous model")
            if (!tmp.renameTo(dst)) throw IOException("Cannot finalize imported model")
            return dst
        } catch (ex: Exception) {
            tmp.delete()
            throw ex
        }
    }

    fun importedModel(context: Context): File? = File(context.filesDir, "gguf_models/BharatAI-V6.gguf")
        .takeIf { it.isFile && it.length() >= 1024 * 1024 }
}
