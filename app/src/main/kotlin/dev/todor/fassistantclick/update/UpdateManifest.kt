package dev.todor.fassistantclick.update

import org.json.JSONObject
import java.net.URL

/**
 * What `:app:dist` writes next to the APK, and the only thing the app fetches from the network.
 *
 * [apkUrl] is resolved against the manifest's own address, so a relative "fassistant-click.apk"
 * in the manifest points at the APK sitting beside it — which is what makes the same manifest
 * work from a GitHub release and from a laptop on the same network.
 */
internal data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
) {
    companion object {
        fun parse(text: String, fetchedFrom: String): UpdateManifest {
            val json = JSONObject(text)
            return UpdateManifest(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = URL(URL(fetchedFrom), json.getString("apkUrl")).toString(),
                sha256 = json.getString("sha256").lowercase(),
                notes = json.optString("notes"),
            )
        }
    }
}
