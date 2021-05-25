/*
 * TimerViewModel.kt
 *
 * Copyright 2021 by MicMun
 */
package de.micmun.android.nextcloudcookbook.ui.timer

import android.app.Application
import androidx.lifecycle.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ViewModel for cook timer.
 *
 * @author MicMun
 * @version 1.1, 18.05.21
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {
   var totalTime: Long? = null
   private val _currentTime = MutableLiveData<Long>()
   val currentTime: LiveData<Long>
      get() = _currentTime
   private var executor = Executors.newSingleThreadScheduledExecutor()
   private val _state = MutableLiveData<State>()
   val state: LiveData<State>
      get() = _state

   init {
      _currentTime.value = 0L
   }

   internal fun startTimer() {
      executor.shutdownNow()
      executor = Executors.newSingleThreadScheduledExecutor()
      executor.scheduleAtFixedRate({
                                      if (currentTime.value!! == totalTime!!)
                                         stopTimer()
                                      else {
                                         _currentTime.postValue(currentTime.value!! + 100)
                                         _state.postValue(State.RUNNING_POSITIVE)
                                      }
                                   }, 0, 100, TimeUnit.MILLISECONDS)
   }

   internal fun pauseTimer() {
      _state.value = State.PAUSED
      executor.shutdownNow()
   }

   internal fun updateTimer(remain: Long) {
      _currentTime.value = totalTime!! - remain
   }

   internal fun stopTimer() {
      _state.value = State.FINISHED
      _currentTime.value = 0L
      executor.shutdownNow()
   }

   enum class State {
      RUNNING_POSITIVE,
      FINISHED,
      PAUSED
   }

   override fun onCleared() {
      executor.shutdownNow()
   }
}

class TimerViewModelFactory(private val application: Application)
   : ViewModelProvider.Factory {
   override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(TimerViewModel::class.java)) {
         @Suppress("UNCHECKED_CAST")
         return TimerViewModel(application) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class")
   }
}
