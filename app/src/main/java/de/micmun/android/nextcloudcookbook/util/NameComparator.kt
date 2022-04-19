/*
 * NameComparator.kt
 *
 * Copyright 2020 by MicMun
 */
package de.micmun.android.nextcloudcookbook.util

import de.micmun.android.nextcloudcookbook.db.model.DbRecipe

/**
 * Comparator with name.
 *
 * @author MicMun
 * @version 1.2, 19.04.2022
 */
class NameComparator(private val asc: Boolean) : Comparator<DbRecipe> {
   override fun compare(o1: DbRecipe, o2: DbRecipe): Int {
      val name1 = o1.recipeCore.name.lowercase()
      val name2 = o2.recipeCore.name.lowercase()
      return if (asc) name1.compareTo(name2) else name2.compareTo(name1)
   }
}
