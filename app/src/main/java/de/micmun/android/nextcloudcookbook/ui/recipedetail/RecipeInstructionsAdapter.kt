/*
 * RecipeIngredientsAdapter.kt
 *
 * Copyright 2020 by MicMun
 */
package de.micmun.android.nextcloudcookbook.ui.recipedetail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.micmun.android.nextcloudcookbook.R
import de.micmun.android.nextcloudcookbook.databinding.InstructionsItemBinding
import de.micmun.android.nextcloudcookbook.db.model.DbInstruction

/**
 * Adapter for recipe ingredients.
 *
 * @author MicMun
 * @version 1.2, 01.05.21
 */
class RecipeInstructionsAdapter(private val instructions: List<DbInstruction>) :
   RecyclerView.Adapter<RecipeInstructionsAdapter.InstructionsViewHolder>() {

   override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InstructionsViewHolder {
      return InstructionsViewHolder.from(parent)
   }

   override fun getItemCount() = instructions.size

   override fun onBindViewHolder(holder: InstructionsViewHolder, position: Int) {
      val ingredient = instructions[position].instruction.trim()
      holder.bind(position + 1, ingredient)
   }

   class InstructionsViewHolder private constructor(val binding: InstructionsItemBinding) :
      RecyclerView.ViewHolder(binding.root) {
      /**
       * Binds the data to the views.
       *
       * @param instruction Instruction String to show in view.
       */
      fun bind(counter: Int, instruction: String) {
         binding.instruction = instruction
         binding.counter = counter.toString()
         //listener to mark as done or undone
         binding.instructionListPosition.setOnClickListener {
            if (R.id.instructionDoneSymbol == binding.instructionsListSwitcher.nextView.id)
               binding.instructionsListSwitcher.showNext()
         }
         binding.instructionDoneSymbol.setOnClickListener {
            if (R.id.instructionListPosition == binding.instructionsListSwitcher.nextView.id)
               binding.instructionsListSwitcher.showNext()
         }
         binding.executePendingBindings()
      }

      companion object {
         fun from(parent: ViewGroup): InstructionsViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = InstructionsItemBinding.inflate(layoutInflater, parent, false)
            return InstructionsViewHolder(binding)
         }
      }
   }
}
