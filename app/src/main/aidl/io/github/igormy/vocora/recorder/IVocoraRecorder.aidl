package io.github.igormy.vocora.recorder;

import android.app.PendingIntent;

// Transaction ids are explicit because Shizuku destroys user services through a fixed one, and AIDL
// requires either all methods to carry an id or none.
interface IVocoraRecorder {
    /**
     * Starts watching call state and recording every call into outputDirectory.
     *
     * cancelAction is what the notification's cancel button fires. It is built by the app and handed
     * over because a process started by Shizuku cannot receive broadcasts of its own: the
     * ActivityManager does not know it as an app process and has nowhere to deliver them.
     */
    void startWatching(String outputDirectory, in PendingIntent cancelAction) = 1;

    /** Stops watching. A recording in progress is finished first. */
    void stopWatching() = 2;

    /** Whether the service is watching for calls. */
    boolean isWatching() = 3;

    /** Whether a call is being recorded right now. */
    boolean isRecording() = 4;

    /** Human readable outcome of the last recording. */
    String lastResult() = 5;

    /** Stops recording this call and deletes what it recorded. Keeps watching for the next one. */
    void cancelCurrentRecording() = 6;

    /** Numbers whose calls are thrown away as soon as the call log says who it was. */
    void setBlacklist(in List<String> numbers) = 7;

    /** Called by the Shizuku server when the user service is torn down. */
    void destroy() = 16777114;
}
