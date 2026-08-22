package com.arslan.clonecat.cmd

/** A single shell command plus a human label; `argv` is exactly what the app runs. */
data class DeviceCommand(val argv: String, val label: String) {
    fun asAdb(): String = "adb shell $argv"
}

/**
 * The only place command strings are built. Every repository call goes through it, so the UI can
 * show the exact `adb shell …` equivalent of what it just ran.
 */
object AdbCommandBuilder {

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
        "cmd lock_settings verify --user $userId --old $credential",
        "Unlock user $userId"
    )
}
