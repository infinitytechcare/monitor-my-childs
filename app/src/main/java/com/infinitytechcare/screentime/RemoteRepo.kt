package com.infinitytechcare.screentime

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

/** One child device's status, as seen by a parent. */
data class RemoteDevice(
    val name: String,
    val locked: Boolean,
    val bonusUntil: Long,
    val lockUntil: Long,
    val requestPending: Boolean,
    val requestText: String,
    val lat: Double?,
    val lng: Double?,
    val locAt: Long,
    val usageTotal: Long,
    val usageTop: List<String>,
    val usage7: List<String>,
    val usageAt: Long,
    val battery: Long,
    val batteryAt: Long,
    val hasZone: Boolean,
    val inZone: Boolean,
    val lastBlockedApp: String,
    val lastBlockedPkg: String,
    val lastBlockedAt: Long,
    val curCap: Long,
    val curWlOn: Boolean,
    val curBedStart: Long,
    val curBedEnd: Long
)

/** Commands + config a parent set, delivered to the child device. */
data class RemoteCommand(
    val locked: Boolean,
    val bonusUntil: Long,
    val lockUntil: Long,
    val locRequest: Boolean,
    val zoneLat: Double?,
    val zoneLng: Double?,
    val zoneRadius: Double,
    val cfgTotalCap: Long?,
    val cfgWhitelist: Boolean?,
    val cfgBedStart: Long?,
    val cfgBedEnd: Long?,
    val allowApp: String
)

/**
 * Thin wrapper over Firestore for transparent parental features: remote lock,
 * timed lock, extra-time, remote limits/schedule, location, geofence, battery
 * and usage reporting. It carries only commands, config and aggregate status —
 * never screen content, audio, messages, or keystrokes.
 *
 * Data model:  families/{code}/devices/{deviceName}
 */
object RemoteRepo {

    private fun db() = FirebaseFirestore.getInstance()

    private fun deviceDoc(code: String, device: String): DocumentReference =
        db().collection("families").document(code)
            .collection("devices").document(device)

    fun ensureAuth(onReady: (Boolean) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser != null) {
            onReady(true); return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { onReady(true) }
            .addOnFailureListener { onReady(false) }
    }

    // ---------- child side ----------

    fun registerChild(ctx: Context) {
        val code = Prefs.familyCode(ctx)
        val device = Prefs.deviceName(ctx)
        if (code.isBlank() || device.isBlank()) return
        deviceDoc(code, device).set(
            mapOf("updatedAt" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    fun listenMyDevice(ctx: Context, onChange: (RemoteCommand) -> Unit): ListenerRegistration? {
        val code = Prefs.familyCode(ctx)
        val device = Prefs.deviceName(ctx)
        if (code.isBlank() || device.isBlank()) return null
        return deviceDoc(code, device).addSnapshotListener { d, _ ->
            if (d != null && d.exists()) {
                onChange(
                    RemoteCommand(
                        locked = d.getBoolean("locked") ?: false,
                        bonusUntil = d.getLong("bonusUntil") ?: 0L,
                        lockUntil = d.getLong("lockUntil") ?: 0L,
                        locRequest = d.getBoolean("locRequest") ?: false,
                        zoneLat = d.getDouble("zoneLat"),
                        zoneLng = d.getDouble("zoneLng"),
                        zoneRadius = d.getDouble("zoneRadius") ?: 0.0,
                        cfgTotalCap = d.getLong("cfgTotalCap"),
                        cfgWhitelist = d.getBoolean("cfgWhitelist"),
                        cfgBedStart = d.getLong("cfgBedStart"),
                        cfgBedEnd = d.getLong("cfgBedEnd"),
                        allowApp = d.getString("allowApp") ?: ""
                    )
                )
            }
        }
    }

    private fun childWrite(ctx: Context, data: Map<String, Any?>) {
        val code = Prefs.familyCode(ctx)
        val device = Prefs.deviceName(ctx)
        if (code.isBlank() || device.isBlank()) return
        deviceDoc(code, device).set(data, SetOptions.merge())
    }

    fun updateUsage(ctx: Context, totalMin: Int, top: List<String>, last7: List<String>) {
        childWrite(
            ctx,
            mapOf(
                "usageTotal" to totalMin.toLong(),
                "usageTop" to top,
                "usage7" to last7,
                "usageAt" to System.currentTimeMillis()
            )
        )
    }

    fun updateLocation(ctx: Context, lat: Double, lng: Double) {
        childWrite(ctx, mapOf("lat" to lat, "lng" to lng, "locAt" to System.currentTimeMillis()))
    }

    fun updateBattery(ctx: Context, level: Int) {
        childWrite(ctx, mapOf("battery" to level.toLong(), "batteryAt" to System.currentTimeMillis()))
    }

    fun updateZoneStatus(ctx: Context, hasZone: Boolean, inZone: Boolean) {
        childWrite(ctx, mapOf("hasZone" to hasZone, "inZone" to inZone))
    }

    fun updateBlocked(ctx: Context, appLabel: String, pkg: String) {
        childWrite(
            ctx,
            mapOf(
                "lastBlockedApp" to appLabel,
                "lastBlockedPkg" to pkg,
                "lastBlockedAt" to System.currentTimeMillis()
            )
        )
    }

    /** Child reports its current effective settings so the parent can show what's active. */
    fun updateConfigState(ctx: Context, cap: Int, wlOn: Boolean, bedStart: Int, bedEnd: Int) {
        childWrite(
            ctx,
            mapOf(
                "curCap" to cap.toLong(),
                "curWlOn" to wlOn,
                "curBedStart" to bedStart.toLong(),
                "curBedEnd" to bedEnd.toLong()
            )
        )
    }

    fun clearLocRequest(ctx: Context) = childWrite(ctx, mapOf("locRequest" to false))
    fun clearAllowApp(ctx: Context) = childWrite(ctx, mapOf("allowApp" to ""))

    fun askForTime(ctx: Context, text: String) {
        childWrite(
            ctx,
            mapOf("requestPending" to true, "requestText" to text, "requestAt" to System.currentTimeMillis())
        )
    }

    // ---------- parent side ----------

    fun listenDevices(code: String, onList: (List<RemoteDevice>) -> Unit): ListenerRegistration? {
        if (code.isBlank()) return null
        return db().collection("families").document(code).collection("devices")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.map { d ->
                    RemoteDevice(
                        name = d.id,
                        locked = d.getBoolean("locked") ?: false,
                        bonusUntil = d.getLong("bonusUntil") ?: 0L,
                        lockUntil = d.getLong("lockUntil") ?: 0L,
                        requestPending = d.getBoolean("requestPending") ?: false,
                        requestText = d.getString("requestText") ?: "",
                        lat = d.getDouble("lat"),
                        lng = d.getDouble("lng"),
                        locAt = d.getLong("locAt") ?: 0L,
                        usageTotal = d.getLong("usageTotal") ?: 0L,
                        usageTop = (d.get("usageTop") as? List<*>)?.map { it.toString() } ?: emptyList(),
                        usage7 = (d.get("usage7") as? List<*>)?.map { it.toString() } ?: emptyList(),
                        usageAt = d.getLong("usageAt") ?: 0L,
                        battery = d.getLong("battery") ?: -1L,
                        batteryAt = d.getLong("batteryAt") ?: 0L,
                        hasZone = d.getBoolean("hasZone") ?: false,
                        inZone = d.getBoolean("inZone") ?: true,
                        lastBlockedApp = d.getString("lastBlockedApp") ?: "",
                        lastBlockedPkg = d.getString("lastBlockedPkg") ?: "",
                        lastBlockedAt = d.getLong("lastBlockedAt") ?: 0L,
                        curCap = d.getLong("curCap") ?: -1L,
                        curWlOn = d.getBoolean("curWlOn") ?: false,
                        curBedStart = d.getLong("curBedStart") ?: -1L,
                        curBedEnd = d.getLong("curBedEnd") ?: -1L
                    )
                }
                onList(list)
            }
    }

    private fun parentWrite(code: String, device: String, data: Map<String, Any?>) {
        deviceDoc(code, device).set(data, SetOptions.merge())
    }

    fun setLocked(code: String, device: String, locked: Boolean) =
        parentWrite(code, device, mapOf("locked" to locked))

    fun grantMinutes(code: String, device: String, minutes: Int) =
        parentWrite(
            code, device,
            mapOf(
                "bonusUntil" to System.currentTimeMillis() + minutes * 60_000L,
                "locked" to false,
                "lockUntil" to 0L,
                "requestPending" to false
            )
        )

    /** Timed lock: locks until now+minutes, then auto-unlocks. */
    fun lockForMinutes(code: String, device: String, minutes: Int) =
        parentWrite(code, device, mapOf("lockUntil" to System.currentTimeMillis() + minutes * 60_000L))

    fun requestLocation(code: String, device: String) =
        parentWrite(code, device, mapOf("locRequest" to true))

    fun setZone(code: String, device: String, lat: Double, lng: Double, radius: Double) =
        parentWrite(code, device, mapOf("zoneLat" to lat, "zoneLng" to lng, "zoneRadius" to radius))

    fun clearZone(code: String, device: String) =
        parentWrite(code, device, mapOf("zoneLat" to null, "zoneLng" to null, "zoneRadius" to 0.0))

    fun setTotalCap(code: String, device: String, minutes: Int) =
        parentWrite(code, device, mapOf("cfgTotalCap" to minutes.toLong()))

    fun setWhitelist(code: String, device: String, on: Boolean) =
        parentWrite(code, device, mapOf("cfgWhitelist" to on))

    fun setBedtime(code: String, device: String, startMin: Int, endMin: Int) =
        parentWrite(code, device, mapOf("cfgBedStart" to startMin.toLong(), "cfgBedEnd" to endMin.toLong()))

    fun allowApp(code: String, device: String, pkg: String) =
        parentWrite(code, device, mapOf("allowApp" to pkg))

    fun clearRequest(code: String, device: String) =
        parentWrite(code, device, mapOf("requestPending" to false))
}
