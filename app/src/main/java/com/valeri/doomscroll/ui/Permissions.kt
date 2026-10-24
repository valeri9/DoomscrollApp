package com.valeri.doomscroll.ui

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.valeri.doomscroll.service.DoomscrollAccessibilityService

/** Reads the three permissions the app needs, none of which use a runtime dialog. */
object Permissions {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, DoomscrollAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // The four-argument checkOpNoThrow is API 36 only; on anything older it throws
        // NoSuchMethodError and takes the dashboard down with it.
        val op = AppOpsManager.OPSTR_GET_USAGE_STATS
        val uid = Process.myUid()
        val mode = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA ->
                appOps.checkOpNoThrow(op, uid, context.packageName, null)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                @Suppress("DEPRECATION") appOps.unsafeCheckOpNoThrow(op, uid, context.packageName)
            else ->
                @Suppress("DEPRECATION") appOps.checkOpNoThrow(op, uid, context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openAccessibilitySettings(context: Context) =
        context.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openOverlaySettings(context: Context) = context.launch(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}"),
        )
    )

    fun openUsageAccessSettings(context: Context) =
        context.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openBatterySettings(context: Context) =
        context.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    private fun Context.launch(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
