package com.lightest.launcher

import android.app.admin.DeviceAdminReceiver

/** Device-admin hook used only for instant lock. No other policies are requested. */
class LockScreenAdminReceiver : DeviceAdminReceiver()
