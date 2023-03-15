package io.nekohasekai.sagernet.utils

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Application
import android.content.Context

@SuppressLint("Registered")
@TargetApi(24)
class DeviceStorageApp(context: Context) : Application() {
    init {
        attachBaseContext(context.createDeviceProtectedStorageContext())
    }

    /**
     * Thou shalt not get the REAL underlying application context which would no longer be operating under device
     * protected storage.
     */
    override fun getApplicationContext() = this
}
