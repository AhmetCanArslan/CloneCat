package com.arslan.clonecat.device

import android.content.Context
import com.arslan.clonecat.cmd.AdbCommandBuilder

object FreezeRepository {

    private const val PREFS = "clonecat_freeze"

    suspend fun frozen(userId: Int): Set<String> =
        Device.run(AdbCommandBuilder.listFrozen(userId)).stdout.lineSequence()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .toSet()

    suspend fun apply(userId: Int, packages: Collection<String>, freeze: Boolean): List<String> =
        packages.filter { pkg ->
            val command = if (freeze) AdbCommandBuilder.freeze(userId, pkg) else AdbCommandBuilder.unfreeze(userId, pkg)
            val result = Device.run(command)
            !result.success || !result.output.contains("new state")
        }

    fun list(context: Context, userId: Int): Set<String> =
        prefs(context).getStringSet("u$userId", emptySet()).orEmpty()

    fun save(context: Context, userId: Int, packages: Set<String>) {
        prefs(context).edit().putStringSet("u$userId", packages).apply()
    }

    fun parse(raw: String): Set<String> =
        raw.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
