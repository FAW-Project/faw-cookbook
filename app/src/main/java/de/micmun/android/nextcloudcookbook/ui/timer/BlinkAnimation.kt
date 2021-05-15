/*
 * BlinkAnimation.kt
 *
 * Copyright 2021 by MicMun
 */
package de.micmun.android.nextcloudcookbook.ui.timer

import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.Transformation

/**
 * Blink animation.
 *
 * @author MicMun
 * @version 1.0, 15.05.21
 */
class BlinkAnimation : Animation() {
   init {
      interpolator = LinearInterpolator()
      duration = 1000
      repeatCount = INFINITE
   }

   override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
      t.alpha = when {
         interpolatedTime < 0.2f -> 1f
         interpolatedTime < 0.6f -> 0f
         else -> 1f
      }
   }
}