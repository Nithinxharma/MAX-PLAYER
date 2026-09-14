package xyz.mpv.rex.cinehub.failover

import android.content.Context
import android.util.Log
import android.widget.Toast
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controller responsible for automatic, seamless player failover.
 *
 * Catches playback errors (HTTP 403, 404, EOF before end, network drops)
 * and infinite buffering (>10s), automatically switching to the next candidate
 * stream from the provider's array and seeking back to the exact saved timestamp
 * without exiting the player UI.
 */
class StreamFailoverManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onStreamSwitched: ((StreamCandidate) -> Unit)? = null,
) {
    companion object {
        private const val TAG = "StreamFailoverManager"
        private const val BUFFER_STALL_TIMEOUT_MS = 10_000L // 10 seconds
    }

    private var candidateStreams: MutableList<StreamCandidate> = mutableListOf()
    private var currentIndex: Int = 0

    @Volatile
    var lastPositionSeconds: Double = 0.0
        private set

    @Volatile
    var isFailingOver: Boolean = false
        private set

    private var bufferWatchdogJob: Job? = null
    private var isBuffering: Boolean = false

    /**
     * Initializes or resets the candidate streams pool.
     */
    fun setupCandidates(primary: StreamCandidate, backups: List<StreamCandidate>) {
        candidateStreams.clear()
        candidateStreams.add(primary)
        backups.forEach { b ->
            if (candidateStreams.none { it.url == b.url }) {
                candidateStreams.add(b)
            }
        }
        currentIndex = 0
        lastPositionSeconds = 0.0
        isFailingOver = false
        cancelBufferWatchdog()
        Log.d(TAG, "Initialized failover manager with ${candidateStreams.size} stream candidates")
    }

    /**
     * Continuously records playback position.
     */
    fun updatePlaybackPosition(seconds: Double) {
        if (seconds > 0.5) {
            lastPositionSeconds = seconds
            // Once playback advances, reset failing over flag
            if (isFailingOver) {
                isFailingOver = false
            }
        }
    }

    /**
     * Notifies the failover manager of buffering state changes (e.g. paused-for-cache property).
     */
    fun onBufferingStateChanged(buffering: Boolean) {
        isBuffering = buffering
        if (buffering) {
            startBufferWatchdog()
        } else {
            cancelBufferWatchdog()
        }
    }

    /**
     * Triggers failover to the next candidate if available.
     *
     * @param reason Diagnostic reason for the failover
     * @return true if a next candidate was loaded, false if all candidates are exhausted
     */
    fun triggerFailover(reason: String): Boolean {
        cancelBufferWatchdog()

        if (currentIndex + 1 >= candidateStreams.size) {
            Log.w(TAG, "Failover requested ($reason) but no remaining backup candidates. Candidates count: ${candidateStreams.size}")
            return false
        }

        currentIndex++
        val nextCandidate = candidateStreams[currentIndex]
        val savedPos = lastPositionSeconds
        isFailingOver = true

        Log.i(TAG, "Triggering auto-failover to candidate #$currentIndex [${nextCandidate.quality}]: ${nextCandidate.url} (Reason: $reason, ResumeAt: ${savedPos}s)")

        scope.launch(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Connection failed: Switching to backup stream (${nextCandidate.quality})...",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Apply headers for the next candidate before loading the file
        applyCandidateHeaders(nextCandidate)

        // Command MPV to load the backup stream
        scope.launch(Dispatchers.Default) {
            runCatching {
                MPVLib.command("loadfile", nextCandidate.url)
            }.onFailure { e ->
                Log.e(TAG, "Failed to send loadfile command to MPV for failover: ${e.message}")
            }
        }

        onStreamSwitched?.invoke(nextCandidate)
        return true
    }

    /**
     * Called by PlayerActivity when MPV reports MPV_EVENT_FILE_LOADED.
     * If we were failing over, seeks immediately to the saved timestamp.
     */
    fun onFileLoadedAfterFailover() {
        if (lastPositionSeconds > 2.0) {
            val targetSec = lastPositionSeconds
            Log.i(TAG, "Restoring playback timestamp to ${targetSec}s after seamless failover")
            scope.launch(Dispatchers.Default) {
                // Give MPV a brief moment to initialize the demuxer
                delay(150)
                runCatching {
                    MPVLib.command("seek", targetSec.toString(), "absolute")
                }
            }
        }
        isFailingOver = false
    }

    /**
     * Injects candidate headers into MPV before loading media.
     */
    fun applyCandidateHeaders(candidate: StreamCandidate) {
        val userAgent = candidate.headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
        if (!userAgent.isNullOrBlank()) {
            runCatching { MPVLib.setPropertyString("user-agent", userAgent) }
        }

        val mpvHeaderFields = candidate.headers.entries
            .filter { !it.key.equals("User-Agent", ignoreCase = true) && it.value.isNotBlank() }
            .joinToString(",") { "${it.key}: ${it.value.replace(",", "\\,")}" }

        if (mpvHeaderFields.isNotBlank()) {
            Log.d(TAG, "Applying MPV http-header-fields: $mpvHeaderFields")
            runCatching { MPVLib.setPropertyString("http-header-fields", mpvHeaderFields) }
        }
    }

    /**
     * Gets the currently active stream candidate.
     */
    fun getCurrentCandidate(): StreamCandidate? {
        return candidateStreams.getOrNull(currentIndex)
    }

    private fun startBufferWatchdog() {
        cancelBufferWatchdog()
        bufferWatchdogJob = scope.launch(Dispatchers.Main) {
            delay(BUFFER_STALL_TIMEOUT_MS)
            if (isBuffering && !isFailingOver) {
                Log.w(TAG, "Player stalled in buffer for > ${BUFFER_STALL_TIMEOUT_MS / 1000}s! Triggering auto-failover.")
                triggerFailover("Infinite buffer timeout (>10s)")
            }
        }
    }

    private fun cancelBufferWatchdog() {
        bufferWatchdogJob?.cancel()
        bufferWatchdogJob = null
    }

    fun release() {
        cancelBufferWatchdog()
        candidateStreams.clear()
    }
}
