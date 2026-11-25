package com.infinitytechcare.screentime

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

/** One child device's remote status, as seen by a parent. */
data class RemoteDevice(
    val name: String,
    val locked: Boolean,
    val bonusUntil: Long,
    val requestPending: Boolean,
    val requestText: String,
    val lat: Double?,
    val lng: Double?,
    val locAt: Long
)

/**
 * Thin wrapper over Firestore for remote lock/unlock and "ask for more time".
 * It carries only on/off commands and a short request message — never screen
 * content, audio, messages or location.
 *
 * Data model:  families/{code}/devices/{deviceName}
 *   locked: Boolean          (parent sets; child enforces)
 *   bonusUntil: Long (ms)    (parent grants extra time; child skips limits until then)
 *   requestPending: Boolean  (child asks for more time)
 *   requestText: String
 */
object RemoteRepo {

    private fun db() = FirebaseFirestore.getInstance()

    private fun deviceDoc(code: String, device: String): DocumentReference =
        db().collection("families").document(code)
            .collection("devices").document(device)

    /** Anonymous sign-in so Firestore rules can require an authenticated user. */
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

    /** Listen to this device's lock + bonus. Returns a registration to remove later. */
    fun listenMyDevice(ctx: Context, onChange: (locked: Boolean, bonusUntil: Long) -> Unit): ListenerRegistration? {
        val code = Prefs.familyCode(ctx)
        val device = Prefs.deviceName(ctx)
        if (code.isBlank() || device.isBlank()) return null
        return deviceDoc(code, device).addSnapshotListener { snap, _ ->
            if (snap != null && snap.exists()) {
                onChange(snap.getBoolean("locked") ?: false, snap.getLong("bonusUntil") ?: 0L)
            }
        }
    }

    fun updateLocation(ctx: Context, lat: Double, lng: Double) {
        val code = Prefs.familyCode(ctx)
        val device = Prefs.deviceName(ctx)
        if (code.isBlank() || device.isBlank()) return
        deviceDoc(code, device).set(
            mapOf("lat" to lat, "lng" to lng, "locAt" to System.currentTimeMillis()),
            SetOptions.merge()
        )
    }

    fun askForTime(ctx: Context, text: String) {
        val code = Prefs.familyCode(ctx)
        val device = Prefs.deviceName(ctx)
        if (code.isBlank() || device.isBlank()) return
        deviceDoc(code, device).set(
            mapOf(
                "requestPending" to true,
                "requestText" to text,
                "requestAt" to System.currentTimeMillis()
            ),
            SetOptions.merge()
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
                        requestPending = d.getBoolean("requestPending") ?: false,
                        requestText = d.getString("requestText") ?: "",
                        lat = d.getDouble("lat"),
                        lng = d.getDouble("lng"),
                        locAt = d.getLong("locAt") ?: 0L
                    )
                }
                onList(list)
            }
    }

    fun setLocked(code: String, device: String, locked: Boolean) {
        deviceDoc(code, device).set(mapOf("locked" to locked), SetOptions.merge())
    }

    fun grantMinutes(code: String, device: String, minutes: Int) {
        deviceDoc(code, device).set(
            mapOf(
                "bonusUntil" to System.currentTimeMillis() + minutes * 60_000L,
                "locked" to false,
                "requestPending" to false
            ),
            SetOptions.merge()
        )
    }

    fun clearRequest(code: String, device: String) {
        deviceDoc(code, device).set(mapOf("requestPending" to false), SetOptions.merge())
    }
}
