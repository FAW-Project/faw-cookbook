/*
 * PreferenceData.kt
 *
 * Copyright 2021 by MicMun
 */
package de.micmun.android.nextcloudcookbook.settings

import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import de.micmun.android.nextcloudcookbook.MainApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Datastore for settings.
 *
 * @author MicMun
 * @version 1.0, 27.11.21
 */
class PreferenceData private constructor() {
   private val isInitializedKey = booleanPreferencesKey(Pref.IS_INIT)
   private val recipeDirKey = stringPreferencesKey(Pref.RECIPE_DIR)
   private val themeKey = intPreferencesKey(Pref.THEME)
   private val sortKey = intPreferencesKey(Pref.SORT)
   private val isStorageAccessedKey = booleanPreferencesKey(Pref.STORAGE_ACCESS)
   private val isSyncServiceEnabled = booleanPreferencesKey(Pref.SYNC_SERVICE)

   companion object {
      @Volatile
      private var INSTANCE: PreferenceData? = null

      fun getInstance(): PreferenceData {
         synchronized(PreferenceData::class) {
            var instance = INSTANCE

            if (instance == null) {
               instance = PreferenceData()
               INSTANCE = instance
            }

            return instance
         }
      }
   }

   fun isInitializedSync(): Boolean {
      var isInit = false

      runBlocking {
         isInit = MainApplication.AppContext.dataStore.data
            .map { preferences ->
               preferences[isInitializedKey] ?: false
            }
            .first()
      }

      return isInit
   }

   fun getRecipeDir(): Flow<String> {
      return MainApplication.AppContext.dataStore.data
         .map { preferences ->
            preferences[recipeDirKey] ?: ""
         }
   }

   fun getTheme(): Flow<Int> {
      return MainApplication.AppContext.dataStore.data
         .map { preferences ->
            preferences[themeKey] ?: 0
         }
   }

   fun getThemeSync(): Int {
      var theme = 0

      runBlocking {
         theme = MainApplication.AppContext.dataStore.data
            .map { preferences ->
               preferences[themeKey] ?: 0
            }
            .first()
      }

      return theme
   }

   fun getSort(): Flow<Int> {
      return MainApplication.AppContext.dataStore.data
         .map { preferences ->
            preferences[sortKey] ?: 0
         }
   }

   fun isStorageAccessed(): Flow<Boolean> {
      return MainApplication.AppContext.dataStore.data
         .map { preferences ->
            preferences[isStorageAccessedKey] ?: false
         }
   }

   fun isSyncServiceEnabled(): Boolean {
      var enabled = true
      runBlocking {
         enabled = MainApplication.AppContext.dataStore.data
            .map { preferences ->
               preferences[isSyncServiceEnabled] ?: false
            }
            .first()
      }

      return enabled
   }

   suspend fun setInitialised(isInit: Boolean) {
      MainApplication.AppContext.dataStore.edit { preferences ->
         preferences[isInitializedKey] = isInit
      }
   }

   suspend fun setStorageAccessed(isStorageAccessed: Boolean) {
      MainApplication.AppContext.dataStore.edit { preferences ->
         preferences[isStorageAccessedKey] = isStorageAccessed
      }
   }

   suspend fun setTheme(theme: Int) {
      MainApplication.AppContext.dataStore.edit { preferences ->
         preferences[themeKey] = theme
      }
   }

   suspend fun setSort(sort: Int) {
      MainApplication.AppContext.dataStore.edit { preferences ->
         preferences[sortKey] = sort
      }
   }

   suspend fun setRecipeDir(recipeDir: String) {
      MainApplication.AppContext.dataStore.edit { preferences ->
         preferences[recipeDirKey] = recipeDir
      }
   }

   /**
    * Migrates the current sharedPreferences to Datastore.
    *
    * @param sharedPreferences SharedPreferences to migrate.
    */
   suspend fun migrateSharedPreferences(sharedPreferences: SharedPreferences) {
      val prefs = sharedPreferences.all
      prefs.keys.forEach { k ->
         when (k) {
            Pref.RECIPE_DIR -> setRecipeDir(prefs[k] as String)
            Pref.STORAGE_ACCESS -> setStorageAccessed(prefs[k] as Boolean)
            Pref.THEME -> setTheme(prefs[k] as Int)
            Pref.SORT -> setSort(prefs[k] as Int)
         }
      }
      if (prefs.isNotEmpty()) { // if true, migration, else first start for the app or migrated yet
         sharedPreferences.edit().clear().apply()
         setInitialised(true)
      }
   }
}
