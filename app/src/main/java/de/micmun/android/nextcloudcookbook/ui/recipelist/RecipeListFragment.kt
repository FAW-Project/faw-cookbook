package de.micmun.android.nextcloudcookbook.ui.recipelist

import android.content.DialogInterface
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.postDelayed
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import de.micmun.android.nextcloudcookbook.MainApplication
import de.micmun.android.nextcloudcookbook.R
import de.micmun.android.nextcloudcookbook.data.CategoryFilter
import de.micmun.android.nextcloudcookbook.data.RecipeFilter
import de.micmun.android.nextcloudcookbook.data.SortValue
import de.micmun.android.nextcloudcookbook.databinding.FragmentRecipelistBinding
import de.micmun.android.nextcloudcookbook.db.DbRecipeRepository
import de.micmun.android.nextcloudcookbook.reciever.LocalBroadcastReceiver
import de.micmun.android.nextcloudcookbook.services.sync.SyncService
import de.micmun.android.nextcloudcookbook.ui.CurrentSettingViewModel
import de.micmun.android.nextcloudcookbook.ui.CurrentSettingViewModelFactory
import de.micmun.android.nextcloudcookbook.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fragment for list of recipes.
 *
 * @author MicMun
 * @version 2.6, 29.05.22
 */
class RecipeListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener, RecipeSearchCallback {
   private lateinit var binding: FragmentRecipelistBinding
   private lateinit var recipesViewModel: RecipeListViewModel
   private lateinit var settingViewModel: CurrentSettingViewModel
   private lateinit var adapter: RecipeListAdapter

   private lateinit var mLocalBroadcastManager: LocalBroadcastManager
   private lateinit var mLocalBroadcastReceiver: LocalBroadcastReceiver
   private var mAutoRefreshList = false

   private var refreshItem: MenuItem? = null

   private var sortDialog: AlertDialog? = null
   private var currentSort: SortValue? = null

   private var isLoaded: Boolean = false

   override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
      binding = DataBindingUtil.inflate(inflater, R.layout.fragment_recipelist, container, false)

      binding.swipeContainer.setOnRefreshListener(this)
      val recipeListViewModelFactory = RecipeListViewModelFactory(requireActivity().application)
      recipesViewModel = ViewModelProvider(this, recipeListViewModelFactory).get(RecipeListViewModel::class.java)
      val factory = CurrentSettingViewModelFactory(MainApplication.AppContext)
      settingViewModel =
         ViewModelProvider(MainApplication.AppContext, factory).get(CurrentSettingViewModel::class.java)
      binding.lifecycleOwner = viewLifecycleOwner


      (activity as MainActivity).setRecipeSearchCallback(this)

      recipesViewModel.isUpdating.observe(viewLifecycleOwner) { it ->
         it?.let { isUpdating ->
            if (isUpdating) {
               // preparation for loading
               binding.swipeContainer.isRefreshing = true
               refreshItem?.let { it.isEnabled = false }
            } else {
               binding.swipeContainer.isRefreshing = false
               refreshItem?.let { it.isEnabled = true }
            }
         }
      }

      recipesViewModel.isLoaded.observe(viewLifecycleOwner) {
         it?.let {
            isLoaded = it
         }
      }

      (activity as MainActivity?)?.showToolbar(
         showToolbar = true,
         showSearch = true,
         showSort = true
      )

      initializeRecipeList()

      val asyncFilter = (activity as MainActivity?)?.getAsyncFilter()
      if(asyncFilter!=null){
         searchRecipes(asyncFilter)
         (activity as MainActivity?)?.setSearchTerm(asyncFilter.query)
         (activity as MainActivity?)?.setAsyncFilter(null)
      }

      setupBroadcastListener()
      return binding.root
   }

   @Deprecated("Deprecated in Java",
               ReplaceWith("@Suppress(\"DEPRECATION\") super.onActivityCreated(savedInstanceState)",
                                          "androidx.fragment.app.Fragment"))
   override fun onActivityCreated(savedInstanceState: Bundle?) {
      @Suppress("DEPRECATION")
      super.onActivityCreated(savedInstanceState)
   }

   private fun initializeRecipeList() {
      binding.recipeListViewModel = recipesViewModel
      binding.lifecycleOwner = viewLifecycleOwner

      // data adapter
      adapter = RecipeListAdapter(RecipeListListener { recipeName -> recipesViewModel.onRecipeClicked(recipeName) },
                                  DbRecipeRepository.getInstance(requireActivity().application))
      binding.recipeList.adapter = adapter

      // settings
      adapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

      recipesViewModel.navigateToRecipe.observe(viewLifecycleOwner) { recipe ->
         recipe?.let {
            this.findNavController()
               .navigate(
                  RecipeListFragmentDirections.actionRecipeListFragmentToRecipeDetailFragment(
                     recipe
                  )
               )
            recipesViewModel.onRecipeNavigated()
         }
      }

      lifecycleScope.launchWhenResumed {
         settingViewModel.sorting.collect { sort ->
            currentSort = SortValue.getByValue(sort)
            recipesViewModel.sortList(currentSort!!)
            loadData()
         }
      }
      lifecycleScope.launchWhenResumed {
         settingViewModel.storageAccessed.collect { storageAccessed ->
            if (storageAccessed) {
               settingViewModel.recipeDirectory.collect { dir ->
                  if (!isLoaded) {
                     recipesViewModel.initRecipes(dir)
                  }
               }
            }
         }
      }

      settingViewModel.category.observe(viewLifecycleOwner) { catFilter ->
         catFilter?.let {
            // filter recipes to set category
            recipesViewModel.filterRecipesByCategory(it)
            setCategoryTitle(it)
            loadData()

            settingViewModel.categoryChanged.observe(viewLifecycleOwner) { changed ->
               changed?.let { c ->
                  if (c) {
                     binding.recipeList.postDelayed(250) {
                        binding.recipeList.smoothScrollToPosition(0)
                     }
                     settingViewModel.resetCategoryChanged()
                  }
               }
            }
         }
      }

      recipesViewModel.categories.observe(viewLifecycleOwner) { categories ->
         categories?.let {
            var order = 1
            val activity = requireActivity() as MainActivity
            val menu = activity.getMenu().findItem(R.id.submenu_item).subMenu

            menu.removeGroup(R.id.menu_categories_group)
            categories.forEach { category ->
               menu.add(R.id.menu_categories_group, category.hashCode(), order++, category)
                  .setIcon(R.drawable.ic_food)
            }
         }
      }

      loadData()
   }

   /**
    * Loads the current data.
    */
   private fun loadData() {
      recipesViewModel.getRecipes().observe(viewLifecycleOwner) { recipes ->
         recipes?.let {
            adapter.submitList(it)
         }
         if (recipes.isNullOrEmpty()) {
            if (R.id.emptyConstraint == binding.switcher.nextView.id)
               binding.switcher.showNext()
         } else if (R.id.titleConstraint == binding.switcher.nextView.id) {
            binding.switcher.showNext()
         }
      }
   }

   /**
    * Sets the category title.
    *
    * @param categoryFilter Filter of the category.
    */
   private fun setCategoryTitle(categoryFilter: CategoryFilter) {
      // set title in text view headline
      val catTitle = binding.categoryTitle
      if (categoryFilter.type == CategoryFilter.CategoryFilterOption.ALL_CATEGORIES)
         catTitle.visibility = View.GONE
      else {
         catTitle.visibility = View.VISIBLE
         catTitle.text = if (categoryFilter.type == CategoryFilter.CategoryFilterOption.UNCATEGORIZED)
            getString(R.string.text_uncategorized)
         else categoryFilter.name
      }
   }

   private fun showSortOptions() {
      val sortNames = resources.getStringArray(R.array.sort_names)
      val builder = AlertDialog.Builder(requireContext())
      builder.setTitle(R.string.menu_sort_title)
      val sortValue = currentSort ?: SortValue.NAME_A_Z
      builder.setSingleChoiceItems(sortNames, sortValue.sort) { _: DialogInterface, which: Int ->
         settingViewModel.setSorting(which, (activity as MainActivity))
         sortDialog?.dismiss()
         sortDialog = null
         binding.recipeList.postDelayed(200) {
            binding.recipeList.smoothScrollToPosition(0)
         }
      }
      builder.setOnDismissListener { sortDialog = null }
      sortDialog = builder.show()
   }

   //todo: think about how to make this more elegant.
   //also it seems quickly refreshing breaks the database.
   private fun onRefreshAndReschedule() {
      if (!mAutoRefreshList) {
         return
      }
      Handler(Looper.getMainLooper()).postDelayed({
         CoroutineScope(Dispatchers.Main).launch {
            settingViewModel.storageAccessed.collect { storageAccessed ->
               if (storageAccessed) {
                  settingViewModel.recipeDirectory.collect { dir ->
                     if (dir != recipesViewModel.getRecipeDir()) {
                        recipesViewModel.initRecipes(dir, true)
                     } else {
                        recipesViewModel.initRecipes(hidden = true)
                     }
                  }
               }
            }
         }
         onRefreshAndReschedule()
      }, 500)
   }

   override fun onRefresh() {
      // load recipes from files
      recipesViewModel.initRecipes()
   }

   override fun onPause() {
      dismissBroadcastListener()
      sortDialog?.dismiss()
      super.onPause()
   }

   override fun onResume() {
      setupBroadcastListener()
      loadData()
      super.onResume()
   }

   override fun searchRecipes(filter: RecipeFilter)  {
      if(filter.type == RecipeFilter.QueryType.QUERY_NAME && filter.query == "") {
         setCategoryTitle(CategoryFilter(CategoryFilter.CategoryFilterOption.ALL_CATEGORIES))
      }
      recipesViewModel.search(filter)
      loadData()
   }

   override fun searchCategory(filter: CategoryFilter) {
      recipesViewModel.filterRecipesByCategory(filter)
      recipesViewModel.search(null)
      loadData()
   }

   override fun showSortSelector() {
      showSortOptions()
   }

   fun notifyUpdate(updating: Boolean) {
      mAutoRefreshList = updating

      // Dont use this for now.
      // onRefreshAndReschedule()

      if (!updating) {
         CoroutineScope(Dispatchers.Main).launch {
            settingViewModel.storageAccessed.collect { storageAccessed ->
               if (storageAccessed) {
                  settingViewModel.recipeDirectory.collect { dir ->
                     if (dir != recipesViewModel.getRecipeDir()) {
                        recipesViewModel.initRecipes(dir, true)
                     } else {
                        recipesViewModel.initRecipes(hidden = true)
                     }
                  }
               }
            }
         }
      }
   }

   private fun setupBroadcastListener() {
      mLocalBroadcastManager = LocalBroadcastManager.getInstance(this.requireContext())
      mLocalBroadcastReceiver = LocalBroadcastReceiver(this)
      val intentFilter = IntentFilter()
      intentFilter.addAction(SyncService.SYNC_SERVICE_UPDATE_BROADCAST)
      mLocalBroadcastManager.registerReceiver(mLocalBroadcastReceiver, intentFilter)
   }

   private fun dismissBroadcastListener() {
      mLocalBroadcastManager.unregisterReceiver(mLocalBroadcastReceiver)
   }
}
