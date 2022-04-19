package de.micmun.android.nextcloudcookbook.nextcloudapi

import android.net.Uri
import android.util.Log
import com.nextcloud.android.sso.aidl.NextcloudRequest
import com.nextcloud.android.sso.api.NextcloudAPI
import com.nextcloud.android.sso.exceptions.NextcloudHttpRequestFailedException
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class CookbookAPI(private val mApi: NextcloudAPI) {

   companion object {
      private const val API_RECIPES = "/index.php/apps/cookbook/api/recipes"
      private val TAG = CookbookAPI::class.toString()
   }

   fun getRecipes(): ArrayList<String> {
      val result = ArrayList<String>()

      val nextcloudRequest: NextcloudRequest = NextcloudRequest.Builder()
         .setMethod("GET")
         .setUrl(Uri.encode(API_RECIPES, "/"))
         .build()

      try {
         val istream = mApi.performNetworkRequestV2(nextcloudRequest)
         val r = BufferedReader(InputStreamReader(istream.body))
         var json = ""

         var line: String?
         while (r.readLine().also { line = it } != null) {
            json += line + '\n'
         }
         val root = JSONArray(json)
         for (i in 0 until root.length()) {
            result.add(root.getJSONObject(i).toString())
         }

      } catch (e: Exception) {
         e.printStackTrace()
      }
      return result
   }

   fun getRecipe(id: String): String {
      var result = ""

      val nextcloudRequest: NextcloudRequest = NextcloudRequest.Builder()
         .setMethod("GET")
         .setUrl(Uri.encode("$API_RECIPES/$id", "/"))
         .build()

      try {
         val istream = mApi.performNetworkRequestV2(nextcloudRequest)
         val r = BufferedReader(InputStreamReader(istream.body))

         var line: String?
         while (r.readLine().also { line = it } != null) {
            result += line + '\n'
         }


      } catch (e: Exception) {
         e.printStackTrace()
      }
      return result
   }

   fun getImage(url: String): ByteArray? {

      //val parameters = Collections.singleton(QueryParam("size", size))
      val nextcloudRequest: NextcloudRequest = NextcloudRequest.Builder()
         .setMethod("GET")
         .setUrl(url)
         .build()

      Log.e(TAG, nextcloudRequest.url)
      try {
         val istream = mApi.performNetworkRequestV2(nextcloudRequest)
         val byteArray: ByteArray?
         try {
            byteArray = istream.body.readBytes()

            if (String(byteArray).startsWith("<svg")) {
               Log.d(TAG, "Remote Image does not exist, do not store.")
               return null
            }

            return byteArray
         } catch (e: Exception) {
            Log.e(TAG, "getImage: ${e.javaClass}: ${e.message}")
            e.printStackTrace()
         }

      } catch (e: NextcloudHttpRequestFailedException) {
         Log.e(TAG, "Nextcloud Http Exception: ${e.message}")
      } catch (e: Exception) {
         Log.e(TAG, "getImage: ${e.javaClass}: ${e.message}")
         e.printStackTrace()
      }
      return null
   }

   fun createRecipe(recipe: JSONObject): String? {
      //val parameters = Collections.singleton(QueryParam("size", size))
      val nextcloudRequest: NextcloudRequest = NextcloudRequest.Builder()
         .setMethod("POST")
         .setRequestBody(recipe.toString())
         .setUrl(API_RECIPES)
         .build()

      Log.e(TAG, nextcloudRequest.url)
      try {
         val istream = mApi.performNetworkRequestV2(nextcloudRequest)
         val byteArray: ByteArray?
         try {
            byteArray = istream.body.readBytes()
            Log.d(TAG, "Result: " + String(byteArray))
            return String(byteArray)
         } catch (e: IOException) {
            e.printStackTrace()
         }

      } catch (e: Exception) {
         e.printStackTrace()
      }
      return null
   }
}