package com.arslan.clonecat.shell

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

/**
 * Runs commands through the Shizuku privileged process. Ported from ShizuWall's
 * ShizukuShellExecutor; batches are small here, so execBatch is a sequential loop.
 */
object ShizukuShell : ShellExecutor {

    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }
    }

    override suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = newProcess.invoke(
                null,
                arrayOf("/system/bin/sh", "-c", command),
                null,
                null
            ) as? ShizukuRemoteProcess ?: return@withContext ShellResult(-1, "", "no-process")

            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            process.outputStream.close()
            val exitCode = process.waitFor()
            process.destroy()
            ShellResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "")
        }
    }
}
