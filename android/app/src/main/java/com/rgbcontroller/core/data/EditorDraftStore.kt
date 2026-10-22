package com.rgbcontroller.core.data

import android.content.Context
import android.util.AtomicFile
import com.rgbcontroller.core.programs.EditorDraftCodec
import com.rgbcontroller.core.programs.EditorDraftSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Atomic private autosave. Corrupted drafts are NEVER silently replaced. */
class EditorDraftStore(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "compi-studio-drafts-v1.txt"))
    private val mutex = Mutex()

    suspend fun load(): EditorDraftSnapshot? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val stream = try { file.openRead() }
                catch (_: java.io.FileNotFoundException) { return@withLock null }
            val bytes = stream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val size = input.read(buffer)
                    if (size == -1) break
                    if (size == 0) continue
                    require(out.size() + size <= EditorDraftCodec.MAX_BYTES) { "Borradores corruptos o demasiado grandes." }
                    out.write(buffer, 0, size)
                }
                out.toByteArray()
            }
            EditorDraftCodec.decode(
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            )
        }
    }

    suspend fun save(drafts: EditorDraftSnapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val bytes = EditorDraftCodec.encode(drafts).toByteArray(Charsets.UTF_8)
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (failure: Exception) {
                file.failWrite(output)
                throw failure
            }
        }
    }
}
