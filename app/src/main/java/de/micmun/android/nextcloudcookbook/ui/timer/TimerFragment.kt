/*
 * TimerFragment.kt
 *
 * Copyright 2021 by MicMun
 */
package de.micmun.android.nextcloudcookbook.ui.timer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.anggrayudi.storage.extension.launchOnUiThread
import de.micmun.android.nextcloudcookbook.R
import de.micmun.android.nextcloudcookbook.databinding.FragmentTimerBinding
import de.micmun.android.nextcloudcookbook.util.DurationUtils
import de.micmun.android.nextcloudcookbook.util.ManagedAlarmPlayer
import java.util.*

/**
 * Fragment for timer.
 *
 * @author MicMun
 * @version 1.0, 15.05.21
 */
class TimerFragment : Fragment() {
   private lateinit var binding: FragmentTimerBinding
   private lateinit var viewModel: TimerViewModel
   private lateinit var alarmPlayer: ManagedAlarmPlayer
   private var cookTime: Long = -1
   private val blinkAnimation: Animation = BlinkAnimation()

   override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
      binding = DataBindingUtil.inflate(inflater, R.layout.fragment_timer, container, false)

      val args = TimerFragmentArgs.fromBundle(requireArguments())
      cookTime = args.cookTime
      val viewModelFactory = TimerViewModelFactory(cookTime, requireActivity().application)
      viewModel = ViewModelProvider(this, viewModelFactory).get(TimerViewModel::class.java)
      binding.lifecycleOwner = viewLifecycleOwner
      alarmPlayer = ManagedAlarmPlayer(requireContext())

      viewModel.currentTime.observe(viewLifecycleOwner, {
         it?.let {
            refreshUi()
         }
      })
      binding.btnReset.setOnClickListener {
         viewModel.stopTimer()
         findNavController().navigateUp()
      }
      if (viewModel.totalTime == null) {
         viewModel.totalTime = cookTime
         // first start: init
         binding.btnControl.apply {
            setImageResource(R.drawable.ic_play)
            setOnClickListener { viewModel.startTimer() }
         }
      }

      // observe status: runningPositive -> finished
      viewModel.state.observe(viewLifecycleOwner, { state ->
         @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
         when (state) {
            TimerViewModel.State.RUNNING_POSITIVE -> {
               // buttons
               binding.btnReset.apply { visibility = View.GONE }
               binding.btnControl.apply {
                  setImageResource(R.drawable.ic_pause)
                  setOnClickListener { viewModel.pauseTimer() }
               }
            }
            TimerViewModel.State.FINISHED -> {
               // animations
               binding.cookingTimeProgress.startAnimation(blinkAnimation)
               // alarms
               playAlarm()
            }
            TimerViewModel.State.PAUSED -> {
               // buttons
               binding.btnReset.apply { visibility = View.VISIBLE }
               binding.btnControl.apply {
                  setImageResource(R.drawable.ic_play)
                  setOnClickListener { viewModel.startTimer() }
               }
            }
         }
      })

      return binding.root
   }

   private fun refreshUi() {
      if (viewModel.totalTime == null) {
         val totalTime = DurationUtils.getCurrentMillisDisplay(cookTime)
         binding.timeDisplay.text = totalTime
         return
      }

      val remainingTime = viewModel.totalTime!! - viewModel.currentTime.value!!

      // cookingTimeProgress bar
      if (remainingTime > 0) {
         if (binding.cookingTimeProgress.max != viewModel.totalTime!!.toInt())
            binding.cookingTimeProgress.max = viewModel.totalTime!!.toInt()
         binding.cookingTimeProgress.setProgress(viewModel.currentTime.value!!.toFloat(), true, 100)
      } else {
         viewModel.stopTimer()
      }

      val display = DurationUtils.getCurrentMillisDisplay(remainingTime)
      binding.timeDisplay.text = display
   }

   override fun onSaveInstanceState(outState: Bundle) {
      Log.d("TimerFragment", "onSaveInstanceState")
      outState.putBoolean("ALARM_PLAYING", alarmPlayer.isPlaying)
      alarmPlayer.stop()
      super.onSaveInstanceState(outState)
   }

   override fun onViewStateRestored(savedInstanceState: Bundle?) {
      super.onViewStateRestored(savedInstanceState)
      if (savedInstanceState?.getBoolean("ALARM_PLAYING") == true) playAlarm()
      Log.d("TimerFragment", "onViewStateRestored")
   }

   override fun onPause() {
      Log.d("TimerFragment", "onPause")
      alarmPlayer.stop()
      super.onPause()
   }

   private fun playAlarm() {
      alarmPlayer.play(R.raw.timer_expire_short)
      launchOnUiThread {

      }
      Timer().schedule(object : TimerTask() {
         override fun run() {
            alarmPlayer.stop()
            // buttons
            binding.btnReset.post { binding.btnReset.apply { visibility = View.VISIBLE } }
            binding.btnReset.post {
               binding.btnControl.apply {
                  setImageResource(R.drawable.ic_play)
                  setOnClickListener { viewModel.startTimer() }
               }
            }
            binding.cookingTimeProgress.post { binding.cookingTimeProgress.clearAnimation() }
         }
      }, 10000)
   }

   override fun onActivityCreated(savedInstanceState: Bundle?) {
      super.onActivityCreated(savedInstanceState)
      if (cookTime != -1L) {
         val title = getString(R.string.timer_title, DurationUtils.getCurrentMillisDisplay(cookTime))
         (activity as AppCompatActivity).supportActionBar?.title = title
      }
   }
}
