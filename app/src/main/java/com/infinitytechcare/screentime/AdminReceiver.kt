package com.infinitytechcare.screentime

import android.app.admin.DeviceAdminReceiver

/**
 * Being an active device admin means the app cannot be uninstalled until an
 * adult deactivates admin in Settings first — so a child can't casually remove it.
 * It grants no monitoring ability of any kind.
 */
class AdminReceiver : DeviceAdminReceiver()
