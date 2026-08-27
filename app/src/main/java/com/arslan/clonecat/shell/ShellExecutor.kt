package com.arslan.clonecat.shell

data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val success: Boolean get() = exitCode == 0

    val output: String get() = stderr.ifBlank { stdout }.trim()
}

interface ShellExecutor {
    suspend fun exec(command: String): ShellResult
}
