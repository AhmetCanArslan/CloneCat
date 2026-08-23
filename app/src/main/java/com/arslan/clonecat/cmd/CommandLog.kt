package com.arslan.clonecat.cmd

object CommandLog {

    private const val MAX = 200

    data class Entry(val command: DeviceCommand, val exitCode: Int, val atMillis: Long) {
        val success: Boolean get() = exitCode == 0
    }

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun record(command: DeviceCommand, exitCode: Int) {
        entries.addFirst(Entry(command, exitCode, System.currentTimeMillis()))
        while (entries.size > MAX) entries.removeLast()
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun clear() = entries.clear()

    fun asAdbScript(): String = snapshot().reversed().joinToString("\n") { it.command.asAdb() }
}
