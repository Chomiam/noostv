package io.noostv

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import io.noostv.core.device.DeviceDetector
import io.noostv.core.device.DeviceType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDetectorTest {

    @Test
    fun `when system has leanback feature should detect TELEVISION`() {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()

        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns null
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns true
        every { packageManager.hasSystemFeature("android.hardware.type.television") } returns false

        val detector = DeviceDetector(context)
        assertEquals(DeviceType.TELEVISION, detector.deviceType)
        assertTrue(detector.isTv)
    }

    @Test
    fun `when screen width is 400dp should detect PHONE`() {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val resources = mockk<Resources>()
        val config = Configuration().apply { smallestScreenWidthDp = 400 }

        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns null
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
        every { packageManager.hasSystemFeature("android.hardware.type.television") } returns false
        every { context.resources } returns resources
        every { resources.configuration } returns config

        val detector = DeviceDetector(context)
        assertEquals(DeviceType.PHONE, detector.deviceType)
    }

    @Test
    fun `when screen width is 800dp should detect TABLET`() {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val resources = mockk<Resources>()
        val config = Configuration().apply { smallestScreenWidthDp = 800 }

        every { context.getSystemService(Context.UI_MODE_SERVICE) } returns null
        every { context.packageManager } returns packageManager
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
        every { packageManager.hasSystemFeature("android.hardware.type.television") } returns false
        every { context.resources } returns resources
        every { resources.configuration } returns config

        val detector = DeviceDetector(context)
        assertEquals(DeviceType.TABLET, detector.deviceType)
    }
}
