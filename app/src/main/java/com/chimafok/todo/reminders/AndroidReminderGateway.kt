package com.chimafok.todo.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.chimafok.todo.MainActivity
import com.chimafok.todo.R
import com.chimafok.todo.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AndroidReminderGateway(private val context: Context) : ReminderGateway {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    companion object { const val CHANNEL = "task_reminders" }
    fun ensureChannel() { notifications.createNotificationChannel(NotificationChannel(CHANNEL, "任務提醒", NotificationManager.IMPORTANCE_DEFAULT)) }
    override fun notificationsAllowed(): Boolean = (Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    override fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
    private fun uri(taskId: String, reminderId: String, key: String, action: String) = Uri.Builder().scheme("todo-reminder")
        .authority(context.packageName).appendPath(action).appendPath(taskId).appendPath(reminderId).appendPath(key).build()
    private fun uri(reminder: ReminderEntity, key: String, action: String) = uri(reminder.taskId, reminder.id, key, action)
    private fun broadcast(taskId: String, reminderId: String, key: String, action: String) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction(action).setData(uri(taskId, reminderId, key, action))
            .putExtra("reminderId", reminderId).putExtra("token", key), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun broadcast(reminder: ReminderEntity, key: String, action: String) = broadcast(reminder.taskId, reminder.id, key, action)
    override fun schedule(reminder: ReminderEntity, key: String, triggerAt: Long): String {
        val pending = broadcast(reminder, key, "FIRE")
        if (exactAllowed()) try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending); return "EXACT"
        } catch (_: SecurityException) { /* Permission can be revoked between the check and scheduling. */ }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        return "INEXACT"
    }
    override fun cancel(reminder: ReminderEntity, key: String) { alarms.cancel(broadcast(reminder, key, "FIRE")) }
    override fun cancelRemoved(taskId: String, reminderId: String, key: String) { alarms.cancel(broadcast(taskId, reminderId, key, "FIRE")) }
    override fun dismiss(reminderId: String) { notifications.cancel(reminderId, 0) }
    override fun show(reminder: ReminderEntity, task: TaskEntity, key: String) {
        ensureChannel()
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .setAction("OPEN_REMINDER").setData(uri(reminder, key, "OPEN"))
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra("reminderTaskId", task.id).putExtra("reminderTaskRevision", task.revision),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val done = broadcast(reminder, key, "COMPLETE")
        val due = task.dueDate?.let { "截止 $it${task.dueTime?.let { time -> " $time" }.orEmpty()}" } ?: "沒有截止日期"
        notifications.notify(reminder.id, 0, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setColor(0xFF57D6E5.toInt()).setContentTitle(task.title).setContentText(due)
            .setStyle(Notification.BigTextStyle().bigText(due)).setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "完成", done).build())
            .setAutoCancel(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_REMINDER).build())
    }
    fun openNotifications() = openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    fun openExact() = if (Build.VERSION.SDK_INT >= 31) openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}"))) else Unit
    private fun openSettings(intent: Intent) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

fun reminderStateLabel(state: String): String = when (state) {
    "DELIVERED" -> "已發出"; "MISSED" -> "已錯過，不補發"; "CANCELED" -> "已取消"
    "BLOCKED" -> "通知未開啟"; else -> "等待提醒"
}
fun reminderInstantLabel(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z"))
