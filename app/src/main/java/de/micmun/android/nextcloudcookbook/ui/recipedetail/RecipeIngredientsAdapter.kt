/*
 * RecipeIngredientsAdapter.kt
 *
 * Copyright 2020 by MicMun
 */
package de.micmun.android.nextcloudcookbook.ui.recipedetail

import android.annotation.SuppressLint
import android.graphics.Paint
import android.text.Html
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.utils.MDUtil.textChanged
import de.micmun.android.nextcloudcookbook.databinding.IngredientsItemBinding
import de.micmun.android.nextcloudcookbook.databinding.TabIngredientsBinding
import de.micmun.android.nextcloudcookbook.db.model.DbIngredient

/**
 * Adapter for recipe ingredients.
 *
 * @author MicMun
 * @version 1.2, 31.05.21
 */
@SuppressLint("NotifyDataSetChanged")
class RecipeIngredientsAdapter(
   private val tabBinding: TabIngredientsBinding,
   private val baseYield: Float,
   private val ingredients: List<DbIngredient>) :
   RecyclerView.Adapter<RecipeIngredientsAdapter.IngredientsViewHolder>() {

   init {
      setYieldInput(baseYield)
      tabBinding.yieldInput.textChanged { notifyDataSetChanged() }
      tabBinding.yieldMinus.setOnClickListener { setYieldInput((getYieldInput() - 1).coerceAtLeast(1f)) }
      tabBinding.yieldPlus.setOnClickListener { setYieldInput(getYieldInput() + 1) }
   }

   override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientsViewHolder {
      return IngredientsViewHolder.from(parent)
   }

   override fun getItemCount() = ingredients.size

   override fun onBindViewHolder(holder: IngredientsViewHolder, position: Int) {
      var ingredient = ingredients[position].ingredient.trim()
      val factor = getYieldInput() / baseYield
      if (factor != 1f) {
         // Replaces the first decimal found with a scaled value. This is usually the amount,
         // but there may be exceptions. We highlight it in bold print to make the user aware.
         ingredient = scaleIngredientAmount(amountRegexString, ingredient, factor)
         // Replace the decimal after a '-' with a scaled value, if there is a range of amounts.
         ingredient = scaleIngredientAmount("-\\s?$amountRegexString", ingredient, factor)
      }
      holder.bind(ingredient)
   }

   class IngredientsViewHolder private constructor(val binding: IngredientsItemBinding) :
      RecyclerView.ViewHolder(binding.root) {
      /**
       * Binds the data to the views.
       *
       * @param ingredient Ingredient String to show in view.
       */
      fun bind(ingredient: String) {
         binding.ingredientsItemText.text = Html.fromHtml(ingredient)
         binding.ingredientsItemText.setOnClickListener {
            binding.ingredientsItemText.paintFlags =
               binding.ingredientsItemText.paintFlags.xor(Paint.STRIKE_THRU_TEXT_FLAG)
         }
         // we must reset the flag, it seems the TextView can be reused for a different item
         binding.ingredientsItemText.paintFlags =
            binding.ingredientsItemText.paintFlags.and(Paint.STRIKE_THRU_TEXT_FLAG.inv())
         binding.executePendingBindings()
      }

      companion object {
         fun from(parent: ViewGroup): IngredientsViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = IngredientsItemBinding.inflate(layoutInflater, parent, false)
            return IngredientsViewHolder(binding)
         }
      }
   }

   private fun getYieldInput(): Float {
      return try {
         tabBinding.yieldInput.text.toString().toFloat()
      } catch (e: NumberFormatException) {
         baseYield
      }
   }

   private fun setYieldInput(yieldAmount: Float) {
      tabBinding.yieldInput.setText(prettyString(yieldAmount))
   }

   private fun prettyString(f: Float): String {
      // 1f.toString() -> "1.0", but just "1" looks better in the ingredient list and rounded to two decimals, too
      val tmp = "%.2f".format(f).replace(',', '.').toFloat()
      return if (tmp.toInt().toFloat() == tmp) tmp.toInt().toString() else tmp.toString()
   }

   private fun scaleIngredientAmount(regexStr: String, ingredient: String, factor: Float) : String {
      return regexStr.toRegex().find(ingredient)?.let {
         val amount = it.groups[1]!! // don't include the "-\s?" on the second replace
         val fraction = it.groups[4]?.value // unicode fraction
         val newValue = factor *
                 if (fraction != null && fraction.isNotEmpty()) { // e.g. "1 ½", "¾"
                    val integer = it.groups[3]?.value ?: ""
                    amount.value.replace(fraction, fractionsMap[fraction]!!)
                       .replace(integer, integer.trim())
                 } else { // e.g. "4.5", "2,7"
                    amount.value.replace(',', '.')
                 }.toFloat()
         ingredient.replaceRange(amount.range, "<b>${prettyString(newValue)}</b>")
      } ?: ingredient
   }

   companion object
   {
      private val fractionsMap = mapOf(
         "½" to ".5",
         "⅓" to ".3333",
         "⅕" to ".2",
         "⅙" to ".1666",
         "⅛" to ".125",
         "⅔" to ".6666",
         "⅖" to ".4",
         "⅚" to ".8333",
         "⅜" to ".375",
         "¾" to ".75",
         "⅗" to ".6",
         "⅝" to ".625",
         "⅞" to ".875",
         "⅘" to ".8",
         "¼" to ".25",
         "⅐" to ".1429",
         "⅑" to ".1111",
         "⅒" to ".1"
      )

      // e.g. "4.5", "2,7", "1 ½", "¾"
      private val amountRegexString =
         "(((\\d+\\s?)?([${fractionsMap.keys.joinToString(separator = "")}]))|(\\d+([.,]\\d+)?))"
   }
}
