package dev.todor.fassistantclick.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fetches a candidate APK and refuses to hand it to Android unless two things hold: the bytes
 * match the checksum in the manifest, and the APK is signed by the same key as the running app.
 *
 * The second check is the one that matters. Android would reject a differently signed update
 * anyway, but doing it here means the failure is a sentence on screen instead of an opaque
 * installer error.
 */
internal object UpdateInstaller {

    private const val TIMEOUT_MS = 20_000

    fun fetchText(address: String): String = open(address).use { stream ->
        stream.reader().readText()
    }

    fun download(context: Context, manifest: UpdateManifest): File {
        val target = File(context.cacheDir, "update-${manifest.versionCode}.apk")
        open(manifest.apkUrl).use { stream ->
            target.outputStream().use { out -> stream.copyTo(out) }
        }
        return target
    }

    fun checksumMatches(apk: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == expected.lowercase()
    }

    fun signedLikeUs(context: Context, apk: File): Boolean {
        @Suppress("DEPRECATION")
        val flag = PackageManager.GET_SIGNATURES

        @Suppress("DEPRECATION")
        val candidate = context.packageManager
            .getPackageArchiveInfo(apk.absolutePath, flag)
            ?.signatures

        @Suppress("DEPRECATION")
        val running = context.packageManager
            .getPackageInfo(context.packageName, flag)
            .signatures

        if (candidate.isNullOrEmpty() || running.isNullOrEmpty()) return false
        return fingerprints(candidate) == fingerprints(running)
    }

    /**
     * Opens Android's own install screen on the APK, which asks the user and does the install.
     *
     * Not a PackageInstaller session, although that is the newer route. Xiaomi's Android, with its
     * default "MIUI optimization" on, refuses sessions from ordinary apps with
     * "INSTALL_FAILED_INTERNAL_ERROR: Permission Denied", and the only cure on the phone is a
     * developer setting. The install screen is allowed there, and works the same everywhere else.
     * It reads the APK through [UpdateProvider].
     */
    fun handOver(context: Context, apk: File) {
        val install = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(UpdateProvider.uriFor(context, apk), UpdateProvider.MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(install)
    }

    private fun fingerprints(signatures: Array<Signature>): Set<String> =
        signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()

    private fun open(address: String) = (URL(address).openConnection() as HttpURLConnection).run {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        instanceFollowRedirects = true
        if (responseCode !in 200..299) {
            disconnect()
            error("HTTP $responseCode")
        }
        inputStream
    }
}
