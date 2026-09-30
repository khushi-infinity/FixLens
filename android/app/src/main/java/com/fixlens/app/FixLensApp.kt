package com.fixlens.app

import android.app.Application
import com.fixlens.app.di.AppContainer

/**
 * FixLens application entry point: initializes the app-wide dependency
 * container (device config, capture store, API client, billing).
 */
class FixLensApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
