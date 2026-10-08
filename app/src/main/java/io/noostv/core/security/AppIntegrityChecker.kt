package io.noostv.core.security

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import io.noostv.BuildConfig
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Rapport d'intégrité de l'environnement d'exécution NoosTV.
 */
data class IntegrityReport(
    val isCompromised: Boolean,
    val fridaDetected: Boolean,
    val debuggerDetected: Boolean,
    val rootDetected: Boolean,
    val issues: List<String>
)

/**
 * Module d'évaluation de la sécurité de l'environnement applicatif.
 * Détecte les tentatives d'instrumentation dynamique par IA (Frida, Xposed),
 * l'attachement d'un débogueur en production et les traces d'élévation root.
 */
object AppIntegrityChecker {
    private const val TAG = "AppIntegrityChecker"
    private const val FRIDA_DEFAULT_PORT = 27042

    private val SU_PATHS = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/su/bin/su"
    )

    /**
     * Analyse l'environnement actuel et retourne un rapport d'intégrité.
     * Cette vérification est conçue pour être ultralégère (< 10ms) et sans blocage réseau.
     */
    fun checkIntegrity(context: Context): IntegrityReport {
        val issues = mutableListOf<String>()

        // 1. Détection Frida / Instrumentation dynamique
        val fridaFound = checkFridaInMemory() || checkFridaPort()
        if (fridaFound) {
            issues.add("Instrumentation dynamique détectée (Frida/Xposed)")
        }

        // 2. Détection du débogueur (uniquement actif hors mode Debug pour les développeurs)
        val debuggerFound = if (!BuildConfig.DEBUG) {
            Debug.isDebuggerConnected()
        } else false
        if (debuggerFound) {
            issues.add("Débogueur actif attaché au processus")
        }

        // 3. Détection de traces Root ou builds non officiels
        val rootFound = checkRootBinaries() || checkTestKeys()
        if (rootFound) {
            issues.add("Environnement rooté ou build avec test-keys")
        }

        val isCompromised = fridaFound || debuggerFound

        if (isCompromised) {
            Log.w(TAG, "Alerte de sécurité d'intégrité applicative: ${issues.joinToString(", ")}")
        }

        return IntegrityReport(
            isCompromised = isCompromised,
            fridaDetected = fridaFound,
            debuggerDetected = debuggerFound,
            rootDetected = rootFound,
            issues = issues
        )
    }

    /**
     * Vérifie si le serveur Frida écoute sur son port par défaut en local.
     */
    private fun checkFridaPort(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", FRIDA_DEFAULT_PORT), 30)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Parcourt la table de mappage mémoire `/proc/self/maps` pour repérer l'injection
     * de bibliothèques d'instrumentation (ex: frida-agent.so, xposed).
     */
    private fun checkFridaInMemory(): Boolean {
        val mapsFile = File("/proc/self/maps")
        if (!mapsFile.exists() || !mapsFile.canRead()) return false

        return try {
            BufferedReader(FileReader(mapsFile)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val lower = line.lowercase()
                    if (lower.contains("frida") || lower.contains("gadget.so") || lower.contains("xposed")) {
                        return true
                    }
                    line = reader.readLine()
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Vérifie la présence de binaires 'su' connus.
     */
    private fun checkRootBinaries(): Boolean {
        for (path in SU_PATHS) {
            try {
                if (File(path).exists()) return true
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Vérifie si l'OS est une version de développement interne signée 'test-keys'.
     */
    private fun checkTestKeys(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }
}
