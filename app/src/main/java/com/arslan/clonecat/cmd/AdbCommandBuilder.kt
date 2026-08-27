package com.arslan.clonecat.cmd

data class DeviceCommand(val argv: String, val label: String)

object AdbCommandBuilder {

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    fun listUsers() = DeviceCommand("pm list users", "List users")

    fun dumpUsers() = DeviceCommand("dumpsys user", "Dump user types")

    fun listThirdParty(userId: Int) =
        DeviceCommand("pm list packages -3 -U --user $userId", "List 3rd-party apps of user $userId")

    fun listSystem(userId: Int) =
        DeviceCommand("pm list packages -s -U --user $userId", "List system apps of user $userId")

    fun dumpsysPackages() = DeviceCommand(
        "dumpsys package packages | grep -oE " +
            "'Package \\[[^]]+]|appId=[0-9]+|userId=[0-9]+|pkgFlags=\\[[^]]*]|" +
            "privateFlags=\\[[^]]*]|User [0-9]+:|installed=[a-z]+|Shared users:'",
        "Scan packages via dumpsys"
    )

    fun installExisting(userId: Int, pkg: String) =
        DeviceCommand("pm install-existing --user $userId $pkg", "Clone $pkg into user $userId")

    fun uninstall(userId: Int, pkg: String) =
        DeviceCommand("pm uninstall --user $userId $pkg", "Remove $pkg from user $userId")

    fun installCreate(userId: Int, pkg: String) = DeviceCommand(
        "pm install-create --user $userId -r -t -d -i com.android.vending",
        "Create install session for $pkg in user $userId"
    )

    fun installWrite(sessionId: String, splitName: String, path: String) = DeviceCommand(
        "pm install-write $sessionId $splitName $path",
        "Write $splitName into session $sessionId"
    )

    fun installCommit(sessionId: String) =
        DeviceCommand("pm install-commit $sessionId", "Commit session $sessionId")

    fun installAbandon(sessionId: String) =
        DeviceCommand("pm install-abandon $sessionId", "Abandon session $sessionId")

    fun installerOf(pkg: String) =
        DeviceCommand("pm list packages -i --user 0 $pkg", "Installer of $pkg")

    fun apkPath(userId: Int, pkg: String) =
        DeviceCommand("pm path --user $userId $pkg", "APK path of $pkg")

    fun queryLauncherActivities(userId: Int, pkg: String) = DeviceCommand(
        "cmd package query-activities --user $userId --brief " +
            "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $pkg",
        "Resolve launcher activity of $pkg"
    )

    fun resolveLauncherActivity(userId: Int, pkg: String) = DeviceCommand(
        "cmd package resolve-activity --user $userId --brief " +
            "-a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $pkg",
        "Resolve default launcher activity of $pkg"
    )

    fun startActivity(userId: Int, component: String) =
        DeviceCommand("am start --user $userId -n $component", "Launch $component in user $userId")

    fun startUser(userId: Int) = DeviceCommand("am start-user -w $userId", "Start user $userId")

    fun stopUser(userId: Int) = DeviceCommand("am stop-user -f $userId", "Stop user $userId")

    fun switchUser(userId: Int) = DeviceCommand("am switch-user $userId", "Switch to user $userId")

    fun unlockUser(userId: Int, credential: String) = DeviceCommand(
        "cmd lock_settings verify --user $userId --old ${quote(credential)}",
        "Unlock user $userId"
    )
}
