/*
 * MainActivity.kt
 *
 * Copyright 2020 by MicMun
 */
package de.micmun.android.nextcloudcookbook.ui

import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.databinding.DataBindingUtil
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.listener.multi.BaseMultiplePermissionsListener
import de.micmun.android.nextcloudcookbook.MainApplication
import de.micmun.android.nextcloudcookbook.R
import de.micmun.android.nextcloudcookbook.data.CategoryFilter
import de.micmun.android.nextcloudcookbook.data.RecipeFilter
import de.micmun.android.nextcloudcookbook.data.SortValue
import de.micmun.android.nextcloudcookbook.databinding.ActivityMainBinding
import de.micmun.android.nextcloudcookbook.nextcloudapi.Accounts
import de.micmun.android.nextcloudcookbook.settings.PreferenceData
import de.micmun.android.nextcloudcookbook.ui.recipelist.RecipeListFragmentDirections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.ui.UiExceptionManager
import com.nextcloud.android.sso.exceptions.NoCurrentAccountSelectedException
import com.nextcloud.android.sso.exceptions.NextcloudFilesAppAccountNotFoundException
import com.nextcloud.android.sso.helper.SingleAccountHelper
import com.nextcloud.android.sso.model.SingleSignOnAccount
import de.micmun.android.nextcloudcookbook.services.sync.SyncService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

import androidx.appcompat.widget.SearchView.OnQueryTextListener
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException
import de.micmun.android.nextcloudcookbook.ui.recipelist.RecipeSearchCallback


/**
 * Main Activity of the app.
 *
 * @author MicMun
 * @version 1.8, 27.11.21
 */
class MainActivity : AppCompatActivity() {
   private lateinit var binding: ActivityMainBinding
   private lateinit var drawerLayout: DrawerLayout
   private lateinit var currentSettingViewModel: CurrentSettingViewModel
   private lateinit var preferenceData: PreferenceData

   private var mRecipeSearchCallback: RecipeSearchCallback? = null

   override fun onCreate(savedInstanceState: Bundle?) {
      preferenceData = PreferenceData.getInstance()

      if (!preferenceData.isInitializedSync()) {
         lifecycleScope.launch(Dispatchers.Main) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)
            preferenceData.migrateSharedPreferences(sharedPreferences)
            if (!preferenceData.isInitializedSync()) {
               preferenceData.setSort(SortValue.NAME_A_Z.sort)
               preferenceData.setTheme(2)
               preferenceData.setStorageAccessed(false)
            }
         }
      }


      setTheme(R.style.AppTheme_Light)
      // apply theme
      when (preferenceData.getThemeSync()) {
         0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
         1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
         2 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
      }

      super.onCreate(savedInstanceState)
      binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

      // toolbar
      setupToolbars()

      // drawer layout
      drawerLayout = binding.drawerLayout

      // settings
      val factory = CurrentSettingViewModelFactory(MainApplication.AppContext)
      currentSettingViewModel =
         ViewModelProvider(MainApplication.AppContext, factory).get(CurrentSettingViewModel::class.java)
      binding.navView.setNavigationItemSelectedListener { item ->
         val currentCat = when (item.itemId) {
            R.id.menu_all_categories -> CategoryFilter(CategoryFilter.CategoryFilterOption.ALL_CATEGORIES)
            R.id.menu_uncategorized -> CategoryFilter(CategoryFilter.CategoryFilterOption.UNCATEGORIZED)
            else -> CategoryFilter(CategoryFilter.CategoryFilterOption.CATEGORY, item.title.toString())
         }
         handleNavigationDrawerSelection(item.itemId)
         currentSettingViewModel.setNewCategory(currentCat)
         drawerLayout.closeDrawers()
         true
      }

      var context = this
      with(binding) {
         currentSettingViewModel =
           ViewModelProvider(MainApplication.AppContext, factory).get(CurrentSettingViewModel::class.java)
         navView.setNavigationItemSelectedListener { item ->
           val currentCat = when (item.itemId) {
              R.id.menu_all_categories -> CategoryFilter(CategoryFilter.CategoryFilterOption.ALL_CATEGORIES)
              R.id.menu_uncategorized -> CategoryFilter(CategoryFilter.CategoryFilterOption.UNCATEGORIZED)
              else -> CategoryFilter(CategoryFilter.CategoryFilterOption.CATEGORY, item.title.toString())
           }
           handleNavigationDrawerSelection(item.itemId)
           currentSettingViewModel.setNewCategory(currentCat)
           drawerLayout.closeDrawers()
           true
        }


         searchText.setOnClickListener {
            searchToolbar.visibility = View.VISIBLE
            normalToolbar.visibility = View.GONE
            searchbar.isIconified = false
         }

         backButton.setOnClickListener{
            searchToolbar.visibility = View.GONE
            normalToolbar.visibility = View.VISIBLE
         }

         sortorder.setOnClickListener{
            mRecipeSearchCallback?.showSortSelector()
         }

         accountSwitcher.setOnClickListener{
            Accounts(context).openAccountChooser(context)
         }

         searchbar.setOnQueryTextListener(object : OnQueryTextListener,
            android.widget.SearchView.OnQueryTextListener {

            override fun onQueryTextChange(qString: String): Boolean {
               search(qString)
               return true
            }
            override fun onQueryTextSubmit(qString: String): Boolean {
               search(qString)
               return true
            }
         })
      }

      // permission for storage
      lifecycleScope.launchWhenCreated {
         currentSettingViewModel.storageAccessed.collect { access ->
            if (!access) {
               storagePermissions()
            }
         }
      }

      handleIntent(intent)
      SyncService.startServiceScheduling(baseContext)
   }

   fun handleNavigationDrawerSelection(item: Int){
      val navHostFragment = supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
      val navController = navHostFragment.findNavController()
      when (item) {
         R.id.menu_search_extended -> {
            navController.navigate(R.id.searchFormFragment)
            showToolbar(true, false)
         }
         R.id.app_import_recipe -> {
            navController.navigate(R.id.downloadFormFragment)
            showToolbar(true, false)
         }
         R.id.app_settings -> {
            navController.navigate(R.id.preferenceFragment)
            showToolbar(true, false)
         }
         R.id.menu_all_categories, R.id.menu_uncategorized -> {
            navController.navigate(R.id.recipeListFragment)
            showToolbar(true, true)
         }
         else -> {
            navController.navigate(R.id.recipeListFragment)
            showToolbar(true, true)
         }
      }

   }

   override fun onNewIntent(intent: Intent?) {
      super.onNewIntent(intent)
      handleIntent(intent)
   }

   fun getMenu(): Menu {
      return binding.navView.menu
   }

   override fun onSupportNavigateUp(): Boolean {
      val navController = this.findNavController(R.id.navHostFragment)
      return NavigationUI.navigateUp(navController, drawerLayout)
   }

   private fun setupToolbars() {
      binding.menuButton.setOnClickListener { v ->
         binding.drawerLayout.openDrawer(
            GravityCompat.START
         )
      }
   }

   /**
    * Handle intent for search.
    *
    * @param intent incoming intent.
    */
   private fun handleIntent(intent: Intent?) {
      if (Intent.ACTION_SEARCH == intent?.action) {
         val query = intent.getStringExtra(SearchManager.QUERY)
         if (query != null) {
            search(query)
         }
      }
   }

   private fun search(query: String) {
      val filter = RecipeFilter(RecipeFilter.QueryType.QUERY_NAME, query)
      mRecipeSearchCallback?.searchRecipes(filter)
   }

   /**
    * Handles the default storage permissions.
    */
   private fun storagePermissions() {
      val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
         listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
      } else {
         listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
      }

      Dexter.withContext(this)
         .withPermissions(permissions)
         .withListener(object : BaseMultiplePermissionsListener() {
            override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
               if (report?.areAllPermissionsGranted() == true) {
                  currentSettingViewModel.setStorageAccess(true)
               } else {
                  currentSettingViewModel.setStorageAccess(false)
                  Snackbar.make(binding.root, "No storage access!", Snackbar.LENGTH_LONG).show()
               }
            }
         })
         .check()
   }

   fun showToolbar(showToolbar: Boolean, showSearch: Boolean = true) {
      if(showToolbar) {
         binding.appBar.visibility = View.VISIBLE
      } else {
         binding.appBar.visibility = View.GONE
      }
      if(showSearch) {
         binding.searchText.visibility = View.VISIBLE
      } else {
         binding.searchText.visibility = View.INVISIBLE
      }


   }

   fun setRecipeSearchCallback(callback: RecipeSearchCallback?) {
      mRecipeSearchCallback = callback
   }

   override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
      super.onActivityResult(requestCode, resultCode, data)
      try {
         AccountImporter.onActivityResult(
            requestCode, resultCode, data, this
         ) { account ->
            val context = applicationContext

            // As this library supports multiple accounts we created some helper methods if you only want to use one.
            // The following line stores the selected account as the "default" account which can be queried by using
            // the SingleAccountHelper.getCurrentSingleSignOnAccount(context) method
            SingleAccountHelper.setCurrentAccount(context, account.name)

            // Get the "default" account
            var ssoAccount: SingleSignOnAccount? = null
            try {
               ssoAccount = SingleAccountHelper.getCurrentSingleSignOnAccount(context)
            } catch (e: NextcloudFilesAppAccountNotFoundException) {
               UiExceptionManager.showDialogForException(context, e)
            } catch (e: NoCurrentAccountSelectedException) {
               UiExceptionManager.showDialogForException(context, e)
            }
            SingleAccountHelper.setCurrentAccount(context, ssoAccount!!.name)
            var username = ssoAccount!!.name
            val file = File(this.filesDir, "recipes/$username/")
            val prefs = PreferenceData.getInstance()
            runBlocking {
               withContext(Dispatchers.IO) {
                  prefs.setRecipeDir(file.absolutePath)
               }
            }
            startService(Intent(this, SyncService::class.java))
         }
      } catch (e: AccountImportCancelledException) {}
   }

   override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<String?>,
      grantResults: IntArray
   ) {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults)
      AccountImporter.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
   }
}
