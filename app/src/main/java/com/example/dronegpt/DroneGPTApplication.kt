package com.example.dronegpt

import android.app.Application
import android.content.Context


class DroneGPTApplication: Application() {
    override fun onCreate() {
        super.onCreate()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        com.secneo.sdk.Helper.install(this)
    }
}