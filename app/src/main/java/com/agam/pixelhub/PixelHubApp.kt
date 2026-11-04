package com.agam.pixelhub

import android.app.Application
import android.content.Context
import com.agam.pixelhub.notify.Channels

class PixelHubApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Channels.create(this)
    }
}

val Context.container: AppContainer
    get() = (applicationContext as PixelHubApp).container
