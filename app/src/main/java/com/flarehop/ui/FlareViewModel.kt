package com.flarehop.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flarehop.flare.Flare
import com.flarehop.flare.FlareEngine
import com.flarehop.flare.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FlareViewModel(application: Application) : AndroidViewModel(application) {
    // DEVELOPMENT (MainScreen.kt) picks the engine: sample data, or Aayush's real Bluetooth engine.
    val engine: FlareEngine = if (DEVELOPMENT) FakeEngine(viewModelScope) else Flare.engine

    private val roleStore = RoleStore(application)

    private val _role = MutableStateFlow(roleStore.load())
    val role: StateFlow<Role> = _role.asStateFlow()

    init {
        engine.setRole(_role.value)
    }

    fun selectRole(role: Role) {
        if (role == _role.value) return
        roleStore.save(role)
        engine.setRole(role)
        _role.value = role
    }
}
