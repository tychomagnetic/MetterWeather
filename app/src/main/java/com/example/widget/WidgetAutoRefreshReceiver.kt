package io.github.tychomagnetic.metterweather.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-establishes the durable widget schedule after boot or app replacement. */
class WidgetAutoRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED ->
                WidgetRefreshManager.scheduleAutoRefresh(context.applicationContext)
        }
    }
}
