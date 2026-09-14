package de.sunslot.app.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.sunslot.app.R
import de.sunslot.app.data.model.DayOutdoorWindow
import de.sunslot.app.data.repository.WeatherRepository
import de.sunslot.app.domain.renderer.TimelineBitmapRenderer
import de.sunslot.app.domain.scorer.OutdoorWindowScorer
import de.sunslot.app.util.WeatherFormatting
import de.sunslot.app.worker.SyncWeatherWorker

private const val TAG = "OutdoorWidget"
private const val SYNC_THROTTLE_MS = 60L * 1000

class OutdoorWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (days, locationName, lastUpdated) = try {
            val repo = WeatherRepository.getInstance(context)
            val coords = repo.coordinates()
            // Cache-first: the network fetch happens via SyncWeatherWorker so the
            // widget update stays fast and reliable (no Glance timeout issues).
            val forecast = repo.cachedForecastForNextDays(days = 3)
            val scored = OutdoorWindowScorer.scoreWindows(forecast, coords.lat, coords.lon, days = 3)
            Triple(scored, repo.locationName(), repo.lastUpdatedAt())
        } catch (e: Exception) {
            Log.w(TAG, "provideGlance failed", e)
            Triple(emptyList<DayOutdoorWindow>(), "", 0L)
        }

        if (days.isEmpty()) {
            val repo = WeatherRepository.getInstance(context)
            val now = System.currentTimeMillis()
            if (now - repo.lastSyncRequestedAt() > SYNC_THROTTLE_MS) {
                repo.updateLastSyncRequestedAt(now)
                SyncWeatherWorker.syncNow(context)
                Log.i(TAG, "No cached data, scheduled immediate sync")
            }
        }

        provideContent {
            GlanceTheme {
                WidgetContent(context, days, locationName, lastUpdated)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        days: List<DayOutdoorWindow>,
        locationName: String,
        lastUpdated: Long
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(R.color.widget_background))
                .cornerRadius(24.dp)
                .padding(16.dp)
                .clickable(
                    onClick = actionRunCallback<RefreshWidgetAction>(),
                    rippleOverride = R.drawable.ripple_none
                ),
            contentAlignment = Alignment.TopStart
        ) {
            if (days.isEmpty()) {
                EmptyWidgetState(hasDataEver = lastUpdated > 0L)
            } else {
                DaysList(context, days, locationName, lastUpdated)
            }
        }
    }

    @Composable
    private fun DaysList(
        context: Context,
        days: List<DayOutdoorWindow>,
        locationName: String,
        lastUpdated: Long
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                text = if (locationName.isNotBlank()) locationName else "Bestes Wetterfenster",
                style = TextStyle(
                    color = ColorProvider(R.color.widget_on_surface_variant),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Text(
                text = WeatherFormatting.lastUpdatedLabel(lastUpdated),
                style = TextStyle(
                    color = ColorProvider(R.color.widget_on_surface_variant),
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(8.dp))

            days.forEachIndexed { index, day ->
                DayRow(context, day)
                if (index < days.lastIndex) {
                    Spacer(modifier = GlanceModifier.height(10.dp))
                }
            }
        }
    }

    @Composable
    private fun DayRow(context: Context, day: DayOutdoorWindow) {
        val renderer = remember(context) { TimelineBitmapRenderer(context) }
        val bitmap = remember(day) { renderer.render(day) }

        val rangeLabel = when {
            day.startHour != null && day.endHour != null ->
                WeatherFormatting.hourRangeLabel(day.startHour, day.endHour)
            else -> day.partialRanges.maxByOrNull { it.last - it.first }?.let { range ->
                WeatherFormatting.hourRangeLabel(range.first, range.last + 1)
            }
        }
        val imageDescription = if (rangeLabel != null) {
            if (day.startHour != null) "Beste Zeit $rangeLabel" else "Medium Zeit $rangeLabel"
        } else {
            "Kein passendes Zeitfenster"
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.width(80.dp)) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = WeatherFormatting.dayLabel(day.date),
                        style = TextStyle(
                            color = ColorProvider(R.color.widget_on_surface),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        text = " · ",
                        style = TextStyle(
                            color = ColorProvider(R.color.widget_on_surface_variant),
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = WeatherFormatting.tempLabel(day.avgTemperature),
                        style = TextStyle(
                            color = ColorProvider(R.color.widget_on_surface_variant),
                            fontSize = 12.sp
                        )
                    )
                }
                if (rangeLabel != null) {
                    Text(
                        text = rangeLabel,
                        style = TextStyle(
                            color = ColorProvider(R.color.widget_on_surface_variant),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            Image(
                provider = ImageProvider(bitmap),
                contentDescription = imageDescription,
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(48.dp),
                contentScale = ContentScale.Fit
            )
        }
    }

    @Composable
    private fun EmptyWidgetState(hasDataEver: Boolean) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasDataEver) "Keine Daten gefunden – Netzwerk prüfen" else "Wird aktualisiert…",
                style = TextStyle(
                    color = ColorProvider(R.color.widget_on_surface),
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Button(
                text = "Aktualisieren",
                onClick = actionRunCallback<RefreshWidgetAction>()
            )
        }
    }

}

class RefreshWidgetAction : androidx.glance.appwidget.action.ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        SyncWeatherWorker.syncNow(context)
        OutdoorWidget().update(context, glanceId)
    }
}

class OutdoorWidgetReceiver : androidx.glance.appwidget.GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OutdoorWidget()
}
