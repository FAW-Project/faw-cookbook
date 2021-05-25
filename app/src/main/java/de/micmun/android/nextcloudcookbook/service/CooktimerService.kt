/*
 * CooktimerService.kt
 *
 * Copyright 2021 by MicMun.
 */package de.micmun.android.nextcloudcookbook.service

import android.app.Service
import android.content.Intent
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log

/**
 * Service for cooking timer with notification.
 *
 * @author MicMun
 * @version 1.0, 18.05.21
 */
class CooktimerService(private val totalTime: Long) : Service() {
   companion object {
      const val COUNTDOWN_BR = "de.micmun.android.nextcloudcookbook.countdown_br"
   }

   private val bi = Intent(COUNTDOWN_BR)
   private lateinit var cdt: CountDownTimer

   override fun onCreate() {
      super.onCreate()

      cdt = object : CountDownTimer(totalTime, 1000) {
         override fun onTick(millisUntilFinished: Long) {
            bi.putExtra("countdown", millisUntilFinished);
            sendBroadcast(bi);
         }

         override fun onFinish() {
            Log.d("CooktimerService", "Finished timer")
         }
      }
      cdt.start()
   }

   override fun onDestroy() {
      cdt.cancel()
      super.onDestroy()
   }

   override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      return super.onStartCommand(intent, flags, startId)
   }

   override fun onBind(intent: Intent?): IBinder? {
      return null
   }
}
