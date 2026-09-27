package com.invokeil.shinigami.core.actions

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.app.RemoteInput
import com.invokeil.shinigami.core.util.ShiniLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory ring of recent notifications, filled by
 * [com.invokeil.shinigami.service.ShinigamiNotificationListener].
 * Nothing is persisted; only metadata the user explicitly asks for is read.
 */
@Singleton
class NotificationCenter @Inject constructor() {

    data class Notif(
        val key: String,
        val packageName: String,
        val appLabel: String,
        val title: String?,
        val text: String?,
        val postedAt: Long,
        val canReply: Boolean,
        val sbn: StatusBarNotification? = null,
    )

    private val ring = ArrayDeque<Notif>(MAX)
    private val lock = Any()

    @Volatile
    var listener: android.service.notification.NotificationListenerService? = null

    fun push(notif: Notif) {
        synchronized(lock) {
            ring.removeAll { it.key == notif.key }
            ring.addFirst(notif)
            while (ring.size > MAX) ring.removeLast()
        }
    }

    fun remove(key: String) {
        synchronized(lock) { ring.removeAll { it.key == key } }
    }

    fun clear() {
        synchronized(lock) { ring.clear() }
    }

    fun recent(appFilter: String? = null, limit: Int = 10): List<Notif> = synchronized(lock) {
        val f = appFilter?.trim()?.lowercase()
        ring.toList()
            .filter { f == null || it.appLabel.lowercase().contains(f) || it.packageName.lowercase().contains(f) }
            .take(limit)
    }

    fun byKey(key: String): Notif? = synchronized(lock) { ring.firstOrNull { it.key == key } }

    private companion object {
        const val MAX = 40
    }
}

/**
 * Dispatcher that turns validated tool calls into Android work.
 * Every executor returns a plain-language result — we never fake success
 * (MASTER SPEC §41, §103).
 */
@Singleton
class ExecutorDispatcher @Inject constructor(
    private val appCatalog: AppCatalog,
    private val notifications: NotificationCenter,
    private val routineDao: com.invokeil.shinigami.core.data.db.RoutineDao,
    private val routineRunner: dagger.Lazy<com.invokeil.shinigami.feature.routines.RoutineRunner>,
) {

    suspend fun dispatch(context: Context, call: ValidatedToolCall): ActionResult = try {
        when (call.def.id) {
            "open_app" -> openApp(context, call)
            "app_info" -> appInfo(context, call)
            "open_url" -> openUrl(context, call)
            "web_search" -> webSearch(context, call)
            "navigate" -> navigate(context, call)
            "set_alarm" -> setAlarm(context, call)
            "set_timer" -> setTimer(context, call)
            "media_play" -> mediaKey(context, AudioManager.ADJUST_RAISE, android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
            "media_pause" -> mediaKey(context, AudioManager.ADJUST_SAME, android.view.KeyEvent.KEYCODE_MEDIA_PAUSE)
            "media_next" -> mediaKey(context, AudioManager.ADJUST_SAME, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
            "media_previous" -> mediaKey(context, AudioManager.ADJUST_SAME, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "volume_set" -> volumeSet(context, call)
            "volume_mute" -> volumeMute(context, true)
            "volume_unmute" -> volumeMute(context, false)
            "flashlight" -> flashlight(context, call)
            "battery_status" -> batteryStatus(context)
            "device_info" -> deviceInfo(context)
            "open_settings" -> openSettings(context, call)
            "dnd_set" -> dndSet(context, call)
            "brightness_set" -> brightnessSet(context, call)
            "contact_lookup" -> contactLookup(context, call)
            "dial_number" -> dialNumber(context, call)
            "call_contact" -> callContact(context, call)
            "sms_compose" -> smsCompose(context, call)
            "read_notifications" -> readNotifications(context, call)
            "dismiss_notification" -> dismissNotification(context, call)
            "reply_notification" -> replyNotification(context, call)
            "screen_read" -> screenRead(context)
            "screen_tap" -> screenTap(context, call)
            "screen_scroll" -> screenScroll(context)
            "screen_back" -> screenBack(context)
            "run_routine" -> runRoutine(context, call)
            else -> ActionResult.Failure("UNSUPPORTED", "This capability has no executor.")
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (t: Throwable) {
        ShiniLog.e(TAG, "executor crashed for ${call.def.id}", t)
        ActionResult.Failure(
            "EXECUTOR_ERROR",
            humanExecutorError(call.def.id, t),
        )
    }

    // ---------------------------------------------------------------- apps --

    private suspend fun openApp(context: Context, call: ValidatedToolCall): ActionResult {
        val name = call.args["app_name"]?.toString() ?: return fail("Missing app name.")
        val app = appCatalog.find(context, name)
            ?: return ActionResult.Failure(
                "APP_NOT_INSTALLED",
                "I couldn't find an app called “$name” on this device.",
            )
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return ActionResult.Failure("NOT_LAUNCHABLE", "${app.label} can't be launched directly.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ActionResult.Success("Opened ${app.label}")
    }

    private suspend fun appInfo(context: Context, call: ValidatedToolCall): ActionResult {
        val name = call.args["app_name"]?.toString() ?: return fail("Missing app name.")
        val app = appCatalog.find(context, name)
            ?: return ActionResult.Failure("APP_NOT_INSTALLED", "No app named “$name” found.")
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ActionResult.Success("Opened app info for ${app.label}")
    }

    // ----------------------------------------------------------------- web --

    private fun openUrl(context: Context, call: ValidatedToolCall): ActionResult {
        val raw = call.args["url"]?.toString() ?: return fail("Missing URL.")
        val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            ActionResult.Success("Opened $url")
        } catch (t: Throwable) {
            ActionResult.Failure("NO_BROWSER", "No browser is available to open that link.")
        }
    }

    private fun webSearch(context: Context, call: ValidatedToolCall): ActionResult {
        val query = call.args["query"]?.toString() ?: return fail("Missing search query.")
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Searching the web for “$query”")
        } catch (t: Throwable) {
            // Fallback: google search URL
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                ActionResult.Success("Searching the web for “$query”")
            } catch (t2: Throwable) {
                ActionResult.Failure("NO_BROWSER", "No browser is available to search the web.")
            }
        }
    }

    private fun navigate(context: Context, call: ValidatedToolCall): ActionResult {
        val dest = call.args["destination"]?.toString() ?: return fail("Missing destination.")
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(dest))
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            ActionResult.Success("Getting directions to $dest")
        } catch (t: Throwable) {
            ActionResult.Failure("NO_MAPS", "No maps application is available.")
        }
    }

    // ---------------------------------------------------------------- time --

    private fun setAlarm(context: Context, call: ValidatedToolCall): ActionResult {
        val hour = (call.args["hour"] as? Number)?.toInt()
            ?: call.args["hour"]?.toString()?.toIntOrNull()
            ?: return fail("Missing alarm hour.")
        val minute = (call.args["minute"] as? Number)?.toInt() ?: 0
        if (hour !in 0..23 || minute !in 0..59) {
            return ActionResult.Failure("BAD_TIME", "That time doesn't exist — try a 24h time like 19:30.")
        }
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            call.args["label"]?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it.toString()) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Alarm set for %02d:%02d".format(hour, minute))
        } catch (t: Throwable) {
            // No clock app that handles SET_ALARM — open the clock instead.
            try {
                context.startActivity(
                    context.packageManager.getLaunchIntentForPackage("com.google.android.deskclock")
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?: Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                ActionResult.Failure("NO_ALARM_APP", "I couldn't set it directly — opened your clock app instead.")
            } catch (t2: Throwable) {
                ActionResult.Failure("NO_ALARM_APP", "No clock app available to set alarms.")
            }
        }
    }

    private fun setTimer(context: Context, call: ValidatedToolCall): ActionResult {
        val seconds = (call.args["seconds"] as? Number)?.toLong()
            ?: call.args["seconds"]?.toString()?.toLongOrNull()
            ?: return fail("Missing timer duration.")
        if (seconds <= 0 || seconds > 86_400) {
            return ActionResult.Failure("BAD_DURATION", "Timers between 1 second and 24 hours are supported.")
        }
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.toInt())
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            call.args["label"]?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it.toString()) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("Timer started: ${durationText(seconds)}")
        } catch (t: Throwable) {
            ActionResult.Failure("NO_TIMER_APP", "No clock app available to start timers.")
        }
    }

    // --------------------------------------------------------------- media --

    private fun mediaKey(context: Context, adjust: Int, keyCode: Int): ActionResult {
        val am = context.getSystemService(AudioManager::class.java)
            ?: return ActionResult.Failure("NO_AUDIO", "Audio service unavailable.")
        // Wake the most recent media session via a key dispatch
        val down = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        val up = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        return try {
            am.dispatchMediaKeyEvent(down)
            am.dispatchMediaKeyEvent(up)
            ActionResult.Success(
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> "Playing"
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> "Paused"
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> "Skipped to next track"
                    else -> "Previous track"
                },
            )
        } catch (t: Throwable) {
            ActionResult.Failure("NO_MEDIA_SESSION", "Nothing is playing right now.")
        }
    }

    private fun volumeSet(context: Context, call: ValidatedToolCall): ActionResult {
        val level = (call.args["level"] as? Number)?.toInt() ?: return fail("Missing volume level.")
        if (level !in 0..100) return ActionResult.Failure("BAD_LEVEL", "Volume must be between 0 and 100.")
        val am = context.getSystemService(AudioManager::class.java)
            ?: return ActionResult.Failure("NO_AUDIO", "Audio service unavailable.")
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * level) / 100, 0)
        return ActionResult.Success("Media volume set to $level%")
    }

    private fun volumeMute(context: Context, mute: Boolean): ActionResult {
        val am = context.getSystemService(AudioManager::class.java)
            ?: return ActionResult.Failure("NO_AUDIO", "Audio service unavailable.")
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
            0,
        )
        return ActionResult.Success(if (mute) "Media muted" else "Media unmuted")
    }

    // -------------------------------------------------------------- device --

    private fun flashlight(context: Context, call: ValidatedToolCall): ActionResult {
        val on = call.args["on"] as? Boolean ?: return fail("Missing on/off state.")
        val cm = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
            ?: return ActionResult.Failure("NO_CAMERA", "Camera service unavailable.")
        val torchId = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return ActionResult.Failure("NO_TORCH", "This device has no flashlight.")
        cm.setTorchMode(torchId, on)
        return ActionResult.Success(if (on) "Flashlight ON" else "Flashlight OFF")
    }

    private fun batteryStatus(context: Context): ActionResult {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ActionResult.Failure("NO_BATTERY", "Battery info unavailable.")
        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        if (level < 0) return ActionResult.Failure("NO_BATTERY", "Battery info unavailable.")
        val pct = (level * 100) / scale
        val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
            status == android.os.BatteryManager.BATTERY_STATUS_FULL
        return ActionResult.Success(
            "Battery at $pct%" + if (charging) " — charging" else "",
        )
    }

    private fun deviceInfo(context: Context): ActionResult {
        val batteryIntent = context.registerReceiver(
            null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        )
        val batteryPct = batteryIntent?.let {
            val level = it.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0) (level * 100) / scale else -1
        } ?: -1
        val storage = try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeGb = stat.availableBytes / (1024.0 * 1024 * 1024)
            "%.0f GB free".format(freeGb)
        } catch (t: Throwable) {
            "storage unavailable"
        }
        return ActionResult.Success(
            "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}, " +
                "Android ${Build.VERSION.RELEASE}, battery $batteryPct%, $storage",
        )
    }

    private fun openSettings(context: Context, call: ValidatedToolCall): ActionResult {
        val screen = call.args["screen"]?.toString() ?: "general"
        val action = when (screen.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> "android.intent.action.POWER_USAGE_SUMMARY"
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "date" -> Settings.ACTION_DATE_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult.Success("Opened ${screen.lowercase()} settings")
        } catch (t: Throwable) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                ActionResult.Success("Opened settings")
            } catch (t2: Throwable) {
                ActionResult.Failure("NO_SETTINGS", "Couldn't open settings.")
            }
        }
    }

    private fun dndSet(context: Context, call: ValidatedToolCall): ActionResult {
        val on = call.args["on"] as? Boolean ?: return fail("Missing on/off state.")
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
            ?: return ActionResult.Failure("NO_DND", "Notification service unavailable.")
        if (!nm.isNotificationPolicyAccessGranted) {
            return ActionResult.Failure(
                "DND_ACCESS_MISSING",
                "Do Not Disturb access is missing — grant it from the Permission Dashboard.",
            )
        }
        nm.setInterruptionFilter(
            if (on) android.app.NotificationManager.INTERRUPTION_FILTER_NONE
            else android.app.NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        return ActionResult.Success(if (on) "Do Not Disturb ON" else "Do Not Disturb OFF")
    }

    private fun brightnessSet(context: Context, call: ValidatedToolCall): ActionResult {
        val level = (call.args["level"] as? Number)?.toInt() ?: return fail("Missing brightness level.")
        if (level !in 0..100) return ActionResult.Failure("BAD_LEVEL", "Brightness must be 0-100.")
        if (!Settings.System.canWrite(context)) {
            return ActionResult.Failure(
                "WRITE_SETTINGS_MISSING",
                "Modify system settings is missing — grant it from the Permission Dashboard.",
            )
        }
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            (level * 255) / 100,
        )
        return ActionResult.Success("Brightness set to $level%")
    }

    // ---------------------------------------------------------------- comms --

    private suspend fun contactLookup(context: Context, call: ValidatedToolCall): ActionResult {
        val name = call.args["name"]?.toString() ?: return fail("Missing contact name.")
        return withContext(Dispatchers.IO) {
            val numbers = queryContactNumbers(context, name)
            when {
                numbers.isEmpty() -> ActionResult.Failure(
                    "CONTACT_NOT_FOUND",
                    "No contact found for “$name”.",
                )

                else -> ActionResult.Success("${numbers.first().second}: ${numbers.first().first}")
            }
        }
    }

    private suspend fun callContact(context: Context, call: ValidatedToolCall): ActionResult {
        val name = call.args["name"]?.toString() ?: return fail("Missing contact name.")
        val numbers = withContext(Dispatchers.IO) { queryContactNumbers(context, name) }
        val number = numbers.firstOrNull()?.first
            ?: return ActionResult.Failure("CONTACT_NOT_FOUND", "No contact found for “$name”.")
        return try {
            context.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            ActionResult.Success("Calling $name")
        } catch (t: Throwable) {
            // CALL_PHONE missing or blocked — fall back to the dialer, honestly.
            try {
                context.startActivity(
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                ActionResult.Failure(
                    "CALL_FALLBACK",
                    "Direct calling isn't allowed — the dialer is open with $name ready, press call.",
                )
            } catch (t2: Throwable) {
                ActionResult.Failure("NO_DIALER", "No dialer available.")
            }
        }
    }

    private fun dialNumber(context: Context, call: ValidatedToolCall): ActionResult {
        val number = call.args["number"]?.toString()?.filter { it.isDigit() || "+()- ".contains(it) }
            ?: return fail("Missing phone number.")
        if (number.isBlank()) return ActionResult.Failure("BAD_NUMBER", "That doesn't look like a phone number.")
        return try {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            ActionResult.Success("Dialer open with $number")
        } catch (t: Throwable) {
            ActionResult.Failure("NO_DIALER", "No dialer available.")
        }
    }

    private fun smsCompose(context: Context, call: ValidatedToolCall): ActionResult {
        val body = call.args["body"]?.toString() ?: return fail("Missing message text.")
        val phone = call.args["phone"]?.toString()
        val uri = Uri.parse("sms:" + (phone ?: ""))
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult.Success("SMS draft ready${if (phone != null) " for $phone" else ""}")
        } catch (t: Throwable) {
            ActionResult.Failure("NO_SMS_APP", "No messaging app available.")
        }
    }

    private fun queryContactNumbers(context: Context, name: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        try {
            val resolver = context.contentResolver
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val cursor = resolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null,
            )
            cursor?.use {
                while (it.moveToNext() && results.size < 5) {
                    val number = it.getString(0) ?: continue
                    val display = it.getString(1) ?: name
                    results.add(number to display)
                }
            }
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "contact query failed (missing permission?)")
        }
        return results
    }

    // -------------------------------------------------------- notifications --

    private fun readNotifications(context: Context, call: ValidatedToolCall): ActionResult {
        val filter = call.args["app"]?.toString()
        val list = notifications.recent(filter, limit = 8)
        if (list.isEmpty()) {
            return ActionResult.Success(
                if (filter == null) "No recent notifications."
                else "No recent notifications from $filter.",
            )
        }
        val text = list.joinToString("\n") { n ->
            "• ${n.appLabel}: ${n.title ?: ""}${if (n.text.isNullOrBlank()) "" else " — ${n.text.take(120)}"}"
        }
        return ActionResult.Success("Latest notifications:\n$text")
    }

    private suspend fun dismissNotification(context: Context, call: ValidatedToolCall): ActionResult {
        val filter = call.args["app"]?.toString()
        val target = notifications.recent(filter, limit = 1).firstOrNull()
            ?: return ActionResult.Failure("NOT_FOUND", "No matching notification found.")
        val listener = notifications.listener
            ?: return ActionResult.Failure("NO_LISTENER", "Notification access isn't active.")
        return try {
            listener.cancelNotification(target.key)
            notifications.remove(target.key)
            ActionResult.Success("Dismissed notification from ${target.appLabel}")
        } catch (t: Throwable) {
            ActionResult.Failure("DISMISS_FAILED", "Couldn't dismiss that notification.")
        }
    }

    private suspend fun replyNotification(context: Context, call: ValidatedToolCall): ActionResult {
        val text = call.args["text"]?.toString() ?: return fail("Missing reply text.")
        val filter = call.args["app"]?.toString()
        val target = notifications.recent(filter, limit = 10).firstOrNull { it.canReply }
            ?: return ActionResult.Failure(
                "NO_REPLYABLE",
                "No recent conversation supports inline replies.",
            )
        val listener = notifications.listener
            ?: return ActionResult.Failure("NO_LISTENER", "Notification access isn't active.")
        val sbn = target.sbn ?: return ActionResult.Failure("NO_REPLYABLE", "That conversation can't be replied to.")
        val action = sbn.notification.actions?.firstOrNull { a -> a.remoteInputs != null && a.remoteInputs.isNotEmpty() }
            ?: return ActionResult.Failure("NO_REPLY_ACTION", "That app doesn't support inline replies.")
        return try {
            val remoteInput = action.remoteInputs.first()
            val intent = Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val bundle = android.os.Bundle()
            bundle.putCharSequence(remoteInput.resultKey, text)
            RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
            action.actionIntent.send(context, 0, intent)
            ActionResult.Success("Reply sent to ${target.appLabel}")
        } catch (t: Throwable) {
            ShiniLog.w(TAG, "remote reply failed")
            ActionResult.Failure("REPLY_FAILED", "The app rejected the inline reply.")
        }
    }

    // ------------------------------------------------------------- helpers --

    private fun fail(msg: String) = ActionResult.Failure("BAD_REQUEST", msg)

    private fun durationText(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return when {
            m > 0 && s > 0 -> "${m}m ${s}s"
            m > 0 -> "${m}m"
            else -> "${s}s"
        }
    }

    private fun humanExecutorError(toolId: String, t: Throwable): String = when (toolId) {
        "flashlight" -> "The flashlight is busy — close the camera app and try again."
        "set_alarm", "set_timer" -> "Your clock app refused the request."
        "open_app" -> "The app couldn't be opened."
        "brightness_set" -> "The system refused the brightness change."
        else -> "The device refused that action."
    }


    // -------------------------------------------------- v0.2: screen/a11y --

    private fun screenRead(context: Context): ActionResult {
        val svc = com.invokeil.shinigami.service.ShinigamiAccessibilityService.instance
            ?: return ActionResult.Failure(
                "A11Y_OFF",
                "Turn on Shinigami's accessibility service first (Settings > Permissions).",
            )
        val captured = svc.captureScreenContext()
            ?: return ActionResult.Failure(
                "PROTECTED_APP",
                "Screen access is disabled for this app.",
            )
        return ActionResult.Success(
            "Screen of ${captured.appLabel}:\n${captured.text.take(1800)}",
        )
    }

    private fun screenTap(context: Context, call: ValidatedToolCall): ActionResult {
        val svc = com.invokeil.shinigami.service.ShinigamiAccessibilityService.instance
            ?: return ActionResult.Failure("A11Y_OFF", "Accessibility service isn't enabled.")
        val x = (call.args["x"] as? Number)?.toFloat()
            ?: call.args["x"]?.toString()?.toFloatOrNull()
            ?: return fail("Missing tap X.")
        val y = (call.args["y"] as? Number)?.toFloat()
            ?: call.args["y"]?.toString()?.toFloatOrNull()
            ?: return fail("Missing tap Y.")
        return if (svc.tapScreen(x, y)) ActionResult.Success("Tapped ($x, $y).")
        else ActionResult.Failure("GESTURE_FAILED", "The tap gesture didn't go through.")
    }

    private fun screenScroll(context: Context): ActionResult {
        val svc = com.invokeil.shinigami.service.ShinigamiAccessibilityService.instance
            ?: return ActionResult.Failure("A11Y_OFF", "Accessibility service isn't enabled.")
        return if (svc.scrollDown()) ActionResult.Success("Scrolled down.")
        else ActionResult.Failure("GESTURE_FAILED", "Scroll didn't go through.")
    }

    private fun screenBack(context: Context): ActionResult {
        val svc = com.invokeil.shinigami.service.ShinigamiAccessibilityService.instance
            ?: return ActionResult.Failure("A11Y_OFF", "Accessibility service isn't enabled.")
        return if (svc.globalBack()) ActionResult.Success("Went back.")
        else ActionResult.Failure("GESTURE_FAILED", "Back didn't go through.")
    }

    private suspend fun runRoutine(context: Context, call: ValidatedToolCall): ActionResult {
        val name = call.args["name"]?.toString()?.trim()
            ?: return fail("Missing routine name.")
        val all = try { routineDao.allEnabled() } catch (_: Throwable) { emptyList() }
        val routine = all.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: all.firstOrNull { it.name.contains(name, ignoreCase = true) }
            ?: return ActionResult.Failure("NO_ROUTINE", "There's no routine called \"$name\".")
        return try {
            kotlinx.coroutines.withTimeout(120_000) {
                val runner = routineRunner.get()
                runner.run(routine.id)
                val progress = runner.progress.value
                when {
                    progress?.error != null ->
                        ActionResult.Failure("ROUTINE_STEP", progress.error ?: "A step failed.")
                    else ->
                        ActionResult.Success("Routine ${routine.name} finished (${progress?.total ?: 0} steps).")
                }
            }
        } catch (t: Throwable) {
            ActionResult.Failure("ROUTINE_ERROR", "Routine failed: ${t.message ?: "unknown"}")
        }
    }

    private companion object {
        const val TAG = "Executors"
    }
}
