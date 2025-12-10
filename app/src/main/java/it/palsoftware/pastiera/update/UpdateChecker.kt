package it.palsoftware.pastiera.update

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import it.palsoftware.pastiera.R
import it.palsoftware.pastiera.SettingsManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

const val GITHUB_RELEASES_URL =
    "https://api.github.com/repos/palsoftware/pastiera/releases/latest"
const val GITHUB_LATEST_RELEASE_PAGE =
    "https://github.com/palsoftware/pastiera/releases/latest"

private val client = OkHttpClient()
private val mainHandler = Handler(Looper.getMainLooper())

fun checkForUpdate(
    context: Context,
    currentVersion: String,
    ignoreDismissedReleases: Boolean = true,
    callback: (hasUpdate: Boolean, latestVersion: String?, downloadUrl: String?) -> Unit
) {
    val request = Request.Builder()
        .url(GITHUB_RELEASES_URL)
        .header("Accept", "application/vnd.github+json")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            postResult(callback, false, null, null)
        }

        override fun onResponse(call: Call, response: Response) {
            response.use { res ->
                if (!res.isSuccessful) {
                    postResult(callback, false, null, null)
                    return
                }

                val body = res.body?.string().orEmpty()
                if (body.isBlank()) {
                    postResult(callback, false, null, null)
                    return
                }

                val json = JSONObject(body)
                val latestVersion = json
                    .optString("tag_name")
                    .takeIf(String::isNotBlank)

                if (latestVersion == null) {
                    postResult(callback, false, null, null)
                    return
                }

                val downloadUrl = json
                    .optJSONArray("assets")
                    ?.let { assets ->
                        var url: String? = null
                        for (index in 0 until assets.length()) {
                            val asset = assets.optJSONObject(index) ?: continue
                            val name = asset.optString("name", "")
                            if (name.lowercase().endsWith(".apk")) {
                                url = asset.optString("browser_download_url")
                                if (url.isNotBlank()) {
                                    break
                                }
                            }
                        }
                        url?.takeIf(String::isNotBlank)
                    }

                // Normalize versions by removing "v" prefix if present
                val normalizedLatest = latestVersion.removePrefix("v").removePrefix("V")
                val normalizedCurrent = currentVersion.removePrefix("v").removePrefix("V")
                
                val hasUpdate = normalizedLatest != normalizedCurrent
                
                // If ignoring dismissed releases, check if this release was dismissed
                if (hasUpdate && ignoreDismissedReleases) {
                    val isDismissed = SettingsManager.isReleaseDismissed(context, latestVersion)
                    if (isDismissed) {
                        // Release was dismissed, don't show update
                        postResult(callback, false, null, null)
                        return
                    }
                }
                
                postResult(callback, hasUpdate, latestVersion, downloadUrl)
            }
        }
    })
}

private fun postResult(
    callback: (Boolean, String?, String?) -> Unit,
    hasUpdate: Boolean,
    latestVersion: String?,
    downloadUrl: String?
) {
    mainHandler.post {
        callback(hasUpdate, latestVersion, downloadUrl)
    }
}

fun showUpdateDialog(context: Context, latestVersion: String, downloadUrl: String?) {
    val builder = AlertDialog.Builder(context)
        .setTitle(R.string.update_dialog_title)
        .setMessage(context.getString(R.string.update_dialog_message, latestVersion))
        .setPositiveButton(R.string.update_dialog_open_github) { _, _ ->
            openUrl(context, GITHUB_LATEST_RELEASE_PAGE)
        }
        .setNeutralButton(R.string.update_dialog_later) { _, _ ->
            // Save dismissed release when user clicks "Later"
            SettingsManager.addDismissedRelease(context, latestVersion)
        }

    if (downloadUrl != null) {
        builder.setNegativeButton(R.string.update_dialog_download_apk) { _, _ ->
            openUrl(context, downloadUrl)
        }
    }

    builder.create().show()
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
