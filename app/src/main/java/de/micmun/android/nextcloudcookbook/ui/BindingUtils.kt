/*
 * BindingUtils.kt
 *
 * Copyright 2020 by MicMun
 */
package de.micmun.android.nextcloudcookbook.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.anggrayudi.storage.extension.launchOnUiThread
import com.anggrayudi.storage.file.getAbsolutePath
import de.micmun.android.nextcloudcookbook.R
import de.micmun.android.nextcloudcookbook.db.model.DbRecipe
import de.micmun.android.nextcloudcookbook.db.model.DbRecipePreview
import de.micmun.android.nextcloudcookbook.settings.PreferenceData
import de.micmun.android.nextcloudcookbook.util.DurationUtils
import de.micmun.android.nextcloudcookbook.util.StorageManager
import java.io.File
import java.net.URLDecoder
import java.util.stream.Collectors

/**
 * Utilities for binding data to view.
 *
 * @author MicMun
 * @version 2.6, 23.04.22
 */

// Overview list
@BindingAdapter("recipeImage")
fun ImageView.setRecipeImage(item: DbRecipePreview?) {
   item?.run {
      if (thumbImageUrl.isEmpty()) {
         setImageURI(null)
      } else {

         // required, because internal storage may contain special chars that are
         // encoded and will result in unreadable images
         var img = item.thumbImageUrl
         if (img.startsWith("file://${context.filesDir}")) {
            img = img.replace("file://", "")
            img = URLDecoder.decode(img, "UTF-8")
            setImageURI(Uri.fromFile(File(img)))
            return
         }

         val thumbImage = StorageManager.getImageFromString(context, img)
         thumbImage?.run {
            try {
               if (thumbImage.canRead())
                  setImageURI(uri)
               else
                  setImageURI(Uri.parse(thumbImage.getAbsolutePath(context)))
            } catch (e: SecurityException) {
               launchOnUiThread {
                  PreferenceData.getInstance().setStorageAccessed(false)
               }
            }
         }
      }
   }
}

@BindingAdapter("recipeName")
fun TextView.setRecipeName(item: DbRecipePreview?) {
   item?.let { text = it.name }
}

@BindingAdapter("recipeDescription")
fun TextView.setRecipeDesc(item: DbRecipe?) {
   item?.let { text = it.recipeCore.description }
}

@BindingAdapter("recipeDescription")
fun TextView.setRecipeDesc(item: DbRecipePreview?) {
   item?.let { text = it.description }
}

// Detail view
@BindingAdapter("recipeHeaderImage")
fun ImageView.setRecipeHeaderImage(item: DbRecipe?) {
   item?.run {
      if (recipeCore.fullImageUrl.isEmpty()) {
         setImageURI(null)
      } else {
         setPadding(0, 0, 0, 0)
         // required, because internal storage may contain special chars that are
         // encoded and will result in unreadable images
         var img = recipeCore.fullImageUrl
         if (img.startsWith("file://${context.filesDir}")) {
            img = img.replace("file://", "")
            img = URLDecoder.decode(img, "UTF-8")
            setImageURI(Uri.fromFile(File(img)))
            return
         }
         val fullImage = StorageManager.getImageFromString(context, recipeCore.fullImageUrl)
         fullImage?.run {
            try {
               if (fullImage.canRead())
                  setImageURI(uri)
               else
                  setImageURI(Uri.parse(fullImage.getAbsolutePath(context)))
            } catch (e: SecurityException) {
               launchOnUiThread {
                  PreferenceData.getInstance().setStorageAccessed(false)
               }
            }
         }
      }
   }
}

@BindingAdapter("recipePublishedDate")
fun TextView.setPublishedDate(item: DbRecipe?) {
   item?.let {
      val date = if (it.recipeCore.datePublished.isEmpty()) "-" else it.recipeCore.datePublished
      text = resources.getString(R.string.text_date_published, date)
   }
}

@BindingAdapter("recipePrepTime")
fun TextView.setPrepTime(item: DbRecipe?) {
   item?.let {
      text = if (it.recipeCore.prepTime.isEmpty()) "" else DurationUtils.formatStringToDuration(
         it.recipeCore.prepTime)
   }
}

@SuppressLint("SetTextI18n")
@BindingAdapter("recipeCookTime")
fun TextView.setCookTime(item: DbRecipe?) {
   item?.let {
      text = if (it.recipeCore.cookTime.isEmpty()) {
         ""
      } else {
         DurationUtils.formatStringToDuration(it.recipeCore.cookTime)
      }

      if (text.isNotEmpty()) {
         // timer icon
         setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_timer, 0, 0, 0)
         // tooltip
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            tooltipText = context.getString(R.string.cooktime_tooltip)
         }
      } else {
         setBackgroundColor(android.R.color.transparent)
      }
   }
}

@BindingAdapter("recipeTotalTime")
fun TextView.setTotalTime(item: DbRecipe?) {
   item?.let {
      text = if (it.recipeCore.totalTime.isEmpty()) "" else DurationUtils.formatStringToDuration(
         it.recipeCore.totalTime)
   }
}

@BindingAdapter("recipeCategories")
fun TextView.setRecipeCategories(item: DbRecipe?) {
   item?.let {
      val categories = if (it.recipeCore.recipeCategory.isEmpty())
         resources.getString(R.string.text_uncategorized)
      else {
         it.recipeCore.recipeCategory
      }
      @Suppress("DEPRECATION")
      text = categories
   }
}

@BindingAdapter("keywords")
fun TextView.setKeywords(item: DbRecipe?) {
   item?.let { recipe ->
      val keywords = if (recipe.keywords.isNullOrEmpty())
         resources.getString(R.string.text_no_keywords)
      else
         recipe.keywords.joinToString(transform = { kw -> kw.keyword })
      @Suppress("DEPRECATION")
      text = keywords
   }
}

@BindingAdapter("recipeAuthor")
fun TextView.setAuthor(item: DbRecipe?) {
   item?.let {
      if (it.recipeCore.author == null) {
         visibility = View.GONE
      } else {
         text = it.recipeCore.author.name
         visibility = View.VISIBLE
      }
   }
}

@BindingAdapter("url")
fun TextView.setUrl(item: DbRecipe?) {
   item?.let {
      if (it.recipeCore.url.isEmpty()) {
         visibility = View.GONE
      } else {
         text = it.recipeCore.url
         visibility = View.VISIBLE
      }
   }
}

@BindingAdapter("recipeYield")
fun TextView.setRecipeYield(item: DbRecipe?) {
   item?.let {
      if (it.recipeCore.recipeYield.isEmpty()) {
         visibility = View.GONE
      } else {
         @Suppress("DEPRECATION")
         text = it.recipeCore.recipeYield
         visibility = View.VISIBLE
      }
   }
}

@BindingAdapter("recipeTools")
fun TextView.setTools(item: DbRecipe?) {
   item?.let { recipe ->
      if (recipe.tool.isNullOrEmpty()) {
         visibility = View.GONE
      } else {
         val tools = recipe.tool.stream().map { it.tool }.collect(Collectors.toList()).joinToString(", ")

         @Suppress("DEPRECATION")
         text = tools
      }
   }
}

// cooking timer
/**
 * Sets the top text for cooktimer text.
 *
 * @param item Recipe data.
 */
@BindingAdapter("topTextTimer")
fun TextView.setTopText(item: DbRecipe?) {
   item?.let {
      text = context.getString(R.string.cooktime_top_text, it.recipeCore.name,
                               DurationUtils.formatStringToDuration(it.recipeCore.cookTime))
   }
}
