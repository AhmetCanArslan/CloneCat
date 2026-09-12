package com.arslan.clonecat.device

import com.arslan.clonecat.cmd.DeviceCommand
import com.arslan.clonecat.shell.ShellResult
import com.arslan.clonecat.shell.ShizukuShell
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object Device {

    suspend fun run(command: DeviceCommand): ShellResult {
        return ShizukuShell.exec(command.argv)
    }

    suspend fun runAll(commands: List<DeviceCommand>): List<ShellResult> = coroutineScope {
        commands.map { async { run(it) } }.awaitAll()
    }
}
