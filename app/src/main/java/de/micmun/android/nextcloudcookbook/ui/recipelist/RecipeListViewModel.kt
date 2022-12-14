/*
 * RecipeViewModel.kt
 *
 * Copyright 2020 by MicMun
 */
package de.micmun.android.nextcloudcookbook.ui.recipelist

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import de.micmun.android.nextcloudcookbook.data.CategoryFilter
import de.micmun.android.nextcloudcookbook.data.RecipeFilter
import de.micmun.android.nextcloudcookbook.data.SortValue
import de.micmun.android.nextcloudcookbook.db.DbRecipeRepository
import de.micmun.android.nextcloudcookbook.db.model.DbRecipePreview
import de.micmun.android.nextcloudcookbook.json.JsonRecipeRepository
import de.micmun.android.nextcloudcookbook.json.model.Recipe
import de.micmun.android.nextcloudcookbook.util.Recipe2DbRecipeConverter
import kotlinx.coroutines.*
import java.io.File
import java.util.stream.Collectors

/**
 * ViewModel for list of recipes.
 *
 * @author MicMun
 * @version 2.0, 29.05.22
 */
class RecipeListViewModel(private val app: Application) : AndroidViewModel(app) {
   // coroutines
   private var viewModelJob = Job()

   private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

   private val recipeRepository = DbRecipeRepository.getInstance(app)
   val categories = recipeRepository.getCategories()

   // on updating
   val isUpdating = MutableLiveData(false)
   val isLoaded = MutableLiveData(false)

   private var recipeDir: String = ""

   // sorting and category
   private var sort: SortValue = SortValue.NAME_A_Z
   private var filter: RecipeFilter? = null
   private var catFilter: CategoryFilter = CategoryFilter(CategoryFilter.CategoryFilterOption.ALL_CATEGORIES)

   // navigate to recipe
   private val _navigateToRecipe = MutableLiveData<Long?>()
   val navigateToRecipe
      get() = _navigateToRecipe

   fun onRecipeClicked(id: Long) {
      _navigateToRecipe.value = id
   }

   fun onRecipeNavigated() {
      _navigateToRecipe.value = null
   }

   fun getRecipes(): LiveData<List<DbRecipePreview>> {
      var recipes: LiveData<List<DbRecipePreview>>

      runBlocking(Dispatchers.IO) {
         recipes =
            if (filter != null) {
               Log.e("TAG", "SEARCH ! $filter")
               recipeRepository.filterAll(sort, filter!!)
            } else {
               if (catFilter.type == CategoryFilter.CategoryFilterOption.ALL_CATEGORIES && sort == SortValue.NAME_A_Z) {
                  recipeRepository.getAllRecipePreviews()
               } else if (catFilter.type == CategoryFilter.CategoryFilterOption.ALL_CATEGORIES) {
                  recipeRepository.sort(sort)
               } else if (catFilter.type == CategoryFilter.CategoryFilterOption.UNCATEGORIZED) {
                  recipeRepository.filterUncategorized(sort, filter)
               } else {
                  recipeRepository.filterCategory(sort, catFilter.name)
               }
            }
      }
      return recipes
   }

   // read recipes
   fun initRecipes(path: String = "", hidden: Boolean = false) {
      if (path.isNotEmpty()) {
         recipeDir = path
      }
      val dir = if (path.isEmpty()) recipeDir else path

      if (dir.isEmpty()) {
         if (!hidden) isUpdating.postValue(false)
         return
      }

      if (!hidden) isUpdating.postValue(true)

      uiScope.launch {
         val list = getRecipesFromRepo(dir)
         val dbList = list.stream()
            .map { Recipe2DbRecipeConverter(it).convert() }
            .collect(Collectors.toList())
         recipeRepository.insertAll(dbList)

         isLoaded.postValue(true)
         if (!hidden) isUpdating.postValue(false)
      }
   }

   private suspend fun getRecipesFromRepo(path: String): List<Recipe> {

      return withContext(Dispatchers.IO) {

         val repositoryRecipes = JsonRecipeRepository.getInstance()
            .getAllRecipes(app, path, recipeRepository.getAllFileInfos())

         for (recipeInfo in recipeRepository.getAllFileInfos()) {
            if (!File(recipeInfo.filePath).exists()) {
               var filename = recipeInfo.filePath.substring(0, recipeInfo.filePath.lastIndexOf("/"))
               filename = filename.substring(filename.lastIndexOf("/") + 1, filename.length)
               recipeRepository.deleteRecipe(filename)
            }
         }
         repositoryRecipes
      }
   }

   // category filter
   fun filterRecipesByCategory(catFilter: CategoryFilter?) {
      if (catFilter == null) {
         this.catFilter = CategoryFilter(CategoryFilter.CategoryFilterOption.ALL_CATEGORIES)
      } else {
         this.catFilter = catFilter
      }
   }

   fun sortList(sort: SortValue) {
      this.sort = sort
   }

   fun search(filter: RecipeFilter?) {
      this.filter = filter
   }

   override fun onCleared() {
      super.onCleared()
      viewModelJob.cancel()
   }

   fun getRecipeDir(): String {
      return recipeDir
   }
}

class RecipeListViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
   @Suppress("UNCHECKED_CAST")
   override fun <T : ViewModel> create(modelClass: Class<T>): T {
      if (modelClass.isAssignableFrom(RecipeListViewModel::class.java)) {
         return RecipeListViewModel(application) as T
      }
      throw IllegalArgumentException("Unknown ViewModel class")
   }
}
