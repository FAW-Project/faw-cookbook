/*
 * RecipeRepository.kt
 *
 * Copyright 2020 by MicMun
 */
package de.micmun.android.nextcloudcookbook.data

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toFile
import de.micmun.android.nextcloudcookbook.data.model.Recipe
import de.micmun.android.nextcloudcookbook.util.JsonRecipeParser
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.stream.Collectors

/**
 * Repository with the recipe data.
 *
 * @author MicMun
 * @version 1.0, 20.04.20
 */
class RecipeRepository {
   private val _recipeList = mutableListOf<Recipe>()
   private val _recipeMap = mutableMapOf<Long, Recipe>()

   companion object {
      @Volatile
      private var INSTANCE: RecipeRepository? = null

      fun getInstance(): RecipeRepository {
         synchronized(this) {
            var instance = INSTANCE

            if (instance == null) {
               instance = RecipeRepository()
               INSTANCE = instance
            }

            return instance
         }
      }
   }

   fun getRecipeWithId(id: Long) = _recipeMap[id]

   /**
    * Reads all recipes from directory.
    */
   fun getAllRecipes(path: String): List<Recipe> {
      val dir = File(path)

      if (dir.exists()) {
         val subdirs = dir.listFiles()
         Log.d("RecipeRepository", "Anzahl subdirs: ${subdirs?.size}")

         var id: Long = 1

         subdirs?.forEach { sd ->
            if (sd.exists() && sd.isDirectory) {
               val jsonFile = File(sd, sd.name.trim() + ".json")
               val thumbFile = File(sd, "thumb.jpg")
               val fullFile = File(sd, "full.jpg")

               if (jsonFile.exists()) {
                  val recipe = readRecipe(Uri.fromFile(jsonFile))
                  recipe.thumbImage = if (thumbFile.exists()) Uri.fromFile(thumbFile) else null
                  recipe.imageUrl = if (fullFile.exists()) fullFile.absolutePath else ""
                  recipe.recipeId = id++
                  _recipeList.add(recipe)
                  _recipeMap[recipe.recipeId] = recipe
               }
            }
         }
      }
      return _recipeList
   }

   /**
    * Reads a recipe and parse it to the Recipe data model.
    *
    * @param path Path of the file.
    * @return Recipe.
    */
   private fun readRecipe(path: Uri): Recipe {
      val reader = BufferedReader(FileReader(path.toFile()))

      val json: String

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
         json = reader.lines().collect(Collectors.joining("\n"))
      } else {
         val strBuilder = StringBuilder()
         var line = reader.readLine()

         while (line != null) {
            strBuilder.append(line)
            line = reader.readLine()
         }

         json = strBuilder.toString()
      }

      return JsonRecipeParser(json).parse()
   }
}
