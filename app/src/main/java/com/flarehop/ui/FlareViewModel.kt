package com.flarehop.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flarehop.flare.Flare
import com.flarehop.flare.FlareEngine
import com.flarehop.flare.LogEvent
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
import com.flarehop.flare.SosStatus
import com.flarehop.sense.SignsOfLife
import com.flarehop.sense.YamnetListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Tab { SURVIVOR, RESCUER, LOG, DEMO }

class FlareViewModel(application: Application) : AndroidViewModel(application) {
    val engine: FlareEngine = Flare.engine

    val locationReader = LocationReader(application, viewModelScope)

    // The beacon service runs this same watcher, so the Demo battery override reaches the engine
    // no matter which of the two last pushed a level.
    private val battery = Flare.battery(application)

    val signs = SignsOfLife(engine, batteryPercent = { battery.overridePercent ?: realBattery() })
    private val listener = YamnetListener(application, signs)

    private val roleStore = RoleStore(application)

    private val _role = MutableStateFlow(roleStore.load())
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _tab = MutableStateFlow(
        if (_role.value == Role.RESCUER) Tab.RESCUER else Tab.SURVIVOR,
    )
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _findTarget = MutableStateFlow<Long?>(null)
    val findTarget: StateFlow<Long?> = _findTarget.asStateFlow()

    private val _events = MutableStateFlow<List<LogEvent>>(emptyList())
    val events: StateFlow<List<LogEvent>> = _events.asStateFlow()

    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _batteryOverride = MutableStateFlow(battery.overridePercent)
    val batteryOverride: StateFlow<Int?> = _batteryOverride.asStateFlow()

    private val _listenContinuously = MutableStateFlow(signs.listenContinuously)
    val listenContinuously: StateFlow<Boolean> = _listenContinuously.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    init {
        engine.setRole(_role.value)
        // log is a SharedFlow without replay, so events are kept here from the start; otherwise
        // everything that happened before the Log tab opened would be lost.
        viewModelScope.launch {
            engine.log.collect { event ->
                _events.value = (listOf(event) + _events.value).take(MAX_EVENTS)
            }
        }
        // Contract 4: the listener runs on the phone that carries an SOS.
        viewModelScope.launch {
            engine.mySos.collect { mine ->
                if (mine?.status == SosStatus.SENDING) startListening() else stopListening()
            }
        }
    }

    fun openTab(tab: Tab) {
        _tab.value = tab
    }

    // A rescuer phone sends a receipt for every SOS it hears (Contract 3, rule 2), which tells the
    // victim help is coming. Opening the tab alone must not do that, so the user confirms first.
    fun becomeRescuer() {
        selectRole(Role.RESCUER)
    }

    fun stopRescuing() {
        _findTarget.value = null
        selectRole(Role.SURVIVOR)
    }

    private fun selectRole(role: Role) {
        if (role == _role.value) return
        roleStore.save(role)
        engine.setRole(role)
        _role.value = role
    }

    // Without a GPS fix the SOS still goes out, and rescuers find it by signal strength alone.
    fun sendSos(details: SosDetails) {
        engine.sendSos(details, locationReader.lastFix())
    }

    fun cancelSos() {
        engine.cancelSos()
    }

    fun find(messageId: Long) {
        _findTarget.value = messageId
    }

    fun stopFinding() {
        _findTarget.value = null
    }

    fun setBatteryOverride(percent: Int?) {
        battery.overridePercent = percent
        _batteryOverride.value = percent
    }

    fun fakeKnock() {
        signs.fakeKnock(System.currentTimeMillis())
    }

    fun setListenContinuously(on: Boolean) {
        signs.listenContinuously = on
        _listenContinuously.value = on
    }

    fun setKeepScreenOn(on: Boolean) {
        _keepScreenOn.value = on
    }

    fun reset() {
        _findTarget.value = null
        _events.value = emptyList()
        engine.reset()
    }

    // Without RECORD_AUDIO the SOS still goes out, only without signs of life.
    private fun startListening() {
        if (_listening.value) return
        val canRecord = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!canRecord) return
        // The model or microphone can fail to open on some phones; the beacon must keep going.
        runCatching { listener.start(viewModelScope) }
            .onSuccess { _listening.value = true }
            .onFailure { Log.w(TAG, "Sound listener did not start", it) }
    }

    private fun stopListening() {
        if (!_listening.value) return
        listener.stop()
        _listening.value = false
    }

    private fun realBattery(): Int? = getApplication<Application>()
        .getSystemService(BatteryManager::class.java)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }

    override fun onCleared() {
        locationReader.stop()
        stopListening()
    }

    private companion object {
        const val TAG = "FlareViewModel"
        const val MAX_EVENTS = 200
    }
}
