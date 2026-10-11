package com.lemon.yingshi.mobile.ui.player

import androidx.media3.common.Player
import com.lemon.yingshi.tv.domain.service.WatchHistoryService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 二改：播放统计记录器（与宿主解耦的独立组件）。
 *
 * 背景：原版仅全屏播放器（PlayerViewModel）记录观看历史与播放时长，
 * 详情页顶部内嵌播放器不记录。本记录器为内嵌播放提供对等的记录能力：
 * 开始播放即创建历史，播放中每 15s 保存一次进度，停止/切集时保存最终进度。
 *
 * 使用：[start] 传入播放器与影片信息；[stop] 保存最终进度并释放。
 */
class PlaybackStatsRecorder(
    private val watchHistoryService: WatchHistoryService,
    private val scope: CoroutineScope
) {

    data class Target(
        val mediaId: String,
        val episodeId: String?,
        val title: String?,
        val episodeTitle: String?,
        val videoUrl: String?,
        val posterUrl: String?
    )

    private var recordJob: Job? = null
    private var player: Player? = null
    private var target: Target? = null

    fun start(player: Player, target: Target) {
        // 切集/重播：先保存旧的最终进度
        val oldPlayer = this.player
        val oldTarget = this.target
        if (oldPlayer != null && oldTarget != null) {
            scope.launch { saveSnapshot(oldPlayer, oldTarget) }
        }
        recordJob?.cancel()
        this.player = player
        this.target = target
        // 立即保存一次，确保观看历史即时出现
        scope.launch { saveSnapshot(player, target) }
        recordJob = scope.launch {
            while (isActive) {
                delay(STATS_INTERVAL_MS)
                saveSnapshot(player, target)
            }
        }
    }

    fun stop() {
        val p = player
        val t = target
        recordJob?.cancel()
        recordJob = null
        player = null
        target = null
        if (p != null && t != null) {
            scope.launch { saveSnapshot(p, t) }
        }
    }

    private suspend fun saveSnapshot(player: Player, target: Target) {
        try {
            val duration = player.duration.coerceAtLeast(0L)
            if (duration <= 0L) return
            val position = player.currentPosition.coerceIn(0L, duration)
            watchHistoryService.saveWatchProgress(
                mediaId = target.mediaId,
                episodeId = target.episodeId,
                progress = position,
                duration = duration,
                title = target.title,
                episodeTitle = target.episodeTitle,
                videoUrl = target.videoUrl,
                posterUrl = target.posterUrl
            )
        } catch (_: Exception) {
            // 统计记录失败不影响播放
        }
    }

    companion object {
        private const val STATS_INTERVAL_MS = 15_000L
    }
}
