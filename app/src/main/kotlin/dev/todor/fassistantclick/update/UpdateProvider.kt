package dev.todor.fassistantclick.update

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Lets Android's install screen read a downloaded update, and nothing else.
 *
 * An install intent has to carry a content:// address: this app targets 25, where a file:// one
 * throws, and FileProvider lives in AndroidX, which this app does not use. Only files named like an
 * update are served, and only to whoever the intent granted read access.
 */
class UpdateProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
        ParcelFileDescriptor.open(apk(uri), ParcelFileDescriptor.MODE_READ_ONLY)

    override fun getType(uri: Uri) = MIME_TYPE

    // Some installers ask for the name and size before reading.
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor {
        val file = apk(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
            .apply { addRow(arrayOf<Any>(file.name, file.length())) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    private fun apk(uri: Uri): File {
        val name = uri.lastPathSegment?.takeIf { UPDATE_NAME.matches(it) } ?: throw FileNotFoundException(uri.toString())
        val file = File(context!!.cacheDir, name)
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        return file
    }

    companion object {
        const val MIME_TYPE = "application/vnd.android.package-archive"

        // Matches what UpdateInstaller.download writes, and nothing else in the cache.
        private val UPDATE_NAME = Regex("""update-\d+\.apk""")

        fun uriFor(context: Context, apk: File): Uri = Uri.parse("content://${context.packageName}.updates/${apk.name}")
    }
}
