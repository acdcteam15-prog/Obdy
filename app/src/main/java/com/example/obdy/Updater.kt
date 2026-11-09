package com.example.obdy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateInfo(val code: Int, val name: String, val url: String, val notes: String, val size: Long)

/** Looks for a newer APK in the GitHub Releases of the app's own repository and installs it over the old one. */
object Updater {
    fun installedCode(ctx: Context): Int = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
    } catch (e: Exception) { 0 }

    /** Returns the newer release, or null when this build is already the newest. Blocking: call from a background thread. */
    fun check(ctx: Context): UpdateInfo? {
        val repo = BuildConfig.UPDATE_REPO
        if (repo.startsWith("CHANGE_ME") || !repo.contains("/")) {
            throw IllegalStateException("This build does not know its GitHub repository yet. Build it through GitHub Actions (Rebuild-APK.bat) and install that APK.")
        }
        val c = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
        if (code == 404) throw IllegalStateException("No release published yet. Run Publish-Update.bat once on the laptop.")
        if (code !in 200..299) throw IllegalStateException("GitHub answered HTTP $code.")
        val o = JSONObject(text)
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val m = Regex("build(\\d+)\\.apk$").find(name) ?: continue
            val remote = m.groupValues[1].toInt()
            if (remote > installedCode(ctx)) {
                return UpdateInfo(remote, name, a.getString("browser_download_url"), o.optString("body"), a.optLong("size"))
            }
            return null
        }
        return null
    }

    fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "apks").also { it.mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, info.name)
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        c.instanceFollowRedirects = true
        val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
        c.inputStream.use { ins ->
            out.outputStream().use { os ->
                val buf = ByteArray(32 * 1024); var done = 0L; var last = -1
                while (true) {
                    val n = ins.read(buf); if (n < 0) break
                    os.write(buf, 0, n); done += n
                    if (total > 0) { val p = (done * 100 / total).toInt(); if (p != last) { last = p; onProgress(p) } }
                }
            }
        }
        return out
    }

    /** Hands the APK to Android's installer. Sends you to the "allow installs" switch first if needed. */
    fun install(ctx: Context, apk: File): Boolean {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
