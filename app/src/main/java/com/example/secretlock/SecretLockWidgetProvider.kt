package com.example.secretlock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * SecretLockWidgetProvider: Home Screen App Widget provider that triggers manual device locking.
 */
class SecretLockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            // a. Create an Intent with action com.example.secretlock.WIDGET_LOCK
            val intent = Intent(SecretLockService.ACTION_WIDGET_LOCK).apply {
                // b. Explicitly set intent package to context.packageName for security
                setPackage(context.packageName)
            }

            // c. Create PendingIntent.getBroadcast using FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE (API 35 compliant)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // d. Use RemoteViews to attach this PendingIntent to @id/widget_lock_button via setOnClickPendingIntent
            val views = RemoteViews(context.packageName, R.layout.widget_lock).apply {
                setOnClickPendingIntent(R.id.widget_lock_button, pendingIntent)
            }

            // e. Call appWidgetManager.updateAppWidget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
