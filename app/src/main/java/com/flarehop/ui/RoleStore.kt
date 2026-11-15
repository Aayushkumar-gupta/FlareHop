package com.flarehop.ui

import android.content.Context
import androidx.core.content.edit
import com.flarehop.flare.Role

class RoleStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Role {
        val saved = prefs.getString(KEY_ROLE, null) ?: return Role.SURVIVOR
        // A value from an older build may no longer be a valid role; fall back instead of crashing.
        return runCatching { Role.valueOf(saved) }.getOrDefault(Role.SURVIVOR)
    }

    fun save(role: Role) {
        prefs.edit { putString(KEY_ROLE, role.name) }
    }

    private companion object {
        const val PREFS_NAME = "flarehop"
        const val KEY_ROLE = "role"
    }
}
