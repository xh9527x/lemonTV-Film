package com.lemon.yingshi.mobile.ui

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.lemon.yingshi.mobile.R
import com.lemon.yingshi.mobile.databinding.ActivityMainBinding
import com.lemon.yingshi.tv.ui.screens.home.MacCmsHomeUiState
import com.lemon.yingshi.tv.ui.screens.home.MacCmsHomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private val versionUpdateViewModel: VersionUpdateViewModel by viewModels()
    private val homeViewModel: MacCmsHomeViewModel by viewModels()

    private val topLevelDestinations = setOf(
        R.id.homeFragment,
        R.id.recommendationFragment,
        R.id.profileFragment
    )

    private var splashDismissed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.splashOverlay.isVisible = true
        applySystemBarInsets()
        observeProfileNavBadge()
        observeUpdateDownload()
        observeHomeReadyForSplash()

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController
        // 二改：自定义底栏点击（替代 BottomNavigationView.setupWithNavController）
        binding.navHomeBtn.setOnClickListener { navigateToTopLevel(R.id.homeFragment) }
        binding.navRecommendBtn.setOnClickListener { navigateToTopLevel(R.id.recommendationFragment) }
        binding.navProfileBtn.setOnClickListener { navigateToTopLevel(R.id.profileFragment) }
        syncBottomNavWithDestination()

        binding.navHomeBtn.setOnLongClickListener {
            scrollHomeToTop()
            true
        }

        window.decorView.post {
            window.setBackgroundDrawableResource(R.color.background_dark)
        }

        lifecycleScope.launch {
            delay(500)
            val versionCode = packageManager.getPackageInfo(packageName, 0).versionCode
            versionUpdateViewModel.checkForUpdates(versionCode)
        }
    }

    private fun observeHomeReadyForSplash() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    delay(SPLASH_MAX_WAIT_MS)
                    dismissSplash()
                }
                homeViewModel.uiState.collect { state ->
                    if (isHomeReadyForDisplay(state)) {
                        dismissSplash()
                    }
                }
            }
        }
    }

    private fun dismissSplash() {
        if (splashDismissed) return
        splashDismissed = true
        binding.splashOverlay.isVisible = false
    }

    private fun isHomeReadyForDisplay(state: MacCmsHomeUiState): Boolean {
        if (state.isLoading) return false
        if (!state.isConfigured) return true
        if (state.error != null &&
            state.sections.isEmpty() &&
            state.recommendedItems.isEmpty()
        ) {
            return true
        }
        if (state.recommendedItems.isNotEmpty() || state.sections.isNotEmpty()) {
            return true
        }
        return !state.isLoadingSections
    }

    private fun observeUpdateDownload() {
        UpdateDownloadDialog.observe(
            activity = this,
            isDownloading = versionUpdateViewModel.isDownloading,
            downloadProgress = versionUpdateViewModel.downloadProgress,
            downloadFailed = versionUpdateViewModel.downloadFailed,
            installApk = versionUpdateViewModel.installApk
        )
    }

    override fun onResume() {
        super.onResume()
        UpdateInstallCoordinator.retryPendingInstall(this)
    }

    override fun onDestroy() {
        UpdateDownloadDialog.dismiss()
        super.onDestroy()
    }

    private fun observeProfileNavBadge() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                versionUpdateViewModel.hasUpdate.collect { hasUpdate ->
                    // 二改：自定义底栏暂不支持 Badge，更新提示走关于页
                }
            }
        }
    }

    /** 二改：自定义底栏导航到顶级目的地 */
    private fun navigateToTopLevel(destinationId: Int) {
        val current = navController.currentDestination?.id
        if (current == destinationId) {
            if (destinationId == R.id.homeFragment) scrollHomeToTop()
            return
        }
        // 二改：用 navOptions 保持返回栈干净（替代 setupWithNavController）
        val options = androidx.navigation.navOptions {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        navController.navigate(destinationId, null, options)
    }

    private fun syncBottomNavWithDestination() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id !in topLevelDestinations) return@addOnDestinationChangedListener
            updateBottomNavSelected(destination.id)
        }
    }

    /** 二改：更新自定义底栏选中态（图标着色） */
    private fun updateBottomNavSelected(selectedId: Int) {
        val activeColor = androidx.core.content.ContextCompat.getColor(this, R.color.bottom_nav_active)
        val inactiveColor = androidx.core.content.ContextCompat.getColor(this, R.color.bottom_nav_inactive)
        binding.navHomeBtn.imageTintList = android.content.res.ColorStateList.valueOf(
            if (selectedId == R.id.homeFragment) activeColor else inactiveColor
        )
        binding.navRecommendBtn.imageTintList = android.content.res.ColorStateList.valueOf(
            if (selectedId == R.id.recommendationFragment) activeColor else inactiveColor
        )
        binding.navProfileBtn.imageTintList = android.content.res.ColorStateList.valueOf(
            if (selectedId == R.id.profileFragment) activeColor else inactiveColor
        )
    }

    private fun scrollHomeToTop() {
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val home = navHost?.childFragmentManager?.fragments
            ?.filterIsInstance<com.lemon.yingshi.mobile.ui.home.HomeFragment>()
            ?.firstOrNull()
        home?.scrollToTop()
    }

    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.navHostFragment.updatePadding(top = systemBars.top)
            // 二改：手势条安全区用 bottomMargin，避免自定义底栏被撑高。
            binding.bottomNav.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                bottomMargin = systemBars.bottom
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private companion object {
        const val SPLASH_MAX_WAIT_MS = 12_000L
    }
}
