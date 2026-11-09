package com.example.obdy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateInfo(val code: Int, val name: String, val url: String, val notes: String, val size: Long, val published: String)

/**
 * Looks for a newer APK in the GitHub Releases of the app's own repository, keeps the download on the phone
 * (in the app's private "updates" folder) and installs it over the old version whenever you press Install.
 */
object Updater {
    private val codeRe = Regex("build(\\d+)\\.apk$")

    fun installedCode(ctx: Context): Int = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode.toInt()
    } catch (e: Exception) { 0 }

    fun codeOf(name: String): Int? = codeRe.find(name)?.groupValues?.get(1)?.toIntOrNull()

    fun dir(ctx: Context): File = File(ctx.filesDir, "updates").also { it.mkdirs() }

    /** The newest downloaded update that is newer than the installed app, or null. Older and partial files are deleted. */
    fun ready(ctx: Context): Pair<File, Int>? {
        val installed = installedCode(ctx)
        var best: Pair<File, Int>? = null
        for (f in dir(ctx).listFiles() ?: emptyArray()) {
            val c = codeOf(f.name)
            if (c == null) { if (f.name.endsWith(".part")) f.delete(); continue }
            if (c <= installed) { f.delete(); continue }
            val b = best
            if (b == null || c > b.second) { b?.first?.delete(); best = f to c } else f.delete()
        }
        return best
    }

    fun onUnmetered(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork
        val c = if (n == null) null else cm.getNetworkCapabilities(n)
        c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    } catch (e: Exception) { false }

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
        if (code == 404) throw IllegalStateException("No update published yet. Run Rebuild-APK.bat or Publish-Update.bat on the laptop once.")
        if (code !in 200..299) throw IllegalStateException("GitHub answered HTTP $code.")
        val o = JSONObject(text)
        val assets = o.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val remote = codeOf(name) ?: continue
            if (remote > installedCode(ctx)) {
                return UpdateInfo(remote, name, a.getString("browser_download_url"), o.optString("body"), a.optLong("size"), o.optString("published_at").take(10))
            }
            return null
        }
        return null
    }

    /** Downloads into the updates folder (kept until installed). Checks the size, so a cut connection never leaves a broken file. */
    fun download(ctx: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File {
        val d = dir(ctx)
        d.listFiles()?.forEach { if (it.name.endsWith(".part")) it.delete() }
        val part = File(d, info.name + ".part")
        val c = URL(info.url).openConnection() as HttpURLConnection
        c.connectTimeout = 20_000; c.readTimeout = 60_000
        c.instanceFollowRedirects = true
        val total = c.contentLengthLong.takeIf { it > 0 } ?: info.size
        var done = 0L
        c.inputStream.use { ins ->
            part.outputStream().use { os ->
                val buf = ByteArray(32 * 1024); var last = -1
                while (true) {
                    val n = ins.read(buf); if (n < 0) break
                    os.write(buf, 0, n); done += n
                    if (total > 0) { val p = (done * 100 / total).toInt(); if (p != last) { last = p; onProgress(p) } }
                }
            }
        }
        if (total > 0 && done != total) { part.delete(); throw IllegalStateException("The download was cut off. Try again.") }
        val out = File(d, info.name)
        d.listFiles()?.forEach { if (it.name.endsWith(".apk") && it.name != info.name) it.delete() }
        if (out.exists()) out.delete()
        if (!part.renameTo(out)) { part.delete(); throw IllegalStateException("Could not save the update.") }
        return out
    }

    private fun signatures(p: PackageInfo): Set<String> = try {
        if (Build.VERSION.SDK_INT >= 28) (p.signingInfo?.apkContentsSigners ?: emptyArray()).map { it.toCharsString() }.toSet()
        else @Suppress("DEPRECATION") (p.signatures ?: emptyArray()).map { it.toCharsString() }.toSet()
    } catch (e: Exception) { emptySet() }

    /** A plain-words reason why Android would refuse this APK, or null when it looks installable. */
    fun problem(ctx: Context, apk: File): String? {
        try {
            val pm = ctx.packageManager
            @Suppress("DEPRECATION")
            val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return "The saved file is not a valid APK. Check for updates to download it again."
            if (archive.packageName != ctx.packageName) return "The saved file belongs to a different app."
            val installed = pm.getPackageInfo(ctx.packageName, flags)
            val a = signatures(archive); val b = signatures(installed)
            if (a.isNotEmpty() && b.isNotEmpty() && a != b) {
                return "This update is signed with a different key than the installed app, so Android would refuse it. Uninstall Obdy once and install the new APK from the laptop (your data can be restored from a backup)."
            }
        } catch (_: Exception) { }
        return null
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
