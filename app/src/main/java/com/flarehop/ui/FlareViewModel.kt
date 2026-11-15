package com.flarehop.ui

import android.app.Application
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flarehop.flare.Flare
import com.flarehop.flare.FlareEngine
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FlareViewModel(application: Application) : AndroidViewModel(application) {
    // DEVELOPMENT (MainScreen.kt) picks the engine: sample data, or Aayush's real Bluetooth engine.
    val engine: FlareEngine = if (DEVELOPMENT) FakeEngine(viewModelScope) else Flare.engine

    val locationReader = LocationReader(application, viewModelScope)

    private val roleStore = RoleStore(application)

    private val _role = MutableStateFlow(roleStore.load())
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _tab = MutableStateFlow(_role.value)
    val tab: StateFlow<Role> = _tab.asStateFlow()

    init {
        engine.setRole(_role.value)
    }

    // A rescuer phone sends a receipt for every SOS it hears (Contract 3, rule 2), which tells the
    // victim help is coming. Opening the tab alone must not do that, so the user confirms first.
    fun openTab(tab: Role) {
        _tab.value = tab
        if (tab != Role.RESCUER) selectRole(tab)
    }

    fun becomeRescuer() {
        selectRole(Role.RESCUER)
    }

    private fun selectRole(role: Role) {
        if (role == _role.value) return
        roleStore.save(role)
        engine.setRole(role)
        _role.value = role
    }

    // Without a GPS fix the SOS still goes out, and rescuers find it by signal strength alone.
    // Nothing reports battery to the engine until the battery watcher in sense lands, so read
    // it once here; otherwise the SOS would carry "battery unknown".
    fun sendSos() {
        batteryPercent()?.let(engine::setBattery)
        engine.sendSos(NO_DETAILS, locationReader.lastFix())
    }

    private fun batteryPercent(): Int? {
        val manager = getApplication<Application>().getSystemService(BatteryManager::class.java)
        val percent = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return percent?.takeIf { it in 0..100 }
    }

    fun cancelSos() {
        engine.cancelSos()
    }

    override fun onCleared() {
        locationReader.stop()
    }
}

// The SOS screen no longer asks for people or conditions; 0 people means "not given" (Contract 2).
private val NO_DETAILS = SosDetails(
    people = 0,
    injured = false,
    trapped = false,
    water = false,
    medical = false,
    fire = false,
)
