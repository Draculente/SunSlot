package de.sunslot.app

import android.app.Application
import de.sunslot.app.worker.SyncWeatherWorker

class SunSlotApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SyncWeatherWorker.schedulePeriodic(this)
    }
}
