package com.savan.drishyam

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads a release APK privately, validates its published digest and package identity,
 * then hands it to Android's package installer for the user's explicit approval.
 */
internal object VerifiedUpdateInstaller {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var downloading = false

    fun downloadAndInstall(activity: Activity, release: VerifiedReleaseApk, status: (String) -> Unit = {}) {
        synchronized(this) {
            if (downloading) {
                status("A Drishyam update download is already running.")
                return
            }
            downloading = true
        }
        status("Downloading update securely…")
        Thread {
            var temp: File? = null
            try {
                val dir = File(activity.cacheDir, "verified_updates")
                if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Couldn't create a private update folder.")
                temp = File.createTempFile("drishyam-${release.version}-", ".part", dir)
                val connection = URL(release.url).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "Drishyam-Android")
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IllegalStateException("Download server returned HTTP ${connection.responseCode}.")
                    }
                    val expectedLength = connection.contentLengthLong
                    var total = 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(temp).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                total += n
                                if (total > 200L * 1024L * 1024L) throw IllegalStateException("APK is unexpectedly large.")
                                output.write(buffer, 0, n)
                                if (expectedLength > 0L) {
                                    val percent = ((total * 100L) / expectedLength).toInt().coerceIn(0, 100)
                                    main.post {
                                        if (!activity.isFinishing && !activity.isDestroyed) status("Downloading update securely… $percent%")
                                    }
                                }
                            }
                        }
                    }
                    if (expectedLength > 0 && total != expectedLength) throw IllegalStateException("Download was incomplete. Please retry.")
                } finally { connection.disconnect() }

                if (!release.verifyFile(temp)) throw SecurityException("APK checksum verification failed. The file was not opened for installation.")
                val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
                val archive = activity.packageManager.getPackageArchiveInfo(temp.absolutePath, flags)
                    ?: throw SecurityException("Android couldn't validate this APK.")
                if (archive.packageName != activity.packageName) throw SecurityException("This APK is not the Drishyam app.")
                if (archive.versionName != release.version) throw SecurityException("APK version doesn't match the selected release.")
                val installed = activity.packageManager.getPackageInfo(activity.packageName, flags)
                if (archive.longVersionCode <= installed.longVersionCode) {
                    throw SecurityException("This APK is not newer than the installed Drishyam version.")
                }
                if (Build.VERSION.SDK_INT >= 28) {
                    val installedInfo = installed.signingInfo ?: throw SecurityException("Installed app signing identity couldn't be checked.")
                    val archiveInfo = archive.signingInfo ?: throw SecurityException("Downloaded app signing identity couldn't be checked.")
                    val installedCerts = installedInfo.apkContentsSigners
                    val archiveCerts = archiveInfo.apkContentsSigners
                    val matches = if (installedInfo.hasMultipleSigners() || archiveInfo.hasMultipleSigners()) {
                        installedCerts.size == archiveCerts.size && installedCerts.all { installedSigner ->
                            archiveCerts.any { it.toByteArray().contentEquals(installedSigner.toByteArray()) }
                        }
                    } else {
                        val installedSigner = installedCerts.singleOrNull()
                        val archiveSigner = archiveCerts.singleOrNull()
                        installedSigner != null && archiveSigner != null && (
                            archiveSigner.toByteArray().contentEquals(installedSigner.toByteArray()) ||
                                archiveInfo.signingCertificateHistory.any {
                                    it.toByteArray().contentEquals(installedSigner.toByteArray())
                                }
                        )
                    }
                    if (!matches) throw SecurityException("APK signing identity doesn't match the installed app. Installation was blocked.")
                }

                val verified = File(dir, "Drishyam-${release.version}-verified.apk")
                if (verified.exists() && !verified.delete()) throw IllegalStateException("Couldn't replace an older temporary update.")
                if (!temp.renameTo(verified)) throw IllegalStateException("Couldn't prepare the verified APK.")
                temp = null
                main.post {
                    try {
                        if (activity.isFinishing || activity.isDestroyed) return@post
                        if (Build.VERSION.SDK_INT >= 26 && !activity.packageManager.canRequestPackageInstalls()) {
                            status("Allow Drishyam to install updates in Android settings, then return and tap Download again.")
                            val settings = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${activity.packageName}"))
                            activity.startActivity(settings)
                            return@post
                        }
                        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", verified)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/vnd.android.package-archive")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        activity.startActivity(intent)
                        activity.getSharedPreferences("drishyam_update_state", android.content.Context.MODE_PRIVATE)
                            .edit().putString("pending_whats_new_version", release.version).apply()
                        status("APK verified. Android will ask you to approve installation.")
                    } catch (e: Exception) {
                        status("APK verified, but Android couldn't open the installer: ${e.message ?: "try again"}.")
                    } finally {
                        synchronized(this) { downloading = false }
                    }
                }
            } catch (e: Exception) {
                temp?.delete()
                synchronized(this) { downloading = false }
                val message = e.message ?: "Update download failed. Please try again."
                main.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        status(message)
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }.start()
    }
}
