package io.github.igormy.vocora.recorder.logic

import io.github.igormy.vocora.recorder.logic.RecordingProgress.Shown
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingProgressTest {

    @Test
    fun `what the phone is doing is what shows until the recording is sent`() {
        assertEquals(Shown.NOT_UPLOADED, RecordingProgress.of(UploadState.PENDING, null))
        assertEquals(Shown.UPLOADING, RecordingProgress.of(UploadState.UPLOADING, null))
    }

    @Test
    fun `a refused upload stays refused whatever the server last said`() {
        assertEquals(Shown.FAILED, RecordingProgress.of(UploadState.FAILED, "done"))
    }

    @Test
    fun `once sent, the server has the say`() {
        assertEquals(Shown.WAITING, RecordingProgress.of(UploadState.UPLOADED, "pending"))
        assertEquals(Shown.TRANSCRIBING, RecordingProgress.of(UploadState.UPLOADED, "transcribing"))
        assertEquals(Shown.DONE, RecordingProgress.of(UploadState.UPLOADED, "done"))
        assertEquals(Shown.FAILED, RecordingProgress.of(UploadState.UPLOADED, "failed"))
    }

    @Test
    fun `embedding is still the server working on it`() {
        assertEquals(Shown.TRANSCRIBING, RecordingProgress.of(UploadState.UPLOADED, "embedding"))
    }

    @Test
    fun `a status this version does not know about says no more than uploaded`() {
        assertEquals(Shown.UPLOADED, RecordingProgress.of(UploadState.UPLOADED, "whatever"))
        assertEquals(Shown.UPLOADED, RecordingProgress.of(UploadState.UPLOADED, null))
    }
}
