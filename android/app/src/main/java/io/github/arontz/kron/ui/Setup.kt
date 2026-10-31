package io.github.arontz.kron.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** One permission kRon asks for: whether it's on, and where in Settings to turn it on. */
enum class Step { Overlay, NotificationAccess, Notifications, Battery }

object Setup {
    fun isDone(context: Context, step: Step): Boolean = when (step) {
        Step.Overlay -> Settings.canDrawOverlays(context)
        Step.NotificationAccess ->
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        Step.Notifications -> Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        Step.Battery ->
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    /** The settings screen for a step. Notifications are asked for in-app instead, so it has none. */
    @SuppressLint("BatteryLife") // kRon ships outside Play and must stay running to show the pill.
    fun settingsIntent(context: Context, step: Step): Intent? {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (step) {
            Step.Overlay -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            Step.NotificationAccess -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            Step.Notifications -> null
            Step.Battery -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        }
    }

    /** App info, where "Allow restricted settings" lives for apps installed from an APK. */
    fun appInfoIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
