package io.github.igormy.vocora.recorder;

// Transaction ids are explicit because Shizuku destroys user services through a fixed one, and AIDL
// requires either all methods to carry an id or none.
interface IVocoraRecorder {
    /** Records the call audio to outputPath for the given number of seconds. */
    void record(String outputPath, int seconds) = 1;

    /** Human readable result of the last run, for the smoke test to report. */
    String lastResult() = 2;

    /** Called by the Shizuku server when the user service is torn down. */
    void destroy() = 16777114;
}
