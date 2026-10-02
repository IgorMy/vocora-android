package io.github.igormy.vocora.recorder.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoragePathTest {

    @Test
    fun `resolves a folder on internal storage`() {
        assertEquals(
            "/storage/emulated/0/Recordings/Vocora",
            StoragePath.ofTreeDocumentId("primary:Recordings/Vocora"),
        )
    }

    @Test
    fun `resolves the root of internal storage`() {
        assertEquals("/storage/emulated/0", StoragePath.ofTreeDocumentId("primary:"))
    }

    @Test
    fun `drops a trailing separator`() {
        assertEquals("/storage/emulated/0/Vocora", StoragePath.ofTreeDocumentId("primary:Vocora/"))
    }

    @Test
    fun `an SD card cannot be resolved to a path the recorder can write to`() {
        assertNull(StoragePath.ofTreeDocumentId("1D03-2E0F:Vocora"))
    }

    @Test
    fun `an id without a volume is rejected`() {
        assertNull(StoragePath.ofTreeDocumentId("Vocora"))
    }
}
