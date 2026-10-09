package io.github.igormy.vocora.recorder.logic

/** How far a recording has got towards being sent. */
enum class UploadState {
    /** Waiting its turn, which is where every recording starts. */
    PENDING,

    /** Being sent right now, or left this way by a run that did not finish. */
    UPLOADING,

    /** The server has it. What happens to it from there is the server's business. */
    UPLOADED,

    /** The server refused it, and asking again would be refused the same way. */
    FAILED,
}

/**
 * What the list says about a recording, out of what the phone did and what the server answered.
 *
 * Two sources, one line: until the recording leaves the phone only the queue has anything to say,
 * and from then on the server does.
 */
object RecordingProgress {

    enum class Shown {
        NOT_UPLOADED,
        UPLOADING,

        /** Sent, but the server has not been asked how it is going yet. */
        UPLOADED,

        /** The server has it and has not started. */
        WAITING,
        TRANSCRIBING,
        DONE,
        FAILED,
    }

    fun of(uploadState: UploadState, serverStatus: String?): Shown = when (uploadState) {
        UploadState.PENDING -> Shown.NOT_UPLOADED
        UploadState.UPLOADING -> Shown.UPLOADING
        UploadState.FAILED -> Shown.FAILED
        UploadState.UPLOADED -> when (serverStatus) {
            "pending" -> Shown.WAITING
            // Embedding is still the server chewing on it, and one word for both is enough here.
            "transcribing", "embedding" -> Shown.TRANSCRIBING
            "done" -> Shown.DONE
            "failed" -> Shown.FAILED
            // Not asked yet, or a status this version does not know about.
            else -> Shown.UPLOADED
        }
    }
}
