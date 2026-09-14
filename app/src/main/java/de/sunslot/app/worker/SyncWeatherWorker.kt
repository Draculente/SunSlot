package de.sunslot.app.worker

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.sunslot.app.data.repository.WeatherRepository
import de.sunslot.app.widget.OutdoorWidget
import java.util.concurrent.TimeUnit

class SyncWeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = WeatherRepository.getInstance(applicationContext)
            repo.getForecastForNextDays(days = 3)
            updateAllWidgets()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun updateAllWidgets() {
        val manager = GlanceAppWidgetManager(applicationContext)
        val glanceIds = manager.getGlanceIds(OutdoorWidget::class.java)
        glanceIds.forEach { id ->
            OutdoorWidget().update(applicationContext, id)
        }
    }

    companion object {
        private const val WORK_NAME = "weather_sync"
        private const val IMMEDIATE_WORK_NAME = "weather_sync_immediate"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWeatherWorker>(3, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            syncNow(context)
        }

        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWeatherWorker>()
                .setConstraints(Constraints(NetworkType.CONNECTED))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
