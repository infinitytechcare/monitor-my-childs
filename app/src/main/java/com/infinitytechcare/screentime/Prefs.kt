package com.infinitytechcare.screentime

import android.content.Context

/** A per-app daily cap, in minutes. */
data class AppLimit(val pkg: String, val minutes: Int)

/** A time window during which apps are locked (e.g. bedtime, school). */
data class LockWindow(val startMin: Int, val endMin: Int, val label: String)

/**
 * All settings live in plain SharedPreferences on this phone. Nothing is sent
 * anywhere. The parent configures these in the app; the MonitorService reads them.
 */
object Prefs {

    private fun sp(c: Context) = c.getSharedPreferences("fst_prefs", Context.MODE_PRIVATE)

    // --- enforcement service on/off ---
    fun isServiceEnabled(c: Context) = sp(c).getBoolean("svc", false)
    fun setServiceEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("svc", v).apply()

    // --- local event alerts (notifications on THIS phone, nothing sent out) ---
    fun isAlertsEnabled(c: Context) = sp(c).getBoolean("alerts", true)
    fun setAlertsEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("alerts", v).apply()

    // --- total daily screen-time cap across all apps (minutes; 0 = off) ---
    fun totalCapMinutes(c: Context) = sp(c).getInt("cap", 0)
    fun setTotalCapMinutes(c: Context, v: Int) = sp(c).edit().putInt("cap", v).apply()

    // --- forced break: after N continuous minutes, lock for M minutes (0 = off) ---
    fun breakAfterMinutes(c: Context) = sp(c).getInt("brk_after", 0)
    fun setBreakAfterMinutes(c: Context, v: Int) = sp(c).edit().putInt("brk_after", v).apply()
    fun breakForMinutes(c: Context) = sp(c).getInt("brk_for", 10)
    fun setBreakForMinutes(c: Context, v: Int) = sp(c).edit().putInt("brk_for", v).apply()

    // --- whitelist mode: only allowed apps may run ---
    fun isWhitelistEnabled(c: Context) = sp(c).getBoolean("wl_on", false)
    fun setWhitelistEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("wl_on", v).apply()

    fun allowed(c: Context): Set<String> =
        HashSet(sp(c).getStringSet("wl", emptySet()) ?: emptySet())

    fun setAppAllowed(c: Context, pkg: String, allowed: Boolean) {
        val set = HashSet(allowed(c))
        if (allowed) set.add(pkg) else set.remove(pkg)
        sp(c).edit().putStringSet("wl", set).apply()
    }

    // --- per-app daily limits, stored as "pkg|minutes" ---
    fun limits(c: Context): Map<String, Int> {
        val raw = sp(c).getStringSet("limits", emptySet()) ?: emptySet()
        val out = HashMap<String, Int>()
        for (s in raw) {
            val i = s.lastIndexOf('|')
            if (i <= 0) continue
            val pkg = s.substring(0, i)
            val min = s.substring(i + 1).toIntOrNull() ?: continue
            out[pkg] = min
        }
        return out
    }

    fun setLimit(c: Context, pkg: String, minutes: Int) {
        val cur = limits(c).toMutableMap()
        if (minutes > 0) cur[pkg] = minutes else cur.remove(pkg)
        val set = cur.entries.map { "${it.key}|${it.value}" }.toHashSet()
        sp(c).edit().putStringSet("limits", set).apply()
    }

    // --- lock windows, stored as "start|end|label" (minutes of day) ---
    fun windows(c: Context): List<LockWindow> {
        val raw = sp(c).getStringSet("windows", emptySet()) ?: emptySet()
        val out = ArrayList<LockWindow>()
        for (s in raw) {
            val parts = s.split("|")
            if (parts.size < 3) continue
            val start = parts[0].toIntOrNull() ?: continue
            val end = parts[1].toIntOrNull() ?: continue
            out.add(LockWindow(start, end, parts[2]))
        }
        return out.sortedBy { it.startMin }
    }

    private fun saveWindows(c: Context, list: List<LockWindow>) {
        val set = list.map { "${it.startMin}|${it.endMin}|${it.label}" }.toHashSet()
        sp(c).edit().putStringSet("windows", set).apply()
    }

    fun addWindow(c: Context, w: LockWindow) {
        val list = windows(c).toMutableList()
        list.removeAll { it.startMin == w.startMin && it.endMin == w.endMin && it.label == w.label }
        list.add(w)
        saveWindows(c, list)
    }

    fun removeWindow(c: Context, w: LockWindow) {
        val list = windows(c).filterNot {
            it.startMin == w.startMin && it.endMin == w.endMin && it.label == w.label
        }
        saveWindows(c, list)
    }

    // --- remote control (Firebase) ---
    fun isRemoteEnabled(c: Context) = sp(c).getBoolean("rc_on", false)
    fun setRemoteEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("rc_on", v).apply()

    fun familyCode(c: Context): String = sp(c).getString("rc_code", "") ?: ""
    fun setFamilyCode(c: Context, v: String) = sp(c).edit().putString("rc_code", v.trim()).apply()

    fun deviceName(c: Context): String = sp(c).getString("rc_device", "") ?: ""
    fun setDeviceName(c: Context, v: String) = sp(c).edit().putString("rc_device", v.trim()).apply()

    // "parent" or "child"
    fun role(c: Context): String = sp(c).getString("rc_role", "child") ?: "child"
    fun setRole(c: Context, v: String) = sp(c).edit().putString("rc_role", v).apply()

    fun remoteReady(c: Context): Boolean =
        isRemoteEnabled(c) && familyCode(c).isNotBlank() && deviceName(c).isNotBlank()
}
