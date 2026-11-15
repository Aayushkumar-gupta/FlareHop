package com.flarehop.flare

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import com.flarehop.flare.FlareConstants.COMPANY_ID
import com.flarehop.ui.RoleStore

class WakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (FlareService.running) return
        val results = IntentCompat.getParcelableArrayListExtra(
            intent,
            BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
            ScanResult::class.java,
        ) ?: return
        val heard = results.mapNotNull { result ->
            result.scanRecord?.getManufacturerSpecificData(COMPANY_ID)?.let { it to result.rssi }
        }
        if (!WakeRule.heardSos(heard.map { it.first })) return
        // The process may be new, so the engine gets the saved role and the SOS it was woken by
        // before the service starts broadcasting.
        val engine = Flare.engine
        engine.setRole(RoleStore(context).load())
        val now = System.currentTimeMillis()
        heard.forEach { (bytes, rssi) -> engine.onPacket(bytes, rssi, now) }
        FlareService.wake(context)
    }
}
