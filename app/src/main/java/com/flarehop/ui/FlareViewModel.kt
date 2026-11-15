package com.flarehop.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.flarehop.flare.Flare
import com.flarehop.flare.FlareEngine
import com.flarehop.flare.LogType
import com.flarehop.flare.Role
import com.flarehop.flare.SosDetails
import com.flarehop.sense.HeardSound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// Victim and Nearby are both the engine's SURVIVOR role: every survivor phone can send its own
// SOS and also carry others'. Only the Rescuer tab can switch the role, after a confirm.
enum class Tab { VICTIM, NEARBY, RESCUER }

class FlareViewModel(application: Application) : AndroidViewModel(application) {
    val engine: FlareEngine = Flare.engine

    val locationReader = LocationReader(application, viewModelScope)

    // The beacon service runs the microphone and says here whether it is listening.
    val listening: StateFlow<Boolean> = Flare.listening

    // This phone's own screen reads the exact time of the last sound from the listener, not from
    // its own SOS record, which only changes when a new sound is heard.
    val heardSound: StateFlow<HeardSound?> = Flare.signsOfLife(application).lastHeard

    private val roleStore = RoleStore(application)

    private val _role = MutableStateFlow(roleStore.load())
    val role: StateFlow<Role> = _role.asStateFlow()

    private val _tab = MutableStateFlow(
        if (_role.value == Role.RESCUER) Tab.RESCUER else Tab.VICTIM,
    )
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _findTarget = MutableStateFlow<Long?>(null)
    val findTarget: StateFlow<Long?> = _findTarget.asStateFlow()

    // Kept from the start, so Track can show what happened before it was opened.
    private val history = SosHistory()
    private val _tracks = MutableStateFlow<Map<Long, SosTrack>>(emptyMap())
    val tracks: StateFlow<Map<Long, SosTrack>> = _tracks.asStateFlow()

    // Kept here rather than in a screen so the names survive tab switches.
    private val _names = MutableStateFlow(PhoneNames())
    val names: StateFlow<PhoneNames> = _names.asStateFlow()

    // Which rescuer answered each SOS, for the Nearby graph. The engine only says so in its log.
    private val _receiptsBy = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val receiptsBy: StateFlow<Map<Long, Int>> = _receiptsBy.asStateFlow()

    init {
        engine.setRole(_role.value)
        // Every new fix goes into this phone's SOS, so one sent before GPS locked on still gets
        // a position.
        viewModelScope.launch {
            locationReader.location.collect { locationReader.lastFix()?.let(engine::setFix) }
        }
        viewModelScope.launch {
            engine.knownSos.collect { _tracks.value = history.update(it) }
        }
        viewModelScope.launch {
            combine(engine.knownSos, engine.group, engine.mySos, engine.carried) {
                    known,
                    group,
                    mine,
                    carried,
                ->
                val myIds = setOfNotNull(mine?.record?.originId) + carried.map { it.carrierId }
                _names.value.update(known, group.map { it.phoneId }, myIds)
            }.collect { _names.value = it }
        }
        viewModelScope.launch {
            engine.log.collect { event ->
                if (event.type == LogType.RECEIPT_MATCHED) {
                    _receiptsBy.value = _receiptsBy.value + (event.messageId to event.phoneId)
                }
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

    override fun onCleared() {
        locationReader.stop()
    }
}
