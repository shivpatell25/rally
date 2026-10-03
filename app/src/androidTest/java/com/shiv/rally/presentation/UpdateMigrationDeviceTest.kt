package com.shiv.rally.presentation

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Opt-in physical-device update check. Never installs/removes an app itself. */
class UpdateMigrationDeviceTest {
    private val production = "79ffff57b611ec7fbbc690007196df16dfd658432dd452458c56108bae55df73"
    private val oldDebug = "211711b801aa9c4ec006b57254ff4e8a0e623dedee4baa8ab1d1d63f9be1425b"
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    @Test fun verifyArchiveAndOpenShippingInstaller() {
        val args = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(args.getString("rallyMigrationInstall") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "updates/migration.apk")
        assertTrue(file.exists())
        val archive = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)!!
        assertEquals(context.packageName, archive.packageName)
        assertEquals(production, digest(archive.signingInfo!!.apkContentsSigners.single().toByteArray()))
        assertTrue(archive.signingInfo!!.signingCertificateHistory.any { digest(it.toByteArray()) == oldDebug })
        val before = JSONObject().put("uid", context.applicationInfo.uid)
        val prefs = JSONObject()
        File(context.applicationInfo.dataDir, "shared_prefs").listFiles()?.filter { it.extension == "xml" }?.forEach {
            prefs.put(it.name, digest(it.readBytes()))
        }
        before.put("preferences", prefs)
        File(context.filesDir, "stabilization-update-marker").writeText("same-data-after-signing-migration")
        File(context.getExternalFilesDir(null), "stabilization-before.json").writeText(before.toString())
        // Exercise the currently installed shipping updater, not a test substitute.
        val dns = Class.forName("com.shiv.rally.data.remote.network.ResilientDns").getDeclaredConstructor().newInstance()
        val diagnostics = Class.forName("com.shiv.rally.data.local.RallyDiagnostics").getDeclaredConstructor(Context::class.java).newInstance(context)
        val type = Class.forName("com.shiv.rally.data.update.RallyUpdateManager")
        val manager = type.constructors.single().newInstance(context, dns, diagnostics)
        type.getDeclaredMethod("verifyRallyPackage", File::class.java).apply { isAccessible = true }.invoke(manager, file)
        val releaseType = Class.forName("com.shiv.rally.data.update.RallyRelease")
        val release = releaseType.constructors.first { it.parameterCount == 8 }.newInstance(
            "v1.0-beta12", "Rally Stabilization", "Local verified migration", "https://github.com/shivpatell25/rally/releases",
            "rally-v1.0-beta12-android-tv.apk", "https://github.com/shivpatell25/rally/releases/download/v1.0-beta12/rally-v1.0-beta12-android-tv.apk",
            file.length(), null)
        type.getDeclaredMethod("install", releaseType, File::class.java).invoke(manager, release, file)
        // Instrumentation force-stops its target when it finishes, revoking URI
        // grants. Keep it alive while the real installer copies the cached APK.
        Thread.sleep(15_000)
    }
}
