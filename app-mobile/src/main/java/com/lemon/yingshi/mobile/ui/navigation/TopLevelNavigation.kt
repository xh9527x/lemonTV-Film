package com.lemon.yingshi.mobile.ui.navigation

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.lemon.yingshi.mobile.R

object TopLevelNavigation {

    private val topLevelDestinations = setOf(
        R.id.homeFragment,
        R.id.recommendationFragment,
        R.id.profileFragment
    )

    fun navigate(fragment: Fragment, @IdRes destinationId: Int) {
        if (destinationId !in topLevelDestinations) return

        // 二改：底栏已重构为 LinearLayout，不再用 BottomNavigationView，直接导航
        val navController = fragment.findNavController()
        val options = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setRestoreState(true)
            .setPopUpTo(navController.graph.startDestinationId, false, true)
            .build()
        navController.navigate(destinationId, null, options)
    }
}
