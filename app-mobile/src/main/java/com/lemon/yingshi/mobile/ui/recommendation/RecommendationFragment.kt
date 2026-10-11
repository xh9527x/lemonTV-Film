package com.lemon.yingshi.mobile.ui.recommendation

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.util.TouchFeedback
import com.lemon.yingshi.mobile.databinding.FragmentRecommendationBinding
import com.lemon.yingshi.mobile.ui.SettingsActivity
import com.lemon.yingshi.mobile.ui.FilterActivity
import com.lemon.yingshi.mobile.ui.VodUiBinder
import com.lemon.yingshi.mobile.ui.home.GridSpacingItemDecoration
import com.lemon.yingshi.mobile.ui.home.VodGridAdapter
import com.lemon.yingshi.tv.ui.screens.recommended.RecommendedUiState
import com.lemon.yingshi.tv.ui.screens.recommended.RecommendedViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RecommendationFragment : Fragment() {

    private var _binding: FragmentRecommendationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecommendedViewModel by viewModels()
    private lateinit var adapter: VodGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecommendationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // 二改：返回时重新应用列数（防止 Fragment 重建后列数丢失）
        val prefs = requireContext().getSharedPreferences("display_prefs", 0)
        val columns = prefs.getInt("grid_columns", 3)
        // 只有不一致时才重应用，避免无限循环
        val lm = binding.recommendRecycler.layoutManager as? GridLayoutManager
        if (lm != null && lm.spanCount != columns) {
            applyColumnCount(columns)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 二改：列数切换（1/2/3列）
        binding.columnToggleButton.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("display_prefs", 0)
            val current = prefs.getInt("grid_columns", 3)
            val next = when (current) {
                3 -> 2
                2 -> 1
                else -> 3
            }
            prefs.edit().putInt("grid_columns", next).apply()
            applyColumnCount(next)
        }

        // 二改：推荐页顶部高级筛选入口（同首页“更多”的筛选页）
        // 取首个顶部分类传 typeId，保证和首页一致有数据；取不到则走通用筛选
        // 二改：推荐页高级筛选不传 typeId，走通用筛选（传随意 typeId 会导致结果为0）
        binding.filterButton.setOnClickListener {
            startActivity(
                FilterActivity.intent(
                    context = requireContext()
                )
            )
        }

        // 二改：初始化列数（从偏好读取）
        val prefs = requireContext().getSharedPreferences("display_prefs", 0)
        val initColumns = prefs.getInt("grid_columns", 3)

        adapter = VodGridAdapter { vod ->
            VodUiBinder.openDetail(requireContext(), vod, viewModel::cacheVodForDetail)
        }
        val spacing = resources.getDimensionPixelSize(R.dimen.card_spacing)
        binding.recommendRecycler.apply {
            setupColumnLayout(this, spacing, initColumns)
            adapter = this@RecommendationFragment.adapter
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    binding.refreshLayout.isEnabled = !recyclerView.canScrollVertically(-1)
                    if (dy <= 0) return
                    // 二改：兼容 Grid 和 Linear 两种 LayoutManager
                    val layoutManager = recyclerView.layoutManager ?: return
                    val lastVisible = when (layoutManager) {
                        is GridLayoutManager -> layoutManager.findLastVisibleItemPosition()
                        is LinearLayoutManager -> layoutManager.findLastVisibleItemPosition()
                        else -> return
                    }
                    if (lastVisible >= (recyclerView.adapter?.itemCount ?: 0) - 4) {
                        viewModel.loadMore()
                    }
                }
            })
        }

        binding.refreshLayout.setColorSchemeResources(R.color.primary_yellow)
        binding.refreshLayout.setOnRefreshListener {
            viewModel.loadMobileFeed(forceRefresh = true)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isLoadingMore.collect { loading ->
                        binding.loadingMore.isVisible = loading
                    }
                }
                launch {
                    viewModel.isRefreshing.collect { refreshing ->
                        binding.refreshLayout.isRefreshing = refreshing
                    }
                }
                viewModel.uiState.collect { state ->
                    when (state) {
                        RecommendedUiState.Loading -> {
                            binding.loadingIndicator.isVisible = !binding.refreshLayout.isRefreshing
                            binding.recommendRecycler.isVisible = false
                            binding.emptyText.isVisible = false
                            binding.errorText.isVisible = false
                            binding.recommendCount.isVisible = false
                        }

                        is RecommendedUiState.Empty -> {
                            binding.loadingIndicator.isVisible = false
                            binding.recommendRecycler.isVisible = false
                            binding.emptyText.isVisible = true
                            binding.emptyText.text = state.message
                            binding.errorText.isVisible = false
                            binding.recommendCount.isVisible = false
                        }

                        is RecommendedUiState.Error -> {
                            binding.loadingIndicator.isVisible = false
                            binding.recommendRecycler.isVisible = false
                            binding.emptyText.isVisible = false
                            binding.errorText.isVisible = true
                            binding.errorText.text = state.message
                            binding.recommendCount.isVisible = false
                            if (state.message.contains("配置")) {
                                TouchFeedback.applyBorderlessRipple(binding.errorText)
                                binding.errorText.setOnClickListener {
                                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                                }
                            }
                        }

                        is RecommendedUiState.Success -> {
                            binding.loadingIndicator.isVisible = false
                            binding.recommendRecycler.isVisible = true
                            binding.emptyText.isVisible = false
                            binding.errorText.isVisible = false
                            binding.recommendCount.isVisible = true
                            // 二改：兜底模式下提示用户当前展示的是最新内容
                            binding.recommendCount.text = if (state.isFallbackToLatest) {
                                getString(
                                    R.string.recommend_fallback_to_latest,
                                    state.total
                                )
                            } else {
                                getString(
                                    R.string.recommend_total_count,
                                    state.total
                                )
                            }
                            adapter.submitList(state.items)
                        }
                    }
                }
            }
        }

        if (savedInstanceState == null) {
            viewModel.loadMobileFeed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** 二改：应用列数设置（切换时重建 Adapter，避免 ViewHolder 类型混乱） */
    private fun applyColumnCount(columns: Int) {
        binding.columnToggleButton.text = "${columns}列"
        val spacing = resources.getDimensionPixelSize(R.dimen.card_spacing)
        // 重建 Adapter，确保 ViewHolder 类型正确
        val currentData = if (::adapter.isInitialized) adapter.getCurrentData() else emptyList()
        adapter = VodGridAdapter { vod ->
            VodUiBinder.openDetail(requireContext(), vod, viewModel::cacheVodForDetail)
        }
        adapter.listMode = (columns == 1)
        if (currentData.isNotEmpty()) {
            adapter.submitList(currentData)
        }
        setupColumnLayout(binding.recommendRecycler, spacing, columns)
        binding.recommendRecycler.adapter = adapter
    }

    /** 二改：根据列数配置 RecyclerView（统一用 GridLayoutManager，1列即单列网格） */
    private fun setupColumnLayout(recyclerView: RecyclerView, spacing: Int, columns: Int) {
        // 清掉旧的 decoration
        while (recyclerView.itemDecorationCount > 0) {
            recyclerView.removeItemDecorationAt(0)
        }
        val lm = recyclerView.layoutManager as? GridLayoutManager
        if (lm != null) {
            // 复用已有的 GridLayoutManager，只改 spanCount（避免切换 LayoutManager 失效）
            lm.spanCount = columns
        } else {
            recyclerView.layoutManager = GridLayoutManager(requireContext(), columns)
        }
        if (columns > 1) {
            recyclerView.addItemDecoration(
                GridSpacingItemDecoration(spanCount = columns, spacingPx = spacing)
            )
        }
        // 1列用横图布局，2/3列用竖海报
        adapter.listMode = (columns == 1)
    }
}
