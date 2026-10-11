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
import androidx.recyclerview.widget.RecyclerView
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.util.TouchFeedback
import com.lemon.yingshi.mobile.databinding.FragmentRecommendationBinding
import com.lemon.yingshi.mobile.ui.SettingsActivity
import com.lemon.yingshi.mobile.ui.FilterActivity
import com.lemon.yingshi.mobile.ui.VodUiBinder
import com.lemon.yingshi.mobile.ui.home.PosterGridLayout
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
        binding.filterButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val typeId = viewModel.getFirstNavTypeId() ?: -1
                startActivity(
                    FilterActivity.intent(
                        context = requireContext(),
                        typeId = typeId
                    )
                )
            }
        }

        // 二改：初始化列数
        val prefs = requireContext().getSharedPreferences("display_prefs", 0)
        applyColumnCount(prefs.getInt("grid_columns", 3))

        adapter = VodGridAdapter { vod ->
            VodUiBinder.openDetail(requireContext(), vod, viewModel::cacheVodForDetail)
        }
        // 二改：1列模式用横图布局
        adapter.listMode = prefs.getInt("grid_columns", 3) == 1
        val spacing = resources.getDimensionPixelSize(R.dimen.card_spacing)
        binding.recommendRecycler.apply {
            PosterGridLayout.setup(this, spacing)
            adapter = this@RecommendationFragment.adapter
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    binding.refreshLayout.isEnabled = !recyclerView.canScrollVertically(-1)
                    if (dy <= 0) return
                    val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
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

    /** 二改：应用列数设置 */
    private fun applyColumnCount(columns: Int) {
        binding.columnToggleButton.text = "${columns}列"
        val recyclerView = binding.recyclerView
        val layoutManager = recyclerView.layoutManager as? GridLayoutManager
        if (layoutManager != null) {
            layoutManager.spanCount = columns
            recyclerView.requestLayout()
        }
        if (::adapter.isInitialized) {
            adapter.listMode = columns == 1
            adapter.notifyDataSetChanged()
        }
    }
}
