/*
 * ManagedAlarmPlayer.kt
 *
 * Copyright 2021 by MicMun
 */
package de.micmun.android.nextcloudcookbook.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer

/**
 * Managed alarm player for cooking timer.
 *
 * @author MicMun
 * @version 1.0, 15.05.21
 */
class ManagedAlarmPlayer(private val context: Context) {
   private var alarmPlayer: MediaPlayer? = null

   var isPlaying = false
      private set

   fun play(resId: Int) {
      if (!isPlaying) {
         isPlaying = true
         alarmPlayer = MediaPlayer.create(
            context,
            resId,
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId())
         alarmPlayer?.start()
      }
   }

   fun stop() {
      if (isPlaying) {
         alarmPlayer?.stop()
         alarmPlayer?.release()
         alarmPlayer = null
         isPlaying = false
      }
   }
}