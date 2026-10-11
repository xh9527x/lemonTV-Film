package com.lemon.yingshi.mobile.ui.filter

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lemon.yingshi.mobile.ui.home.GridSpacingItemDecoration
import androidx.lifecycle.repeatOnLifecycle
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.FragmentFilterCriteriaBinding
import com.lemon.yingshi.mobile.ui.FilterActivity
import com.lemon.yingshi.mobile.ui.VodUiBinder
import com.lemon.yingshi.mobile.ui.home.PosterGridLayout
import com.lemon.yingshi.mobile.ui.home.VodGridAdapter
import com.lemon.yingshi.tv.data.remote.model.MacCmsSortOption
import com.lemon.yingshi.tv.domain.model.MacCmsNavCategory
import com.lemon.yingshi.tv.ui.screens.filter.FilterViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FilterCriteriaFragment : Fragment() {

    private var _binding: FragmentFilterCriteriaBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FilterViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilterCriteriaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.confirmButton.setOnClickListener {
            (requireActivity() as FilterActivity).showResults()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderCategoryChips(state.treeCategories.map { it.category }, state)
                    renderSecondaryTypeChips(state)
                    renderOptionChips(binding.plotChipGroup, listOf("全部") + state.plotOptions, state.selectedPlot) {
                        viewModel.selectPlot(if (it == "全部") "" else it)
                    }
                    renderOptionChips(binding.areaChipGroup, listOf("全部") + state.areaOptions, state.selectedArea) {
                        viewModel.selectArea(if (it == "全部") "" else it)
                    }
                    renderOptionChips(binding.yearChipGroup, listOf("全部") + state.yearOptions.take(12), state.selectedYear) {
                        viewModel.selectYear(if (it == "全部") "" else it)
                    }
                    renderSortChips(state)
                    val count = state.totalCount
                    binding.confirmButton.text = if (count > 0) {
                        "查看 $count 条筛选结果"
                    } else {
                        getString(R.string.view_filter_results)
                    }
                }
            }
        }
    }

    private fun renderCategoryChips(categories: List<MacCmsNavCategory>, state: com.lemon.yingshi.tv.ui.screens.filter.FilterUiState) {
        binding.categoryContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        categories.forEach { category ->
            val chip = inflater.inflate(R.layout.view_filter_chip, binding.categoryContainer, false) as TextView
            val selected = category.typeId == state.selectedNavTypeId && state.selectedTypeId == 0
            chip.text = category.label
            chip.isSelected = selected
            chip.setOnClickListener { viewModel.selectNavCategory(category) }
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = resources.getDimensionPixelSize(R.dimen.section_spacing) / 2
            binding.categoryContainer.addView(chip, params)
        }
    }

    private fun renderSecondaryTypeChips(state: com.lemon.yingshi.tv.ui.screens.filter.FilterUiState) {
        val children = state.secondaryChildren
        val show = children.isNotEmpty()
        binding.secondaryTypeLabel.isVisible = show
        binding.secondaryTypeScroll.isVisible = show
        binding.secondaryTypeContainer.removeAllViews()
        if (!show) return

        val inflater = LayoutInflater.from(requireContext())
        val marginEnd = resources.getDimensionPixelSize(R.dimen.section_spacing) / 2

        fun addChip(label: String, selected: Boolean, onClick: () -> Unit) {
            val chip = inflater.inflate(R.layout.view_filter_chip, binding.secondaryTypeContainer, false) as TextView
            chip.text = label
            chip.isSelected = selected
            chip.setOnClickListener { onClick() }
            val params = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = marginEnd
            binding.secondaryTypeContainer.addView(chip, params)
        }

        addChip("全部", state.selectedTypeId == 0) { viewModel.selectSecondaryType(0) }
        children.forEach { child ->
            addChip(child.label, state.selectedTypeId == child.typeId) {
                viewModel.selectSecondaryType(child.typeId)
            }
        }
    }

    private fun renderOptionChips(
        group: ViewGroup,
        options: List<String>,
        selected: String,
        onSelect: (String) -> Unit
    ) {
        group.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        options.forEach { option ->
            val chip = inflater.inflate(R.layout.view_filter_chip, group, false) as TextView
            chip.text = option
            chip.isSelected = if (option == "全部") selected.isBlank() else selected == option
            chip.setOnClickListener { onSelect(option) }
            group.addView(chip)
        }
    }

    private fun renderSortChips(state: com.lemon.yingshi.tv.ui.screens.filter.FilterUiState) {
        binding.sortChipGroup.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())
        state.sortOptions.forEach { sort ->
            val chip = inflater.inflate(R.layout.view_filter_chip, binding.sortChipGroup, false) as TextView
            chip.text = sort.label
            chip.isSelected = state.selectedSort == sort
            chip.setOnClickListener { viewModel.selectSort(sort) }
            binding.sortChipGroup.addView(chip)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

@AndroidEntryPoint
class FilterResultsFragment : Fragment() {

    override fun onResume() {
        super.onResume()
        // 二改：返回时重新应用列数
        try {
            val prefs = requireContext().getSharedPreferences("display_prefs", 0)
            val columns = prefs.getInt("grid_columns", 3)
            val lm = binding.resultsRecycler.layoutManager as? GridLayoutManager
            if (lm != null && lm.spanCount != columns) {
                applyColumnCount(columns)
            }
        } catch (_: Exception) { }
    }

    private var _binding: com.lemon.yingshi.mobile.databinding.FragmentFilterResultsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: FilterViewModel by activityViewModels()
    private lateinit var adapter: VodGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = com.lemon.yingshi.mobile.databinding.FragmentFilterResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = VodGridAdapter { vod ->
            VodUiBinder.openDetail(requireContext(), vod, viewModel::cacheVodForDetail)
        }
        // 二改：列数切换（1/2/3列），与推荐页共用偏好
        val prefs = requireContext().getSharedPreferences("display_prefs", 0)
        val initColumns = prefs.getInt("grid_columns", 3)
        adapter.listMode = (initColumns == 1)
        binding.columnToggleButton.text = "${initColumns}列"
        binding.columnToggleButton.setOnClickListener {
            val current = prefs.getInt("grid_columns", 3)
            val next = when (current) {
                3 -> 2
                2 -> 1
                else -> 3
            }
            prefs.edit().putInt("grid_columns", next).apply()
            applyColumnCount(next)
        }

        val spacing = resources.getDimensionPixelSize(R.dimen.card_spacing)
        binding.resultsRecycler.apply {
            setupColumnLayout(this, spacing, initColumns)
            adapter = this@FilterResultsFragment.adapter
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy <= 0) return
                    val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val totalItems = recyclerView.adapter?.itemCount ?: 0
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    if (lastVisible >= totalItems - 4) {
                        viewModel.loadMore()
                    }
                }
            })
        }
        binding.refineFilter.setOnClickListener {
            (requireActivity() as FilterActivity).showCriteria()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.resultCount.text = getString(R.string.total_results, state.totalCount)
                    binding.loadingMore.isVisible = state.isLoadingMore
                    binding.loadingIndicator.isVisible = state.isLoading && state.items.isEmpty()
                    binding.resultsRecycler.isVisible = state.items.isNotEmpty() || !state.isLoading
                    binding.errorText.isVisible = state.error != null && state.items.isEmpty() && !state.isLoading
                    binding.errorText.text = state.error
                    adapter.submitList(state.items)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** 二改：应用列数设置 */
    private fun applyColumnCount(columns: Int) {
        binding.columnToggleButton.text = "${columns}列"
        val spacing = resources.getDimensionPixelSize(R.dimen.card_spacing)
        val currentData = adapter.getCurrentData()
        // 重建 Adapter 避免 ViewHolder 混乱
        val newAdapter = VodGridAdapter { vod ->
            VodUiBinder.openDetail(requireContext(), vod, viewModel::cacheVodForDetail)
        }
        newAdapter.listMode = (columns == 1)
        if (currentData.isNotEmpty()) {
            newAdapter.submitList(currentData)
        }
        adapter = newAdapter
        setupColumnLayout(binding.resultsRecycler, spacing, columns)
        binding.resultsRecycler.adapter = adapter
    }

    /** 二改：配置 LayoutManager */
    private fun setupColumnLayout(recyclerView: RecyclerView, spacing: Int, columns: Int) {
        while (recyclerView.itemDecorationCount > 0) {
            recyclerView.removeItemDecorationAt(0)
        }
        val lm = recyclerView.layoutManager as? GridLayoutManager
        if (lm != null) {
            lm.spanCount = columns
        } else {
            recyclerView.layoutManager = GridLayoutManager(requireContext(), columns)
        }
        if (columns > 1) {
            recyclerView.addItemDecoration(
                GridSpacingItemDecoration(spanCount = columns, spacingPx = spacing)
            )
        }
        adapter.listMode = (columns == 1)
    }

}
