package io.noostv.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val title: String,
    val notes: String,
    val targetBranch: String,
    val isPrerelease: Boolean,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long
) {
    val apkSizeMb: Double
        get() = if (apkSizeBytes > 0) apkSizeBytes / (1024.0 * 1024.0) else 0.0
}

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpToDate(val version: String, val channel: String) : UpdateState()
    data class UpdateAvailable(val release: ReleaseInfo, val currentVersion: String, val channel: String) : UpdateState()
    data class Downloading(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class UpdateManager(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val REPO_OWNER = "Chomiam"
        const val REPO_NAME = "noostv"
        const val GITHUB_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"
    }

    /**
     * Vérifie la disponibilité d'une nouvelle version sur GitHub selon le canal sélectionné (stable ou testing)
     */
    suspend fun checkForUpdates(
        channel: String,
        token: String,
        currentVersion: String = io.noostv.BuildConfig.VERSION_NAME
    ): UpdateState = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")

            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext UpdateState.Error("Erreur GitHub API (${response.code}) : ${response.message}")
            }

            val body = response.body?.string() ?: return@withContext UpdateState.Error("Réponse GitHub vide")
            val jsonArray = JsonParser.parseString(body).asJsonArray

            val targetRelease = findMatchingRelease(jsonArray, channel)
                ?: return@withContext UpdateState.UpToDate(currentVersion, channel)

            val isNewer = isVersionNewer(targetRelease.version, currentVersion) || 
                (channel.equals("testing", ignoreCase = true) && targetRelease.isPrerelease && targetRelease.version != currentVersion)

            if (isNewer) {
                UpdateState.UpdateAvailable(
                    release = targetRelease,
                    currentVersion = currentVersion,
                    channel = channel
                )
            } else {
                UpdateState.UpToDate(currentVersion, channel)
            }
        } catch (e: Exception) {
            UpdateState.Error("Impossible de vérifier les mises à jour : ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    private fun findMatchingRelease(releases: JsonArray, channel: String): ReleaseInfo? {
        val isTesting = channel.equals("testing", ignoreCase = true)

        for (elem in releases) {
            val obj = elem.asJsonObject
            val targetCommitish = obj.get("target_commitish")?.asString ?: "stable"
            val isPrerelease = obj.get("prerelease")?.asBoolean ?: false
            val tagName = obj.get("tag_name")?.asString ?: ""
            val name = obj.get("name")?.asString ?: tagName
            val body = obj.get("body")?.asString ?: ""
            val publishedAt = obj.get("published_at")?.asString ?: ""

            // Filtrage par canal
            val matches = if (isTesting) {
                targetCommitish == "testing" || isPrerelease
            } else {
                !isPrerelease && (targetCommitish == "stable" || targetCommitish == "master")
            }

            if (matches) {
                // Recherche de l'asset APK
                var apkUrl: String? = null
                var apkName: String? = null
                var apkSize: Long = 0

                val assets = obj.getAsJsonArray("assets")
                if (assets != null) {
                    for (assetElem in assets) {
                        val assetObj = assetElem.asJsonObject
                        val aName = assetObj.get("name")?.asString ?: ""
                        if (aName.endsWith(".apk", ignoreCase = true)) {
                            apkName = aName
                            apkSize = assetObj.get("size")?.asLong ?: 0
                            // URL de téléchargement de l'asset
                            apkUrl = assetObj.get("url")?.asString ?: assetObj.get("browser_download_url")?.asString
                            break
                        }
                    }
                }

                val cleanVersion = tagName.removePrefix("v").trim()
                return ReleaseInfo(
                    tag = tagName,
                    version = cleanVersion,
                    title = name,
                    notes = body,
                    targetBranch = targetCommitish,
                    isPrerelease = isPrerelease,
                    publishedAt = publishedAt,
                    apkDownloadUrl = apkUrl,
                    apkName = apkName,
                    apkSizeBytes = apkSize
                )
            }
        }
        return null
    }

    /**
     * Télécharge l'APK de mise à jour depuis GitHub
     */
    suspend fun downloadApk(
        release: ReleaseInfo,
        token: String,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadUrl = release.apkDownloadUrl 
            ?: return@withContext Result.failure(Exception("Aucun fichier APK trouvé dans la release"))

        try {
            val reqBuilder = Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/octet-stream")

            if (token.isNotBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Échec téléchargement (${response.code})"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Corps de réponse vide"))
            val totalBytes = if (release.apkSizeBytes > 0) release.apkSizeBytes else body.contentLength()

            val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val outputFile = File(outputDir, release.apkName ?: "noostv-update.apk")

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var totalDownloaded = 0L

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        totalDownloaded += read
                        val percent = if (totalBytes > 0) {
                            ((totalDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else 50
                        onProgress(percent, totalDownloaded, totalBytes)
                    }
                    output.flush()
                }
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lance l'installateur de paquets d'Android pour mettre à jour l'APK
     */
    fun installApk(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Compare deux versions numériques (ex: "1.1.0" vs "1.0.1")
     */
    fun isVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val rParts = remoteVersion.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }
            val cParts = currentVersion.split("-")[0].split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(rParts.size, cParts.size)
            for (i in 0 until maxLen) {
                val r = rParts.getOrElse(i) { 0 }
                val c = cParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        } catch (e: Exception) {
            return remoteVersion != currentVersion
        }
    }
}
