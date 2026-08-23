package com.arslan.clonecat.device

import com.arslan.clonecat.cmd.AdbCommandBuilder

enum class UserType { PRIMARY, SECONDARY, MANAGED, CLONE, PRIVATE, OTHER;

    val isProfile: Boolean get() = this == MANAGED || this == CLONE || this == PRIVATE

    val badge: String
        get() = when (this) {
            PRIMARY -> "P"
            SECONDARY -> "S"
            MANAGED -> "W"
            CLONE -> "C"
            PRIVATE -> "🔒"
            OTHER -> "?"
        }
}

data class DeviceUser(
    val id: Int,
    val name: String,
    val type: UserType,
    val running: Boolean,
    val unlocked: Boolean
) {
    val label: String get() = name.ifBlank { "User $id" }
}

object UserRepository {

    private val USER_LINE = Regex("""UserInfo\{(\d+):([^:]*):""")
    private val TYPE_LINE = Regex("""Type:\s*(\S+)""")
    private val STATE_LINE = Regex("""State:\s*(\S+)""")
    private val UNLOCK_TIME_LINE = Regex("""Unlock time:\s*(.+)""")

    suspend fun listUsers(): List<DeviceUser> {
        val list = Device.run(AdbCommandBuilder.listUsers())
        if (!list.success) return emptyList()

        val running = mutableSetOf<Int>()
        val names = LinkedHashMap<Int, String>()
        list.stdout.lineSequence().forEach { line ->
            val match = USER_LINE.find(line) ?: return@forEach
            val id = match.groupValues[1].toIntOrNull() ?: return@forEach
            names[id] = match.groupValues[2].trim().takeUnless { it == "null" }.orEmpty()
            if (line.contains("running", ignoreCase = true)) running.add(id)
        }
        if (names.isEmpty()) return emptyList()

        val types = HashMap<Int, UserType>()
        val unlocked = mutableSetOf<Int>()
        val dump = Device.run(AdbCommandBuilder.dumpUsers())
        if (dump.success) parseUserDump(dump.stdout, types, running, unlocked)

        return names.map { (id, name) ->
            DeviceUser(
                id = id,
                name = name,
                type = types[id] ?: if (id == 0) UserType.PRIMARY else UserType.OTHER,
                running = id in running,
                unlocked = id in unlocked
            )
        }
    }

    internal fun parseUserDump(
        output: String,
        types: MutableMap<Int, UserType>,
        running: MutableSet<Int>,
        unlocked: MutableSet<Int>
    ) {
        var current = -1
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            USER_LINE.find(line)?.let { match ->
                current = match.groupValues[1].toIntOrNull() ?: -1
                return@forEach
            }
            if (current < 0) return@forEach
            TYPE_LINE.matchEntire(line)?.let { match ->
                types[current] = typeOf(match.groupValues[1])
                return@forEach
            }
            STATE_LINE.matchEntire(line)?.let { match ->
                val state = match.groupValues[1]
                if (state.startsWith("RUNNING")) running.add(current)
                if (state.contains("UNLOCK")) unlocked.add(current)
                return@forEach
            }
            UNLOCK_TIME_LINE.matchEntire(line)?.let { match ->
                if (match.groupValues[1].trim() != "<unknown>") unlocked.add(current)
            }
        }
    }

    private fun typeOf(raw: String): UserType = when {
        raw.endsWith("full.SYSTEM") -> UserType.PRIMARY
        raw.endsWith("full.SECONDARY") -> UserType.SECONDARY
        raw.endsWith("profile.MANAGED") -> UserType.MANAGED
        raw.endsWith("profile.CLONE") -> UserType.CLONE
        raw.endsWith("profile.PRIVATE") -> UserType.PRIVATE
        else -> UserType.OTHER
    }

    suspend fun startUser(id: Int) = Device.run(AdbCommandBuilder.startUser(id))

    suspend fun stopUser(id: Int) = Device.run(AdbCommandBuilder.stopUser(id))

    suspend fun switchUser(id: Int) = Device.run(AdbCommandBuilder.switchUser(id))

    suspend fun unlock(id: Int, credential: String) =
        Device.run(AdbCommandBuilder.unlockUser(id, credential))
}
