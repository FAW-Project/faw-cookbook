package de.micmun.android.nextcloudcookbook.nextcloudapi

import android.content.Context
import android.util.Log
import com.nextcloud.android.sso.api.NextcloudAPI
import de.micmun.android.nextcloudcookbook.util.Filesystem
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

class Sync(private var mContext: Context) {

   private val mAccounts: Accounts = Accounts(mContext)
   private val mAPI: NextcloudAPI = mAccounts.getApiToAccount()!!
   private var mCookbookAPI: CookbookAPI = CookbookAPI(mAPI)
   private var mClosed = false
   private var mFilesystem = Filesystem(mContext)

   companion object {
      private val TAG = Sync::class.toString()
      private const val METADATA = "METADATA"
      const val RECIPE = "recipe.json"
      const val NEW_FILE_MARKER = "NEWFILE"
   }

   /**
    * This function calls downloadRecipes() on the main thread.
    */
   fun synchronizeRecipesAsync() {
      if (mClosed) {
         throw ApiClosedException("The Api has already been closed. Please reinstantiate this class!")
      }

      Executors.newSingleThreadExecutor().submit {
         synchronizeRecipes()
      }
   }

   /**
    * This function starts a bidirectional sync with the cookbook api.
    * It requires that the api is not closed.
    *
    */
   fun synchronizeRecipes() {

      if (mClosed) {
         throw ApiClosedException("The Api has already been closed. Please reinstantiate this class!")
      }

      mCookbookAPI = CookbookAPI(mAccounts.getApiToAccount()!!)
      val remoteList = mCookbookAPI.getRecipes()
      val recipeIds = ArrayList<String>()
      for (recipe in remoteList) {
         val recipeMetadata = JSONObject(recipe)
         val recipeId = recipeMetadata.getString("recipe_id")
         val name = recipeMetadata.getString("name")
         Log.e(TAG, "Pulling Recipe: $name")

         val dateRemote = recipeMetadata.optInt("dateModified", 0)
         val dateLocal = readMetadata(name).optInt("dateModified", 0)

         // if remote date is zero, update anyway because that file is broken.
         // Todo: This breaks when both local and remote recipe have changed. The last one changed will be used.
         // We need to think about if we want that. (When we implement editing)
         if (dateRemote > dateLocal || dateRemote == 0) {
            Log.e(TAG, "Local Recipe out of date: $name")
            try {
               downloadRecipe(recipeMetadata)
            } catch (e: Exception) {
               e.printStackTrace()
               Log.e(TAG, "Error pulling recipe: ${e.message}")
            }
         } else if (dateRemote < dateLocal) {
            Log.e(TAG, "Remote Recipe out of date: $name")
            //update file on the remote
         } else {
            Log.d(TAG, "The Recipe is unchanged. Not syncing.")
         }
         recipeIds.add(recipeId)
      }
      cleanOldRecipes(recipeIds)
      addNewRecipes()
   }

   /**
    * Uploads recipes that have the NEW_FILE_MARKER file present.
    * Only works on the currently selected useraccount
    */
   private fun addNewRecipes() {
      val username = mAccounts.getCurrentAccount()!!.name
      val folders = File(mContext.filesDir, "recipes/$username/")
      val results = folders.listFiles()
      if (results != null) {
         for (folder in results) {
            if (File(folder, NEW_FILE_MARKER).exists()) {
               pushJsonToRemote(folder)
            }
         }
      }
   }

   /**
    * Uploads a recipe.
    * If a recipe already exists, it will retry with (Copy) appended to the recipe name.
    * It moves the recipe to the appropriate folder if (Copy) was appended.
    * Todo: Move Folder if (Copy) appended
    */
   private fun pushJsonToRemote(folder: File, recipeNameAppendix: String = "") {
      val json = JSONObject(mFilesystem.readInternalFile(File(folder, RECIPE)))
      json.put("name", json.get("name$recipeNameAppendix"))
      val id = mCookbookAPI.createRecipe(json)
      if (id != null) {
         json.put("id", id.toInt())
         mFilesystem.writeDataToInternal(
            "recipes/${folder.name}/${getUsername()}/",
            RECIPE,
            json.toString().toByteArray()
         )

         // this seems tedious, but i think the api is not quite ready.
         val remoteList = mCookbookAPI.getRecipes()
         for (recipe in remoteList) {
            val recipeId = JSONObject(recipe).getString("recipe_id")
            if (recipeId == id) {
               mFilesystem.writeDataToInternal(
                  "recipes/${folder.name}/${getUsername()}/",
                  METADATA,
                  JSONObject(recipe).toString().toByteArray()
               )
            }
         }
         // Todo: Reenable before release
         // File(folder, NEW_FILE_MARKER).delete()
      } else {
         Log.e(TAG, "Upload failed! Recipe probably already exists!")
         pushJsonToRemote(folder, "(Copy)")
      }
   }

   /**
    * If a recipe is no longer available on the server, it will be deleted from the filesystem locally.
    *
    * Todo: Think about moving the recipe to a different "DELETED" folder, to allow the user to restore it.
    */
   private fun cleanOldRecipes(recipes: ArrayList<String>) {
      Log.d(TAG, "Clean old recipes")
      val username = mAccounts.getCurrentAccount()!!.name
      val folders = File(mContext.filesDir, "recipes/$username/")
      val results = folders.listFiles()
      if (results != null) {
         for (folder in results) {
            if (!File(folder, NEW_FILE_MARKER).exists()) {
               val metadataContent = mFilesystem.readInternalFile(File(folder, METADATA))
               if (metadataContent.isNotEmpty()) {
                  val id = JSONObject(metadataContent).get("recipe_id")
                  if (!recipes.contains(id)) {
                     Log.d(TAG, "This recipe is to be deleted: ${folder.absolutePath}")
                     mFilesystem.deleteRecursive(folder)
                  }
               } else {
                  Log.e(TAG, "Metadatafile empty while cleaning old recipes!")
               }
            }
         }
      }
   }

   /**
    * Downloads a singular recipe from the remote api. The id is extracted from the
    * recipeMetadata-Json-Object. After that, the appropritate folder is created,
    * and all files downloaded to it.
    */
   private fun downloadRecipe(recipeMetadata: JSONObject) {
      val recipeId = recipeMetadata.getString("recipe_id")
      val name = recipeMetadata.getString("name")
      val recipe = mCookbookAPI.getRecipe(recipeId)
      val username = mAccounts.getCurrentAccount()!!.name

      // use known name, recipe.json, to find recipe again when changes have been made.
      mFilesystem.writeDataToInternal("recipes/$username/$name/", RECIPE, recipe.toByteArray())
      mFilesystem.writeDataToInternal("recipes/$username/$name/", METADATA, recipeMetadata.toString().toByteArray())

      //querys do not work properly, so we use the full image everywhere.
      val imgUrl = "/apps/cookbook/recipes/$recipeId/image"

      //Todo: update this when the upstream bug is fixed in the nextcloud sso-library
      val sizes = arrayOf("thumb", "thumb16", "full")
      for (size in sizes) {
         try {
            val bytes = mCookbookAPI.getImage(imgUrl)
            if (bytes != null) {
               mFilesystem.writeDataToInternal("recipes/$username/$name/", "$size.jpg", bytes)
            }
         } catch (e: Exception) {
            Log.e(TAG, "Error pulling image.$size: ${e.message}")
         }
      }
   }

   private fun getUsername(): String {
      if (mAccounts.getCurrentAccount() == null) {
         Log.e(this::javaClass.toString(), "There is no account, cannot create directory!")
         return "local"
      }
      return mAccounts.getCurrentAccount()!!.name

   }

   private fun readMetadata(name: String): JSONObject {
      val username = mAccounts.getCurrentAccount()!!.name
      val file = File(mContext.filesDir, "recipes/$username/$name/$METADATA")
      val json = mFilesystem.readInternalFile(file)
      if (json == "") {
         return JSONObject("{\"dateModified\": 0}")
      }
      return JSONObject(json)
   }

   fun closeAPI() {
      mClosed = true
      mAPI.stop()
   }
}