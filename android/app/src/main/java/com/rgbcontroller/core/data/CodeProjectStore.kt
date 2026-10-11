package com.rgbcontroller.core.data

import android.content.Context
import android.util.AtomicFile
import com.rgbcontroller.core.programs.CodeProject
import com.rgbcontroller.core.programs.CodeProjectLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Application-private, transactional project storage. Never saves the ESP32 control key.
 * A corrupt/unsupported file is surfaced as an error, not overwritten with an empty list.
 */
class CodeProjectStore(context: Context) {
    private val atomicFile = AtomicFile(File(context.applicationContext.filesDir, "code-projects-v1.txt"))
    private val mutex = Mutex()

    suspend fun list(): List<CodeProject> = withContext(Dispatchers.IO) {
        mutex.withLock { readLocked() }
    }

    suspend fun save(project: CodeProject): List<CodeProject> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = CodeProjectLibrary.upsert(readLocked(), project)
            writeLocked(updated)
            updated
        }
    }

    suspend fun delete(name: String): List<CodeProject> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val updated = CodeProjectLibrary.remove(readLocked(), name)
            writeLocked(updated)
            updated
        }
    }

    private fun readLocked(): List<CodeProject> {
        if (!atomicFile.baseFile.exists()) return emptyList()
        val bytes = ByteArrayOutputStream()
        atomicFile.openRead().use { stream ->
            val buffer = ByteArray(4096)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                if (n == 0) continue
                require(bytes.size() + n <= CodeProjectLibrary.MAX_STORAGE_BYTES) {
                    "Biblioteca de proyectos demasiado grande."
                }
                bytes.write(buffer, 0, n)
            }
        }
        val content = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        return CodeProjectLibrary.decode(content)
    }

    private fun writeLocked(projects: List<CodeProject>) {
        val content = CodeProjectLibrary.encode(projects).toByteArray(Charsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(content)
            atomicFile.finishWrite(output)
        } catch (failure: Exception) {
            atomicFile.failWrite(output)
            throw failure
        }
    }
}
