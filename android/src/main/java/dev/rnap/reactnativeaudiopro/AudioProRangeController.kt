package dev.rnap.reactnativeaudiopro

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage

// Le message temporel est exécuté dans le service, même quand JS est suspendu.
// On conserve la timeline complète pour exposer les positions et la durée source.
@OptIn(UnstableApi::class)
internal class AudioProRangeController(private val player: ExoPlayer) : Player.Listener {
	private var boundary: PlayerMessage? = null
	private var finishing = false
	private var generation = 0

	private fun range(item: MediaItem?): PlaybackRange {
		val extras = item?.mediaMetadata?.extras
		return PlaybackRange(
			extras?.getLong("audioPro.startMs", 0L) ?: 0L,
			if (extras?.containsKey("audioPro.endMs") == true) extras.getLong("audioPro.endMs") else null
		)
	}

	override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
		val start = range(mediaItem).startMs
		if (player.currentPosition < start) player.seekTo(start)
		scheduleBoundary()
	}

	override fun onTimelineChanged(timeline: Timeline, reason: Int) {
		scheduleBoundary()
	}

	override fun onPositionDiscontinuity(
		oldPosition: Player.PositionInfo,
		newPosition: Player.PositionInfo,
		reason: Int
	) {
		if (finishing) return
		// Invalide aussi un message déjà posté au looper avant un seek arrière.
		if (scheduleBoundary()) return
		val range = range(player.currentMediaItem)
		if (newPosition.positionMs < range.startMs) {
			val clamped = range.clamp(newPosition.positionMs, player.duration)
			if (clamped != newPosition.positionMs) player.seekTo(clamped)
		}
	}

	private fun scheduleBoundary(): Boolean {
		val scheduledGeneration = ++generation
		boundary?.cancel()
		boundary = null
		val item = player.currentMediaItem ?: return false
		val currentRange = range(item)
		// Un média entier suivi d'un extrait doit viser le début de cet extrait
		// directement ; la transition automatique habituelle partirait de zéro.
		val nextNeedsSeek = player.hasNextMediaItem() &&
			range(player.getMediaItemAt(player.nextMediaItemIndex)).startMs > 0L
		val end = currentRange.endMs ?: if (nextNeedsSeek && player.duration > 0L) player.duration else return false
		// La fin naturelle possède déjà son événement ; ne pas le doubler.
		if (!nextNeedsSeek && player.duration > 0L && end >= player.duration) return false
		val boundaryMs = if (player.duration > 0L) minOf(end, player.duration) else end
		// Le message précède d'une milliseconde la fin naturelle pour que le seek
		// vers le suivant porte sa position initiale dès la transition.
		val triggerMs = if (nextNeedsSeek) (boundaryMs - 1L).coerceAtLeast(0L) else boundaryMs
		return dispatchPlaybackBoundary(player.currentPosition, triggerMs,
			onReached = { finishRange() },
			onPending = {
				boundary = player.createMessage { _, _ ->
					if (generation == scheduledGeneration && player.currentMediaItem?.mediaId == item.mediaId &&
						player.currentPosition >= triggerMs) finishRange()
				}.setLooper(player.applicationLooper)
					.setPosition(player.currentMediaItemIndex, triggerMs)
					.setDeleteAfterDelivery(false)
					.send()
			})
	}

	private fun finishRange() {
		if (finishing) return
		finishing = true
		try {
			if (player.hasNextMediaItem()) {
				val next = player.nextMediaItemIndex
				player.seekTo(next, range(player.getMediaItemAt(next)).startMs)
			} else {
				val item = player.currentMediaItem ?: return
				val range = range(item)
				val end = range.endMs ?: return
				val duration = player.duration.coerceAtLeast(0L)
				player.pause()
				player.seekTo(range.clamp(range.startMs, duration))
				AudioProController.onRangeEnded(item.mediaId, range.clamp(end, duration), duration)
			}
		} finally {
			finishing = false
			scheduleBoundary()
		}
	}

	fun release() {
		generation++
		boundary?.cancel()
		boundary = null
		player.removeListener(this)
	}
}
