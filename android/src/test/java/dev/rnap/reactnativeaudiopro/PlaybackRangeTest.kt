package dev.rnap.reactnativeaudiopro

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRangeTest {
	@Test fun `termine immediatement si la replanification arrive a la borne ou apres`() {
		for (position in listOf(45000L, 45001L)) {
			var finishes = 0
			var messages = 0
			val finished = dispatchPlaybackBoundary(position, 45000,
				onReached = { finishes++ }, onPending = { messages++ })
			assertEquals(true, finished)
			assertEquals(1, finishes)
			assertEquals(0, messages)
		}
	}

	@Test fun `rearme apres seek arriere puis termine sans attendre un message dans le passe`() {
		var finishes = 0
		var messages = 0
		assertEquals(false, dispatchPlaybackBoundary(35000, 45000,
			onReached = { finishes++ }, onPending = { messages++ }))
		// Une mise à jour de timeline remplace ce message après le passage de la borne.
		assertEquals(true, dispatchPlaybackBoundary(45010, 45000,
			onReached = { finishes++ }, onPending = { messages++ }))
		assertEquals(1, messages)
		assertEquals(1, finishes)
	}

	@Test fun `borne la reprise sans convertir les positions absolues`() {
		val range = PlaybackRange(30000, 45000)
		assertEquals(30000L, range.clamp(0, 90000))
		assertEquals(35000L, range.clamp(35000, 90000))
		assertEquals(45000L, range.clamp(80000, 90000))
	}

	@Test fun `garde la borne quand la duree est inconnue`() {
		assertEquals(45000L, PlaybackRange(30000, 45000).clamp(80000, -1))
	}

	@Test fun `respecte une source plus courte que la plage`() {
		assertEquals(10000L, PlaybackRange(30000, 45000).clamp(30000, 10000))
	}

	@Test fun `preserve les pistes sans plage`() {
		assertEquals(35000L, PlaybackRange().clamp(35000, 90000))
		assertEquals(0L, PlaybackRange().clamp(-1000, 90000))
	}
}
