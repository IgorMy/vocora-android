package io.github.igormy.vocora.recorder.logic

private const val PRIMARY_VOLUME = "primary"
private const val PRIMARY_VOLUME_PATH = "/storage/emulated/0"

/**
 * Turns the document id of a picked folder into a path on disk.
 *
 * The recorder writes as the shell user, so it cannot use the permission that comes with the picked
 * tree URI: that permission belongs to the app. It needs a plain path instead.
 */
object StoragePath {

    /** Resolves ids like `primary:Recordings/Vocora`. Returns null for other volumes. */
    fun ofTreeDocumentId(documentId: String): String? {
        val parts = documentId.split(":", limit = 2)
        if (parts.size != 2) return null
        val (volume, relativePath) = parts
        if (volume != PRIMARY_VOLUME) return null
        if (relativePath.isBlank()) return PRIMARY_VOLUME_PATH
        return "$PRIMARY_VOLUME_PATH/$relativePath".trimEnd('/')
    }
}
