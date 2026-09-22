package dev.rnap.reactnativeaudiopro

// Les bornes restent dans le temps de la source : ni les paroles ni la reprise
// ne doivent changer de repère lorsqu'on lit seulement un extrait.
internal data class PlaybackRange(val startMs: Long = 0L, val endMs: Long? = null) {
	fun clamp(positionMs: Long, durationMs: Long): Long {
		val upper = listOfNotNull(endMs, durationMs.takeIf { it > 0L }).minOrNull()
		val lower = if (upper == null) startMs else minOf(startMs, upper)
		return positionMs.coerceAtLeast(lower).let { if (upper == null) it else minOf(it, upper) }
	}
}

// Un message Media3 placé dans le passé ne sera pas nécessairement livré.
// La décision s'applique à chaque replanification, pas seulement au callback.
internal fun dispatchPlaybackBoundary(
	positionMs: Long,
	triggerMs: Long,
	onReached: () -> Unit,
	onPending: () -> Unit
): Boolean {
	if (positionMs >= triggerMs) {
		onReached()
		return true
	}
	onPending()
	return false
}
