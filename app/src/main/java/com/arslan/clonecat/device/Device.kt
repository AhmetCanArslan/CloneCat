package com.arslan.clonecat.device

import com.arslan.clonecat.cmd.CommandLog
import com.arslan.clonecat.cmd.DeviceCommand
import com.arslan.clonecat.shell.ShellResult
import com.arslan.clonecat.shell.ShizukuShell

/** Runs a [DeviceCommand] and records it, so the commands sheet mirrors reality. */
object Device {

    suspend fun run(command: DeviceCommand): ShellResult {
        val result = ShizukuShell.exec(command.argv)
        CommandLog.record(command, result.exitCode)
        return result
    }

    suspend fun runAll(commands: List<DeviceCommand>): List<ShellResult> = commands.map { run(it) }
}
