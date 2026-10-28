package com.jtexpress.bevest.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** One thing the phone needs for the emergency alarm to get through, and where to fix it. */
data class AlarmCheck(
    val key: String,
    val title: String,
    val detail: String,
    val ok: Boolean,
    /** Settings pages to try, in order; the first one this phone has is opened. */
    val fixes: List<Intent>,
    /**
     * Steps the app can't verify (phone-brand settings): the officer follows [steps] and
     * confirms with "I've done this" instead.
     */
    val confirmable: Boolean = false,
    val steps: List<String> = emptyList(),
)

/** Settings some manufacturers add on top of Android, which kill background apps. */
private data class BrandSetup(val brand: String, val steps: List<String>, val pages: List<ComponentName>)

/**
 * Whether this phone will actually sound the emergency alarm. Android lets the user (or
 * the manufacturer) switch off each piece, and an app can't switch them back on itself —
 * it can only check and send the user to the right setting.
 */
object AlarmReadiness {

    const val KEY_NOTIFICATIONS = "notifications"
    const val KEY_BRAND = "brand"

    fun checks(context: Context): List<AlarmCheck> {
        NotificationHelper.ensureChannels(context)
        val pkg = context.packageName
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager?.getNotificationChannel(NotificationChannels.EMERGENCY)
        val channelSettings = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationChannels.EMERGENCY)

        val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val channelOn = channel != null && channel.importance >= NotificationManager.IMPORTANCE_HIGH
        val fullScreen = Build.VERSION.SDK_INT < 34 || manager?.canUseFullScreenIntent() == true
        val bypassDnd = channel?.canBypassDnd() == true
        val audio = context.getSystemService(AudioManager::class.java)
        val alarmVolume = audio == null || audio.getStreamVolume(AudioManager.STREAM_ALARM) > 0
        val battery = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(pkg) == true

        return buildList {
            add(
                AlarmCheck(
                    key = KEY_NOTIFICATIONS,
                    title = "Notifications allowed",
                    detail = "Without this, no safety alert can reach you.",
                    ok = notificationsOn,
                    fixes = listOf(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
                    ),
                ),
            )
            add(
                AlarmCheck(
                    key = "channel",
                    title = "Emergency alarm channel on",
                    detail = "Must stay on with \"Pop on screen\" so the siren and vibration play.",
                    ok = channelOn,
                    fixes = listOf(channelSettings),
                ),
            )
            add(
                AlarmCheck(
                    key = "full_screen",
                    title = "Full-screen alerts",
                    detail = "Lets the red emergency screen wake the phone and show over the lock screen.",
                    ok = fullScreen,
                    fixes = if (Build.VERSION.SDK_INT >= 34) {
                        listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")))
                    } else {
                        listOf(channelSettings)
                    },
                ),
            )
            add(
                AlarmCheck(
                    key = "dnd",
                    title = "Override Do Not Disturb",
                    detail = "Turn on \"Override Do Not Disturb\" for Emergency alarms so the siren still plays.",
                    ok = bypassDnd,
                    fixes = listOf(channelSettings),
                ),
            )
            add(
                AlarmCheck(
                    key = "battery",
                    title = "No battery restrictions",
                    detail = "Find Bevest and choose \"Don't optimise\" (or \"No restrictions\") so alerts aren't delayed.",
                    ok = battery,
                    fixes = listOf(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)),
                ),
            )
            brandSetup()?.let { b ->
                add(
                    AlarmCheck(
                        key = KEY_BRAND,
                        title = "${b.brand} phone settings",
                        detail = "${b.brand} phones close background apps with their own settings, " +
                            "which Bevest can't check. Do these, then confirm.",
                        ok = AlarmSetupPrefs.brandConfirmed(context),
                        fixes = b.pages.map { Intent().setComponent(it) } + appDetails(pkg),
                        confirmable = true,
                        steps = b.steps,
                    ),
                )
            }
            add(
                AlarmCheck(
                    key = "volume",
                    title = "Alarm volume up",
                    detail = "The siren plays at alarm volume, which silent mode doesn't mute — but zero does.",
                    ok = alarmVolume,
                    fixes = listOf(Intent(Settings.ACTION_SOUND_SETTINGS)),
                ),
            )
        }
    }

    fun ready(context: Context): Boolean = checks(context).all { it.ok }

    /**
     * Opens the first settings page in [fixes] this phone actually has. Brand pages move
     * between OS versions, so every list ends in a page that always exists.
     */
    fun openFix(context: Context, fixes: List<Intent>) {
        for (intent in fixes + appDetails(context.packageName)) {
            val opened = runCatching {
                context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (opened) return
        }
    }

    /** Fires a sample emergency so the user can hear and see exactly what an alert does. */
    fun test(context: Context) {
        NotificationHelper.show(
            context = context,
            title = "⚠ Test emergency alarm",
            body = "This is how a fall or emergency on your site will sound. Tap Stop alarm or press a volume button to silence it.",
            channelId = NotificationChannels.EMERGENCY,
            type = "TEST",
            targetId = TEST_ID,
            workerId = null,
        )
    }

    private fun appDetails(pkg: String) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    private fun brandSetup(): BrandSetup? {
        val maker = Build.MANUFACTURER.lowercase()
        return when {
            maker in setOf("xiaomi", "redmi", "poco") -> BrandSetup(
                brand = "Xiaomi",
                steps = listOf(
                    "Autostart: turn ON for Bevest",
                    "Battery saver: choose \"No restrictions\"",
                    "Open recent apps, long-press Bevest and tap the lock icon",
                ),
                pages = listOf(
                    ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                ),
            )
            maker in setOf("oppo", "realme", "oneplus") -> BrandSetup(
                brand = if (maker == "oneplus") "OnePlus" else "Oppo / Realme",
                steps = listOf(
                    "Auto launch (or Auto startup): turn ON for Bevest",
                    "Battery: allow background activity",
                ),
                pages = listOf(
                    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                    ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                ),
            )
            maker in setOf("vivo", "iqoo") -> BrandSetup(
                brand = "Vivo",
                steps = listOf(
                    "Background power consumption: choose \"Allow high usage\"",
                    "Autostart: turn ON for Bevest",
                ),
                pages = listOf(
                    ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                ),
            )
            maker == "samsung" -> BrandSetup(
                brand = "Samsung",
                steps = listOf(
                    "Battery → Background usage limits → Never sleeping apps",
                    "Add Bevest to the list",
                ),
                pages = listOf(
                    ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                ),
            )
            maker in setOf("huawei", "honor") -> BrandSetup(
                brand = if (maker == "honor") "Honor" else "Huawei",
                steps = listOf(
                    "App launch: find Bevest and choose \"Manage manually\"",
                    "Turn ON Auto-launch, Secondary launch and Run in background",
                ),
                pages = listOf(
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                ),
            )
            else -> null
        }
    }

    private const val TEST_ID = "alarm-test"
}

/** What the officer has told us that the app can't check for itself. */
object AlarmSetupPrefs {
    private const val FILE = "alarm_setup"
    private const val BRAND = "brand_settings_confirmed"
    private const val NOTIFICATION_PROMPT = "notification_prompt_used"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * Android stops showing its notification prompt after it's refused, so the setup
     * asks once and then sends the officer to the settings page instead.
     */
    fun notificationPromptUsed(context: Context) = prefs(context).getBoolean(NOTIFICATION_PROMPT, false)
    fun markNotificationPromptUsed(context: Context) =
        prefs(context).edit().putBoolean(NOTIFICATION_PROMPT, true).apply()

    fun brandConfirmed(context: Context) = prefs(context).getBoolean(BRAND, false)
    fun setBrandConfirmed(context: Context, done: Boolean) = prefs(context).edit().putBoolean(BRAND, done).apply()
}
