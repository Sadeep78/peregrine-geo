package dev.peregrinegeo.peregrine

import android.app.Application

/**
 * Peregrine Application entry point.
 * Provides testing and development mock-location injection.
 */
class PeregrineApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
