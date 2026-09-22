package dev.rnap.reactnativeaudiopro

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule

/**
 * AudioProAmbientController
 *
 * A completely isolated controller for ambient audio playback.
 * This controller is separate from the main AudioProController and does not
 * share any state, events, or resources with it.
 */
object AudioProAmbientController {
	private const val TAG = "[react-native-audio-pro]"
	private const val AMBIENT_EVENT_NAME = "AudioProAmbientEvent"
	private const val EVENT_TYPE_AMBIENT_TRACK_ENDED = "AMBIENT_TRACK_ENDED"
	private const val EVENT_TYPE_AMBIENT_ERROR = "AMBIENT_ERROR"

	private const val FADE_STEP_MS = 50L

	private var reactContext: ReactApplicationContext? = null
	private var enginePlayerAmbient: ExoPlayer? = null
	private var engineListenerAmbient: Player.Listener? = null
	private var settingDebugAmbient: Boolean = false
	private var settingLoopAmbient: Boolean = true
	private var settingVolumeAmbient: Float = 1.0f

	// Fade-out state for ambientPause({ holdMs, fadeMs }). The ramp runs on the
	// main looper (native Handler), so it keeps ticking while the app is
	// backgrounded — unlike JS timers, which React Native freezes on Android
	// when the host activity is paused. All access happens on the UI thread.
	private var fadeGainAmbient: Float = 1.0f
	private var fadeRunnableAmbient: Runnable? = null
	private val fadeHandlerAmbient = Handler(Looper.getMainLooper())

	/**
	 * Set the React context
	 */
	fun setReactContext(context: ReactApplicationContext?) {
		reactContext = context
	}

	/**
	 * Log a message if debug is enabled
	 */
	private fun log(vararg args: Any?) {
		if (settingDebugAmbient) {
			Log.d(TAG, "${args.joinToString(" ")}")
		}
	}

	/**
	 * Play an ambient audio track
	 */
	fun ambientPlay(options: ReadableMap) {
		val optionUrl = options.getString("url") ?: run {
			emitAmbientError("Invalid URL provided to ambientPlay()")
			return
		}

		if (options.hasKey("debug")) settingDebugAmbient = options.getBoolean("debug")

		val optionLoop = if (options.hasKey("loop")) options.getBoolean("loop") else true
		settingLoopAmbient = optionLoop

		// Log all options for debugging
		log(
			"Ambient options parsed:",
			"url=$optionUrl",
			"loop=$optionLoop"
		)

		// Stop any existing ambient playback
		ambientStop()

		// Create a new player
		val context = reactContext ?: run {
			emitAmbientError("React context is not set")
			return
		}

		runOnUiThread {
			enginePlayerAmbient = ExoPlayer.Builder(context).build().apply {
				// Set up player
				repeatMode =
					if (settingLoopAmbient) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

				// Set up listener
				engineListenerAmbient = object : Player.Listener {
					override fun onPlaybackStateChanged(state: Int) {
						if (state == Player.STATE_ENDED && !settingLoopAmbient) {
							// If playback ended and loop is disabled, emit event and clean up
							emitAmbientTrackEnded()
							ambientStop()
						}
					}

					override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
						emitAmbientError(error.message ?: "Unknown ambient playback error")
						ambientStop()
					}
				}
				addListener(engineListenerAmbient!!)

				// Prepare media item
				// Parse the URL string into a Uri object to properly handle all URI schemes including file://
				val uri = android.net.Uri.parse(optionUrl)
				log("Parsed ambient URI: $uri, scheme: ${uri.scheme}")

				val mediaItem = MediaItem.Builder()
					.setUri(uri)
					.build()

				setMediaItem(mediaItem)
				prepare()
				volume = settingVolumeAmbient
				play()
			}
		}
	}

	/**
	 * Stop ambient audio playback
	 */
	fun ambientStop() {
		log("Ambient Stop")

		runOnUiThread {
			cancelAmbientFade()
			enginePlayerAmbient?.let { exo ->
				engineListenerAmbient?.let { exo.removeListener(it) }
				exo.stop()
				exo.release()
			}
			enginePlayerAmbient = null
			engineListenerAmbient = null
		}
	}

	/**
	 * Pause ambient audio playback
	 * No-op if already paused or not playing
	 *
	 * Optional fade-out: `options` may carry `holdMs` (full volume hold before
	 * the ramp) and `fadeMs` (linear ramp duration down to silence). With no
	 * options (or both at 0), pauses immediately — previous behavior.
	 * A pending fade is cancelled by ambientResume/ambientPlay/ambientStop.
	 */
	fun ambientPause(options: ReadableMap?) {
		val holdMs =
			if (options?.hasKey("holdMs") == true) options.getDouble("holdMs").toLong() else 0L
		val fadeMs =
			if (options?.hasKey("fadeMs") == true) options.getDouble("fadeMs").toLong() else 0L
		log("Ambient Pause", "holdMs=$holdMs", "fadeMs=$fadeMs")

		runOnUiThread {
			cancelAmbientFade()
			val player = enginePlayerAmbient ?: return@runOnUiThread
			if (holdMs <= 0L && fadeMs <= 0L) {
				player.pause()
				return@runOnUiThread
			}
			startAmbientFadeOut(holdMs, fadeMs)
		}
	}

	/**
	 * Resume ambient audio playback
	 * No-op if already playing or no active track
	 * Cancels a pending fade-out and restores full volume before resuming.
	 */
	fun ambientResume() {
		log("Ambient Resume")

		runOnUiThread {
			cancelAmbientFade()
			enginePlayerAmbient?.play()
		}
	}

	/**
	 * Start the fade-out ramp: hold at full volume for holdMs, ramp linearly to
	 * silence over fadeMs, then pause the player and restore full volume (the
	 * player is paused at that point, so the restore is inaudible and the next
	 * resume starts at the user volume). Must be called on the UI thread.
	 */
	private fun startAmbientFadeOut(holdMs: Long, fadeMs: Long) {
		val startedAt = SystemClock.uptimeMillis()
		val step = object : Runnable {
			override fun run() {
				if (fadeRunnableAmbient !== this) return
				val player = enginePlayerAmbient ?: run {
					fadeRunnableAmbient = null
					fadeGainAmbient = 1.0f
					return
				}
				val elapsed = SystemClock.uptimeMillis() - startedAt
				val gain = when {
					elapsed < holdMs -> 1.0f
					fadeMs <= 0L -> 0.0f
					else -> (1.0f - (elapsed - holdMs).toFloat() / fadeMs).coerceIn(0.0f, 1.0f)
				}
				fadeGainAmbient = gain
				player.volume = settingVolumeAmbient * gain
				if (gain <= 0.0f) {
					fadeRunnableAmbient = null
					player.pause()
					fadeGainAmbient = 1.0f
					player.volume = settingVolumeAmbient
				} else {
					fadeHandlerAmbient.postDelayed(this, FADE_STEP_MS)
				}
			}
		}
		fadeRunnableAmbient = step
		fadeHandlerAmbient.post(step)
	}

	/**
	 * Cancel a pending fade-out and restore full volume.
	 * Must be called on the UI thread.
	 */
	private fun cancelAmbientFade() {
		fadeRunnableAmbient?.let { fadeHandlerAmbient.removeCallbacks(it) }
		fadeRunnableAmbient = null
		if (fadeGainAmbient != 1.0f) {
			fadeGainAmbient = 1.0f
			enginePlayerAmbient?.volume = settingVolumeAmbient
		}
	}

	/**
	 * Seek to position in ambient audio track
	 * Silently ignore if not supported or no active track
	 *
	 * @param positionMs Position in milliseconds
	 */
	fun ambientSeekTo(positionMs: Long) {
		log("Ambient Seek To", positionMs)

		runOnUiThread {
			enginePlayerAmbient?.seekTo(positionMs)
		}
	}

	/**
	 * Set the volume of ambient audio playback
	 */
	fun ambientSetVolume(volume: Float) {
		settingVolumeAmbient = volume
		log("Ambient Set Volume", volume)

		runOnUiThread {
			// Keep a running fade-out authoritative: apply the user volume
			// scaled by the current fade gain.
			enginePlayerAmbient?.volume = settingVolumeAmbient * fadeGainAmbient
		}
	}

	/**
	 * Emit an ambient track ended event
	 */
	private fun emitAmbientTrackEnded() {
		log("Ambient Track Ended")
		emitAmbientEvent(EVENT_TYPE_AMBIENT_TRACK_ENDED, null)
	}

	/**
	 * Emit an ambient error event
	 */
	private fun emitAmbientError(message: String) {
		log("Ambient Error:", message)

		val payload = Arguments.createMap().apply {
			putString("error", message)
		}

		emitAmbientEvent(EVENT_TYPE_AMBIENT_ERROR, payload)
	}

	/**
	 * Emit an ambient event
	 */
	private fun emitAmbientEvent(type: String, payload: WritableMap?) {
		val context = reactContext
		if (context is ReactApplicationContext) {
			val body = Arguments.createMap().apply {
				putString("type", type)

				if (payload != null) {
					putMap("payload", payload)
				}
			}

			context
				.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
				.emit(AMBIENT_EVENT_NAME, body)
		} else {
			Log.w(TAG, "Context is not an instance of ReactApplicationContext")
		}
	}

	/**
	 * Run a block on the UI thread
	 */
	private fun runOnUiThread(block: () -> Unit) {
		Handler(Looper.getMainLooper()).post(block)
	}
}
