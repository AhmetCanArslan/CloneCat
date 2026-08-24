package com.arslan.clonecat.ui

import android.content.Context

object UiPrefs {

    private fun prefs(context: Context) =
        context.getSharedPreferences("clonecat_ui", Context.MODE_PRIVATE)

    fun showSystem(context: Context): Boolean = prefs(context).getBoolean("show_system", false)

    fun setShowSystem(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("show_system", value).apply()
    }
}
