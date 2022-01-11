package de.micmun.android.nextcloudcookbook.ui.recipedetail

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import de.micmun.android.nextcloudcookbook.MainApplication
import de.micmun.android.nextcloudcookbook.R
import de.micmun.android.nextcloudcookbook.databinding.FragmentDetailBinding
import de.micmun.android.nextcloudcookbook.db.model.DbRecipe
import de.micmun.android.nextcloudcookbook.settings.PreferenceData
import de.micmun.android.nextcloudcookbook.ui.CurrentSettingViewModel
import de.micmun.android.nextcloudcookbook.ui.CurrentSettingViewModelFactory
import de.micmun.android.nextcloudcookbook.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Fragment for detail of a recipe.
 *
 * @author MicMun
 * @version 2.1, 28.08.21
 */
class RecipeDetailFragment : Fragment(), CookTimeClickListener {
   private lateinit var binding: FragmentDetailBinding
   private lateinit var viewModel: RecipeViewModel
   private lateinit var settingViewModel: CurrentSettingViewModel

   private var adapter: ViewPagerAdapter? = null

   private var currentPage = 0

   private var recipeId = -1L
   private var isServiceStarted = false

   companion object {
      private const val KEY_CURRENT_PAGE = "current_page"
   }

   override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      val args = RecipeDetailFragmentArgs.fromBundle(requireArguments())
      if (savedInstanceState != null) {
         currentPage = savedInstanceState[KEY_CURRENT_PAGE] as Int
         recipeId = args.recipeId
      } else {
         recipeId = args.recipeId
      }
      isServiceStarted = args.isServiceStarted
   }

   override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
      binding = DataBindingUtil.inflate(inflater, R.layout.fragment_detail, container, false)

      // if the service calls this fragment, navigate to CooktimerFragment
      if (isServiceStarted) {
         isServiceStarted = false
         findNavController().navigate(
            RecipeDetailFragmentDirections.actionRecipeDetailFragmentToCooktimerFragment(recipeId))
      }

      // Settings view model
      val factory = CurrentSettingViewModelFactory(MainApplication.AppContext)
      settingViewModel =
         ViewModelProvider(MainApplication.AppContext, factory).get(CurrentSettingViewModel::class.java)

      val viewModelFactory = RecipeViewModelFactory(recipeId, requireActivity().application)
      viewModel = ViewModelProvider(this, viewModelFactory).get(RecipeViewModel::class.java)
      binding.lifecycleOwner = viewLifecycleOwner

      val orientation = resources.configuration.orientation

      viewModel.recipe.observe(viewLifecycleOwner) { recipe ->
         recipe?.let {
            initPager(it, orientation)
            setTitle(it.recipeCore.name)
         }
      }

      var parent = (activity as MainActivity?)!!
      binding.backButton.setOnClickListener {
         requireActivity().onBackPressed()
         parent.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }

      parent.showToolbar(false)

      if(PreferenceData.getInstance().getScreenKeepalive()){
         parent.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }

      return binding.root
   }

   override fun onStop() {
      (activity as MainActivity?)!!.showToolbar(true)
      super.onStop()
   }
   /**
    * Initialise the view pager and tablayout with the current recipe.
    *
    * @param recipe current recipe data.
    */
   private fun initPager(recipe: DbRecipe, orientation: Int) {
      adapter = ViewPagerAdapter(recipe, orientation, this)
      binding.pager.adapter = adapter
      binding.pager.offscreenPageLimit = adapter!!.itemCount
      val tabLayout = binding.tabLayout
      TabLayoutMediator(tabLayout, binding.pager) { tab, position ->
         when (adapter!!.getItemViewType(position)) {
            ViewPagerAdapter.TYPE_INFO -> {
               tab.icon = AppCompatResources.getDrawable(this.requireContext(), R.drawable.ic_info)
            }
            ViewPagerAdapter.TYPE_INGREDIENTS -> {
               tab.icon = AppCompatResources.getDrawable(this.requireContext(), R.drawable.ic_ingredients)
            }
            ViewPagerAdapter.TYPE_INSTRUCTIONS -> {
               tab.icon = AppCompatResources.getDrawable(this.requireContext(), R.drawable.ic_instructions)
            }
            ViewPagerAdapter.TYPE_NUTRITIONS -> {
               tab.icon = AppCompatResources.getDrawable(this.requireContext(), R.drawable.ic_nutritions)
            }
            ViewPagerAdapter.TYPE_INGRED_AND_INSTRUCT -> {
               tab.icon = AppCompatResources.getDrawable(this.requireContext(), R.drawable.ic_instructions)
            }
         }
      }.attach()

      binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
         override fun onPageSelected(position: Int) {
            super.onPageSelected(position)
            currentPage = position

            val type = adapter!!.getItemViewType(position)

            if (type == ViewPagerAdapter.TYPE_INSTRUCTIONS || type == ViewPagerAdapter.TYPE_INGRED_AND_INSTRUCT) {
               activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
               activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
         }
      })
   }

   override fun onSaveInstanceState(outState: Bundle) {
      outState.putInt(KEY_CURRENT_PAGE, currentPage)
      super.onSaveInstanceState(outState)
   }

   override fun onPause() {
      activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      super.onPause()
   }

   override fun onResume() {
      super.onResume()
      (activity as MainActivity?)!!.showToolbar(false)
      if (adapter != null) {
         val type = adapter!!.getItemViewType(currentPage)
         if (type == ViewPagerAdapter.TYPE_INSTRUCTIONS || type == ViewPagerAdapter.TYPE_INGRED_AND_INSTRUCT) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
         }
      }
   }

   @Suppress("DEPRECATION")
   override fun onActivityCreated(savedInstanceState: Bundle?) {
      super.onActivityCreated(savedInstanceState)
      val title = viewModel.recipe.value?.recipeCore?.name
      title?.let {
         setTitle(it)
      }
   }

   private fun setTitle(title: String) {
      (activity as AppCompatActivity).supportActionBar?.title = title
   }

   override fun onClick(recipe: DbRecipe) {
     if(!recipe.recipeCore.cookTime.isEmpty()){
        findNavController()
           .navigate(
              RecipeDetailFragmentDirections.actionRecipeDetailFragmentToCooktimerFragment(recipe.recipeCore.id))
     } else {
        Toast.makeText(requireContext(), getString(R.string.recipe_no_timer), Toast.LENGTH_SHORT).show()
     }
   }
}
