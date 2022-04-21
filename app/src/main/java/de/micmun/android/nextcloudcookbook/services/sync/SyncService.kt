package de.micmun.android.nextcloudcookbook.services.sync

import android.app.AlarmManager
import android.app.IntentService
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.micmun.android.nextcloudcookbook.nextcloudapi.Accounts
import de.micmun.android.nextcloudcookbook.nextcloudapi.Sync
import de.micmun.android.nextcloudcookbook.notifications.NotificationChannelManager
import de.micmun.android.nextcloudcookbook.notifications.NotificationChannelManager.Companion.SYNC_SERVICE_NOTIFICATION_ID
import de.micmun.android.nextcloudcookbook.settings.PreferenceData
import java.util.*
import java.util.concurrent.Executors


class SyncService : IntentService("SyncService") {

   private var mLocalBroadcastManager = LocalBroadcastManager.getInstance(this)

   companion object {
      val TAG = SyncService::class.java.toString()
      private const val SECOND = 1000
      private const val MINUTE = SECOND * 60
      private const val HOUR = MINUTE * 60
      const val SYNC_SERVICE_START_BROADCAST = "SYNC_SERVICE_START_BROADCAST"
      const val SYNC_SERVICE_UPDATE_BROADCAST = "SYNC_SERVICE_UPDATE_BROADCAST"
      const val SYNC_SERVICE_UPDATE_STATUS = "SYNC_SERVICE_UPDATE_STATUS"
      const val SYNC_SERVICE_UPDATE_STATUS_START = "SYNC_SERVICE_UPDATE_STATUS_START"
      const val SYNC_SERVICE_UPDATE_STATUS_END = "SYNC_SERVICE_UPDATE_STATUS_END"
      const val SYNC_SERVICE_INTERVAL_DEFAULT = 24

      fun startServiceScheduling(context: Context) {

         if (Accounts(context).getCurrentAccount() == null) {
            //no sso, dont schedule
            return
         }

         if (!PreferenceData.getInstance().isSyncServiceEnabled()) {
            // Sync disabled. Dont schedule.
            return
         }

         val interval = PreferenceData.getInstance().getSyncServiceInterval()

         val myIntent = Intent(context.applicationContext, SyncService::class.java)
         val pendingIntent = PendingIntent.getService(context, 0, myIntent, PendingIntent.FLAG_IMMUTABLE)

         val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
         val calendar: Calendar = Calendar.getInstance()
         calendar.timeInMillis = System.currentTimeMillis()
         //calendar.set(Calendar.SECOND, 0)
         //calendar.set(Calendar.MINUTE, 0)
         //calendar.set(Calendar.HOUR_OF_DAY, 1)
         alarmManager.cancel(pendingIntent)
         alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            ( HOUR * interval).toLong(),
            pendingIntent
         )
      }
   }

   @Deprecated("Deprecated in Java")
   override fun onHandleIntent(intent: Intent?) {
      when (intent?.action) {
         SYNC_SERVICE_START_BROADCAST -> {
            startService(Intent(this, SyncService::class.java))
         }
      }
   }

   @Deprecated("Deprecated in Java")
   override fun onBind(intent: Intent): IBinder? {
      return null
   }

   @Deprecated("Deprecated in Java")
   override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      when (intent?.action) {
         SYNC_SERVICE_START_BROADCAST -> {
            startService(Intent(this, SyncService::class.java))
         }
      }

      NotificationChannelManager.createSyncServiceNotificationChannel(this)
      val serviceNotification = NotificationChannelManager.createSyncServiceNotification(this)
      startForeground(SYNC_SERVICE_NOTIFICATION_ID, serviceNotification.build())
      sync()
      return START_STICKY
   }

   private fun sync() {
      sendSyncStartEvent()
      Executors.newSingleThreadExecutor().submit {
         try {
            val sync = Sync(this)
            sync.synchronizeRecipes()
            sync.closeAPI()
         } catch (e: Exception) {
            Log.e(TAG, "Error Syncing: " + e.message)
         } finally {
            sendSyncEndEvent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
               stopForeground(true)
            }
         }
      }
   }

   private fun sendSyncStartEvent() {
      val intent = Intent(SYNC_SERVICE_UPDATE_BROADCAST)
      intent.putExtra(SYNC_SERVICE_UPDATE_STATUS, SYNC_SERVICE_UPDATE_STATUS_START)
      mLocalBroadcastManager.sendBroadcast(intent)

   }

   private fun sendSyncEndEvent() {
      val intent = Intent(SYNC_SERVICE_UPDATE_BROADCAST)
      intent.putExtra(SYNC_SERVICE_UPDATE_STATUS, SYNC_SERVICE_UPDATE_STATUS_END)
      mLocalBroadcastManager.sendBroadcast(intent)
   }
}