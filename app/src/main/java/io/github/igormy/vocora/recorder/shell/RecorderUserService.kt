package io.github.igormy.vocora.recorder.shell

import android.app.PendingIntent
import android.content.Context
import android.util.Log
import io.github.igormy.vocora.recorder.IVocoraRecorder
import io.github.igormy.vocora.recorder.logic.CallLogEntry
import io.github.igormy.vocora.recorder.logic.BlacklistMatch
import io.github.igormy.vocora.recorder.logic.CallIdentity
import io.github.igormy.vocora.recorder.logic.PhoneNumbers
import io.github.igormy.vocora.recorder.logic.RecordingName
import java.io.File
import java.util.Date
import kotlin.system.exitProcess

private const val TAG = "VocoraRecorder"

/** How long to wait for the call log to catch up after a call ends, before giving up on the number. */
private const val CALL_LOG_TIMEOUT_MILLIS = 3_000L
private const val CALL_LOG_POLL_MILLIS = 250L

/**
 * Records every call, inside the process Shizuku runs with the shell uid.
 *
 * This lives here rather than in the app because the app cannot be relied on: HyperOS kills it in
 * the background and does not deliver broadcasts to it while it is stopped. This process is started
 * by the Shizuku server, so Android's background limits do not reach it.
 */
class RecorderUserService() : IVocoraRecorder.Stub() {

    /** Shizuku hands a context to the user service when the constructor asks for one. */
    private var baseContext: Context? = null

    constructor(context: Context) : this() {
        baseContext = context
    }

    private val context: Context by lazy { ShellContext(baseContext ?: systemContext()) }
    private val recorder: CallAudioRecorder by lazy { CallAudioRecorder(context) }
    private val watcher: CallWatcher by lazy { CallWatcher(context) }
    private val notifier: RecordingNotifier by lazy { RecordingNotifier(context) }

    private var outputDirectory: File? = null
    private var currentFolder: File? = null
    private var callLogDateBeforeCall: Long = 0

    @Volatile
    private var watching = false

    @Volatile
    private var blacklistNumbers: List<String> = emptyList()

    @Volatile
    private var blacklistNames: List<String> = emptyList()

    @Volatile
    private var currentCall = CallIdentity(null, null)

    @Volatile
    private var result: String = "nothing recorded yet"

    override fun setBlacklist(numbers: List<String>?, names: List<String>?) {
        blacklistNumbers = numbers.orEmpty()
        blacklistNames = names.orEmpty()
        Log.i(TAG, "blacklist: ${blacklistNumbers.size} numbers, ${blacklistNames.size} names")
    }

    /**
     * The dialer's notification arrives a moment after the audio mode says a call is up, so by the
     * time anyone knows who it is with the recording has already started. Checking here as well as
     * at the start means a blacklisted call is dropped within that second rather than kept.
     */
    override fun setCurrentCall(number: String?, name: String?) {
        currentCall = CallIdentity(number, name)
        if (!recorder.isRecording) return
        if (!BlacklistMatch.isBlocked(currentCall, blacklistNumbers, blacklistNames)) return

        Log.i(TAG, "identified as ${name ?: number}, which is blacklisted")
        discardRecording()
        // Says why nothing is being kept, and stays up for as long as the call does.
        notifier.showBlocked(name ?: number)
    }

    override fun isWatching(): Boolean = watching

    override fun isRecording(): Boolean = recorder.isRecording

    override fun lastResult(): String = result

    /**
     * What to fire once a call is over, so the app can send it.
     *
     * Uploads happen in the app, which is usually not running while a call is recorded. Firing this
     * starts it, the same way the notification's cancel button does.
     */
    private var callEndedAction: PendingIntent? = null

    override fun setCallEndedAction(action: PendingIntent?) {
        callEndedAction = action
    }

    override fun startWatching(outputDirectory: String, cancelAction: PendingIntent?) {
        notifier.cancelAction = cancelAction
        if (watching) return
        this.outputDirectory = File(outputDirectory).apply { mkdirs() }
        watching = true

        if (!watcher.start(::onCallStarted, ::onCallEnded)) {
            watching = false
            result = "FAILED: call detection could not be set up, calls will not be recorded"
            Log.w(TAG, result)
            return
        }
        Log.i(TAG, "watching for calls, recordings go to $outputDirectory")
    }

    override fun stopWatching() {
        if (!watching) return
        watching = false
        watcher.stop()
        discardRecording()
        Log.i(TAG, "stopped watching")
    }

    /** Cancels this call's recording from the notification, and keeps watching for the next call. */
    override fun cancelCurrentRecording() {
        // Taken down whatever the state: having pressed cancel and still seeing the notification
        // reads as the button being broken, which is how this looked while it was.
        notifier.hide()
        discardRecording()
    }

    /**
     * Drops a recording that was still in progress, file included.
     *
     * Stopping a recording half way is a decision about that call too, so leaving a truncated file
     * behind would be keeping something the user just asked not to keep. The encoder is stopped
     * first so the file is closed before it is deleted.
     */
    @Synchronized
    private fun discardRecording() {
        if (!recorder.isRecording) return
        recorder.stop()
        notifier.hide()
        val folder = currentFolder
        currentFolder = null
        val deleted = folder?.deleteRecursively() ?: false
        result = "recording dropped" + if (folder != null && !deleted) {
            ", but ${folder.name} could not be deleted"
        } else {
            ""
        }
        Log.i(TAG, result)
    }

    @Synchronized
    private fun onCallStarted() {
        if (recorder.isRecording) return
        val directory = outputDirectory ?: return

        // Known only when the notification listener is on. Without it the call is recorded and
        // dropped at hang up instead, which ends the same way with a file in between.
        if (BlacklistMatch.isBlocked(currentCall, blacklistNumbers, blacklistNames)) {
            result = "not recording: ${currentCall.name ?: currentCall.number} is blacklisted"
            Log.i(TAG, result)
            notifier.showBlocked(currentCall.name ?: currentCall.number)
            return
        }

        // Remembered so that the entry this call adds can be told apart from the previous one.
        callLogDateBeforeCall = CallLogReader.latest()?.date ?: 0

        val folder = File(directory, RecordingName.forStart(Date()))
        currentFolder = folder
        result = if (recorder.start(folder)) {
            notifier.show()
            "recording ${folder.name}"
        } else {
            currentFolder = null
            recorder.lastResult
        }
    }

    @Synchronized
    private fun onCallEnded() {
        // Taken down whether a recording was running or the call was one Vocora refused to keep.
        notifier.hide()
        currentCall = CallIdentity(null, null)
        if (!recorder.isRecording) return
        recorder.stop()

        val folder = currentFolder
        currentFolder = null
        if (folder == null) return

        // Who it was with is only known now, so a blacklisted call is recorded and then dropped.
        val entry = awaitNewCallLogEntry()
        if (entry != null && PhoneNumbers.isListed(entry.number, blacklistNumbers)) {
            val deleted = folder.deleteRecursively()
            result = "dropped: ${entry.number} is blacklisted" +
                if (deleted) "" else ", but ${folder.name} could not be deleted"
            Log.i(TAG, result)
            return
        }

        val named = entry?.let { rename(folder, it) } ?: folder
        currentFolder = named
        result = "${recorder.lastResult} → ${named.name}"

        // There is something to upload now, and only the app can upload it.
        runCatching { callEndedAction?.send() }
            .onFailure { Log.i(TAG, "could not wake the app: ${it.javaClass.simpleName}") }
    }

    /**
     * The call log only learns about a call once it is over, and with a small delay, so who it was
     * with is added by renaming. Without it the date alone is still a usable name.
     */
    private fun rename(folder: File, entry: CallLogEntry): File {
        if (!folder.exists()) return folder
        val renamed = File(
            folder.parentFile,
            RecordingName.withCallDetails(folder.name, entry.number, entry.direction),
        )
        return if (folder.renameTo(renamed)) renamed else folder
    }

    private fun awaitNewCallLogEntry(): CallLogEntry? {
        var waited = 0L
        while (waited < CALL_LOG_TIMEOUT_MILLIS) {
            val entry = CallLogReader.latest()
            if (entry != null && entry.date != callLogDateBeforeCall) return entry
            Thread.sleep(CALL_LOG_POLL_MILLIS)
            waited += CALL_LOG_POLL_MILLIS
        }
        Log.i(TAG, "the call log did not show the call in time, keeping the date name")
        return null
    }

    /** Falls back to the system context when Shizuku built the service without one. */
    private fun systemContext(): Context {
        val activityThread = Class.forName("android.app.ActivityThread")
        val main = activityThread.getMethod("systemMain").invoke(null)
        return activityThread.getMethod("getSystemContext").invoke(main) as Context
    }

    override fun destroy() {
        stopWatching()
        exitProcess(0)
    }
}
