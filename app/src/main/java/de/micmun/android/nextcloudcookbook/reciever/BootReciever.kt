package de.micmun.android.nextcloudcookbook.reciever

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.micmun.android.nextcloudcookbook.services.sync.SyncService

class BootReciever : BroadcastReceiver() {

    private val TAG = BootReciever::class.toString()

    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action== Intent.ACTION_BOOT_COMPLETED){
            Log.d(TAG, "BootReciever: Started SyncScheduling!")
            SyncService().startServiceScheduling(context)
        }
    }
}