/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.permissionui.cts

import android.content.pm.PackageManager
import android.health.connect.HealthPermissions
import android.os.Build
import android.permission.flags.Flags.FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Runtime permission behavior tests for permission splits. */
@FlakyTest
class PermissionSplitTest : BaseUsePermissionTest() {

    companion object {
        @JvmStatic
        private val supportHeartrate =
            packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HEART_RATE)
    }

    @Rule @JvmField val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Before
    fun assumeNotTv() {
        assumeFalse(isTv)
    }

    @Test
    fun testPermissionSplit28() {
        installPackage(APP_APK_PATH_28)
        testLocationPermissionSplit(true)
    }

    @Test
    fun testPermissionNotSplit29() {
        installPackage(APP_APK_PATH_29)
        testLocationPermissionSplit(false)
    }

    @Test
    fun testPermissionNotSplit30() {
        installPackage(APP_APK_PATH_30)
        testLocationPermissionSplit(false)
    }

    @Test
    fun testPermissionNotSplitLatest() {
        installPackage(APP_APK_PATH_LATEST)
        testLocationPermissionSplit(false)
    }

    // TODO: b/388596433 - Update maxSdkVersion to VANILLA_ICE_CREAM after SDK bumps.
    // TODO: b/383440585 - Remove this test when flag annotation issue is fixed.
    @SdkSuppress(
        minSdkVersion = Build.VERSION_CODES.TIRAMISU,
        maxSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    )
    @Test
    fun testBodySensorSplitOnTToU() {
        installPackage(APP_APK_PATH_31)
        testBodySensorPermissionSplitToBodySensorsBackground(true)
    }

    // Before SDK_INT bumps to 36, the in-development B images are using SDK_INT=35(V). This will
    // cause test failures on main builds where replaceBodySensor flag is enabled to remove Sensor
    // group UI. As a workaround, we move SDK_INT=35 tests out and requires replaceBodySensor flag
    // disabled when running on these images.
    // TODO: b/388596433 - Update minSdkVersion to BAKLAVA after SDK bumps.
    // TODO: b/383440585 - Update minSdkVersion to TIRAMISU when flag annotation issue is fixed.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @RequiresFlagsDisabled(FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    @Test
    fun testBodySensorSplitPostV_replaceBodySensorFlagDisabled() {
        installPackage(APP_APK_PATH_31)
        testBodySensorPermissionSplitToBodySensorsBackground(true)
    }

    // TODO: b/388596433 - Update maxSdkVersion to VANILLA_ICE_CREAM after SDK bumps.
    // TODO: b/383440585 - Remove this test when flag annotation issue is fixed.
    @SdkSuppress(
        minSdkVersion = Build.VERSION_CODES.TIRAMISU,
        maxSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    )
    @Test
    fun testBodySensorSplit32OnTToU() {
        installPackage(APP_APK_PATH_32)
        testBodySensorPermissionSplitToBodySensorsBackground(true)
    }

    // Before SDK_INT bumps to 36, the in-development B images are using SDK_INT=35(V). This will
    // cause test failures on main builds where replaceBodySensor flag is enabled to remove Sensor
    // group UI. As a workaround, we move SDK_INT=35 tests out and requires replaceBodySensor flag
    // disabled when running on these images.
    // TODO: b/388596433 - Update minSdkVersion to BAKLAVA after SDK bumps.
    // TODO: b/383440585 - Update minSdkVersion to TIRAMISU when flag annotation issue is fixed.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @RequiresFlagsDisabled(FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    @Test
    fun testBodySensorSplit32PostV_replaceBodySensorFlagDisabled() {
        installPackage(APP_APK_PATH_32)
        testBodySensorPermissionSplitToBodySensorsBackground(true)
    }

    // TODO: b/388596433 - Update maxSdkVersion to VANILLA_ICE_CREAM after SDK bumps.
    // TODO: b/383440585 - Remove this test when flag annotation issue is fixed.
    @SdkSuppress(
        minSdkVersion = Build.VERSION_CODES.TIRAMISU,
        maxSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    )
    @Test
    fun testBodySensorNonSplitOnTToU() {
        installPackage(APP_APK_PATH_LATEST)
        testBodySensorPermissionSplitToBodySensorsBackground(false)
    }

    // Before SDK_INT bumps to 36, the in-development B images are using SDK_INT=35(V). This will
    // cause test failures on main builds where replaceBodySensor flag is enabled to remove Sensor
    // group UI. As a workaround, we move SDK_INT=35 tests out and requires replaceBodySensor flag
    // disabled when running on these images.
    // TODO: b/388596433 - Update minSdkVersion to BAKLAVA after SDK bumps.
    // TODO: b/383440585 - Update minSdkVersion to TIRAMISU when flag annotation issue is fixed.
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    @RequiresFlagsDisabled(FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    @Test
    fun testBodySensorNonSplitPostV_replaceBodySensorFlagDisabled() {
        installPackage(APP_APK_PATH_LATEST)
        testBodySensorPermissionSplitToBodySensorsBackground(false)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "Baklava")
    @RequiresFlagsEnabled(FLAG_REPLACE_BODY_SENSOR_PERMISSION_ENABLED)
    @Test
    fun testBodySensorSplitOnBaklava_splitToReadHeartRate() {
        assumeTrue(supportHeartrate)
        installPackage(APP_APK_PATH_30_WITH_BACKGROUND)
        assertAppHasPermission(android.Manifest.permission.BODY_SENSORS, false)
        assertAppHasPermission(HealthPermissions.READ_HEART_RATE, false)
        assertAppHasPermission(android.Manifest.permission.BODY_SENSORS_BACKGROUND, false)
        assertAppHasPermission(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND, false)

        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.BODY_SENSORS to true,
            waitForWindowTransition = false,
        ) {
            clickAllowReadHeartRate()
        }

        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.BODY_SENSORS_BACKGROUND to true,
            waitForWindowTransition = false,
        ) {
            clickAlwaysAllowReadHealthDataInBackground()
        }

        assertAppHasPermission(android.Manifest.permission.BODY_SENSORS, true)
        assertAppHasPermission(HealthPermissions.READ_HEART_RATE, true)
        assertAppHasPermission(android.Manifest.permission.BODY_SENSORS_BACKGROUND, true)
        assertAppHasPermission(HealthPermissions.READ_HEALTH_DATA_IN_BACKGROUND, true)
    }

    private fun testLocationPermissionSplit(expectSplit: Boolean) {
        assertAppHasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION, false)
        assertAppHasPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION, false)

        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.ACCESS_FINE_LOCATION to true,
            waitForWindowTransition = false,
        ) {
            if (expectSplit) {
                clickPermissionRequestSettingsLinkAndAllowAlways()
            } else {
                if (isWatch) {
                    clickPermissionRequestAllowForegroundButton()
                } else {
                    doAndWaitForWindowTransition { clickPermissionRequestAllowForegroundButton() }
                }
            }
        }

        assertAppHasPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION, expectSplit)
    }

    private fun testBodySensorPermissionSplitToBodySensorsBackground(expectSplit: Boolean) {
        assumeTrue(supportHeartrate)
        assertAppHasPermission(android.Manifest.permission.BODY_SENSORS, false)
        assertAppHasPermission(android.Manifest.permission.BODY_SENSORS_BACKGROUND, false)

        requestAppPermissionsAndAssertResult(
            android.Manifest.permission.BODY_SENSORS to true,
            waitForWindowTransition = false,
        ) {
            if (expectSplit) {
                clickPermissionRequestSettingsLinkAndAllowAlways()
            } else {
                if (isWatch) {
                    clickPermissionRequestAllowForegroundButton()
                } else {
                    doAndWaitForWindowTransition { clickPermissionRequestAllowForegroundButton() }
                }
            }
        }

        assertAppHasPermission(android.Manifest.permission.BODY_SENSORS_BACKGROUND, expectSplit)
    }
}
