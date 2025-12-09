/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.permissioncontroller.tests.mocking.appfunctions.domain.usecase

import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.appfunctions.domain.usecase.GetDeviceSettingsTargetIconUseCase
import com.android.permissioncontroller.tests.mocking.pm.data.repository.FakePackageRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

// TODO(b/424004217): Update this to the correct version code
/** Unit tests for [GetDeviceSettingsTargetIconUseCase]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
class GetDeviceSettingsTargetIconUseCaseUseCaseTest {
    @Mock private lateinit var icon: Drawable

    private var mockitoSession: MockitoSession? = null

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession().strictness(Strictness.LENIENT).startMocking()
    }

    @After
    fun finish() {
        mockitoSession?.finishMocking()
    }

    @Test
    fun getDeviceSettingsTargetIcon_getSettingsPackageNameNull_returnsNull() = runTest {
        val useCase =
            GetDeviceSettingsTargetIconUseCase(
                FakePackageRepository(packageIcons = mapOf(TEST_PACKAGE_NAME to icon))
            )
        val actualIcon = useCase(Process.myUserHandle())
        assertThat(actualIcon).isNull()
    }

    @Test
    fun getDeviceSettingsTargetIcon_getSettingsPackageIconNull_returnsNull() = runTest {
        val useCase =
            GetDeviceSettingsTargetIconUseCase(
                FakePackageRepository(settingsPackageName = TEST_PACKAGE_NAME)
            )
        val actualIcon = useCase(Process.myUserHandle())
        assertThat(actualIcon).isNull()
    }

    @Test
    fun getDeviceSettingsTargetIcon_returnsSettingsPackageIcon() = runTest {
        val useCase =
            GetDeviceSettingsTargetIconUseCase(
                FakePackageRepository(
                    packageIcons = mapOf(TEST_PACKAGE_NAME to icon),
                    settingsPackageName = TEST_PACKAGE_NAME,
                )
            )
        val actualIcon = useCase(Process.myUserHandle())
        assertThat(actualIcon).isEqualTo(icon)
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "test.package"
    }
}
