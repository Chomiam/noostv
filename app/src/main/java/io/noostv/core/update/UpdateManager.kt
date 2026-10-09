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
     * Vérifie la disponibilité d'une nouvelle version sur GitHub selon le canal sélectionné (stable ou testing).
     * Le dépôt étant public, aucun jeton n'est requis par défaut.
     */
    suspend fun checkForUpdates(
        channel: String,
        token: String = "",
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

            val isNewer = isVersionNewer(targetRelease.version, currentVersion)

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
        var bestRelease: ReleaseInfo? = null

        for (elem in releases) {
            val obj = elem.asJsonObject
            val targetCommitish = obj.get("target_commitish")?.asString ?: "stable"
            val isPrerelease = obj.get("prerelease")?.asBoolean ?: false
            val tagName = obj.get("tag_name")?.asString ?: ""
            val name = obj.get("name")?.asString ?: tagName
            val body = obj.get("body")?.asString ?: ""
            val publishedAt = obj.get("published_at")?.asString ?: ""

            // Filtrage par canal :
            // - testing : toutes les releases sont acceptées (pré-releases testing ET stables les plus récentes)
            // - stable  : uniquement les versions stables officielles (!isPrerelease)
            val matches = if (isTesting) {
                true
            } else {
                !isPrerelease && (targetCommitish == "stable" || targetCommitish == "master")
            }

            if (!matches) continue

            // Recherche de l'asset APK (privilégie browser_download_url pour téléchargement public direct)
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
                        val browserUrl = assetObj.get("browser_download_url")?.asString
                        val apiUrl = assetObj.get("url")?.asString
                        apkUrl = if (!browserUrl.isNullOrBlank()) browserUrl else apiUrl
                        break
                    }
                }
            }

            if (apkUrl.isNullOrBlank()) continue

            val cleanVersion = tagName.removePrefix("v").trim()
            val candidate = ReleaseInfo(
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

            // Garde la version la plus haute parmi les éligibles
            val currentBest = bestRelease
            if (currentBest == null || isVersionNewer(candidate.version, currentBest.version)) {
                bestRelease = candidate
            }
        }
        return bestRelease
    }

    /**
     * Télécharge l'APK de mise à jour depuis GitHub (public ou privé).
     */
    suspend fun downloadApk(
        release: ReleaseInfo,
        token: String = "",
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val downloadUrl = release.apkDownloadUrl 
            ?: return@withContext Result.failure(Exception("Aucun fichier APK trouvé dans la release"))

        try {
            val reqBuilder = Request.Builder()
                .url(downloadUrl)

            // Si c'est une URL de l'API REST privée, on spécifie l'accept octet-stream et le token.
            // Si c'est un browser_download_url public (github.com/releases/download/...),
            // AUCUN header d'authentification ne doit être envoyé (sinon rejet HTTP 400 par AWS S3 après redirection).
            if (downloadUrl.contains("api.github.com")) {
                reqBuilder.header("Accept", "application/octet-stream")
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
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
     * Vérifie si l'application dispose de l'autorisation d'installer des paquets inconnus (Android 8.0+).
     */
    fun canInstallPackages(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Ouvre directement la page des paramètres système pour autoriser l'installation d'applications inconnues pour NoosTV.
     */
    fun openInstallPermissionSettings() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback pour les ROMs Android TV ne supportant pas l'URI direct
                val fallbackIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            }
        }
    }

    /**
     * Valide l'intégrité de l'archive APK téléchargée puis lance l'installateur de paquets d'Android.
     * Rejette et supprime les fichiers corrompus, incomplets ou dont l'applicationId ne correspond pas.
     */
    sealed class InstallResult {
        object Success : InstallResult()
        object PermissionRequired : InstallResult()
        data class Error(val message: String) : InstallResult()
    }

    /**
     * Valide l'intégrité de l'archive APK téléchargée puis lance l'installateur de paquets d'Android.
     * Rejette et supprime les fichiers corrompus, incomplets ou dont l'applicationId ne correspond pas.
     */
    fun installApk(apkFile: File): Boolean {
        return installApkWithResult(apkFile) is InstallResult.Success
    }

    fun installApkWithResult(apkFile: File): InstallResult {
        if (!apkFile.exists() || apkFile.length() < 1024 * 100) {
            android.util.Log.e("UpdateManager", "Fichier APK manquant ou taille invalide (< 100 Ko)")
            return InstallResult.Error("Fichier APK invalide ou incomplet")
        }

        // Vérification de la structure du paquet via l'analyseur natif du PackageManager
        val pm = context.packageManager
        val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
        if (archiveInfo == null) {
            android.util.Log.e("UpdateManager", "PackageArchiveInfo nul : APK corrompu ou altéré")
            apkFile.delete()
            return InstallResult.Error("Fichier APK corrompu ou altéré")
        }

        // Vérification stricte du package applicatif
        if (archiveInfo.packageName != context.packageName) {
            android.util.Log.e("UpdateManager", "Alerte sécurité : PackageName de l'APK rejeté (${archiveInfo.packageName} != ${context.packageName})")
            apkFile.delete()
            return InstallResult.Error("Alerte sécurité : Le paquet ne correspond pas à NoosTV")
        }

        // Vérification contre le downgrade pour éviter INSTALL_FAILED_VERSION_DOWNGRADE
        val currentPkgInfo = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }

        val currentVersionCode = if (currentPkgInfo != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                currentPkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                currentPkgInfo.versionCode.toLong()
            }
        } else 0L

        val archiveVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }

        if (archiveVersionCode > 0 && currentVersionCode > 0 && archiveVersionCode < currentVersionCode) {
            android.util.Log.e("UpdateManager", "Refus d'installation : downgrade ($archiveVersionCode < $currentVersionCode)")
            apkFile.delete()
            return InstallResult.Error("Impossible d'installer : la version téléchargée (build $archiveVersionCode) est plus ancienne que la version installée (build $currentVersionCode).")
        }

        // Si l'autorisation d'installer des sources inconnues n'est pas encore accordée sur Android 8+, ouvrir les paramètres
        if (!canInstallPackages()) {
            android.util.Log.w("UpdateManager", "Permission REQUEST_INSTALL_PACKAGES manquante, redirection vers les paramètres")
            openInstallPermissionSettings()
            return InstallResult.PermissionRequired
        }

        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            context.startActivity(intent)
            InstallResult.Success
        } catch (e: Exception) {
            android.util.Log.e("UpdateManager", "Échec lors du lancement de l'intent d'installation", e)
            InstallResult.Error("Erreur d'installation : ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Compare deux versions numériques (ex: "1.1.0" vs "1.0.1", "1.2.9-beta.1" vs "1.2.8")
     */
    fun isVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
        try {
            val rClean = remoteVersion.trim().removePrefix("v")
            val cClean = currentVersion.trim().removePrefix("v")
            if (rClean == cClean) return false

            val rBase = rClean.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
            val cBase = cClean.substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }

            val maxLen = maxOf(rBase.size, cBase.size)
            for (i in 0 until maxLen) {
                val r = rBase.getOrElse(i) { 0 }
                val c = cBase.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }

            // Les composantes numériques de base sont identiques (ex: 1.2.8 vs 1.2.8-beta.1)
            val rHasPre = rClean.contains("-")
            val cHasPre = cClean.contains("-")

            if (!rHasPre && cHasPre) {
                // Version finale stable plus récente que la pré-release (ex: 1.2.8 > 1.2.8-beta.1)
                return true
            }
            if (rHasPre && !cHasPre) {
                // Pré-release plus ancienne que la version finale de même numéro
                return false
            }
            if (rHasPre && cHasPre) {
                // Deux pré-releases sur le même numéro de base (ex: 1.2.8-beta.2 vs 1.2.8-beta.1)
                val rSuffix = rClean.substringAfter("-")
                val cSuffix = cClean.substringAfter("-")
                val rNum = Regex("\\d+").find(rSuffix)?.value?.toIntOrNull() ?: 0
                val cNum = Regex("\\d+").find(cSuffix)?.value?.toIntOrNull() ?: 0
                if (rNum != cNum) {
                    return rNum > cNum
                }
                return rSuffix > cSuffix
            }

            return false
        } catch (e: Exception) {
            return false
        }
    }
}
