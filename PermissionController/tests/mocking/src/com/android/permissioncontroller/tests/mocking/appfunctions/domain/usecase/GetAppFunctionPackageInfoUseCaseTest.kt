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

import android.os.Build
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.permissioncontroller.appfunctions.domain.model.AppFunctionPackageInfo
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAppFunctionPackageInfoUseCase
import com.android.permissioncontroller.tests.mocking.pm.data.repository.FakePackageRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

// TODO(b/424004217): Update this to the correct version code
/** Unit tests for [GetAppFunctionPackageInfoUseCase]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
class GetAppFunctionPackageInfoUseCaseTest {
    @Test
    fun getAppFunctionPackageInfo_returnsAppFunctionPackageInfo() = runTest {
        val expectedPackageInfo = AppFunctionPackageInfo(TEST_PACKAGE_NAME, TEST_LABEL, null)
        val useCase =
            GetAppFunctionPackageInfoUseCase(
                FakePackageRepository(packagesAndLabels = packagesAndLabels)
            )
        val actualPackageInfo = useCase(TEST_PACKAGE_NAME, Process.myUserHandle())
        assertThat(actualPackageInfo).isEqualTo(expectedPackageInfo)
    }

    companion object {
        private const val TEST_PACKAGE_NAME = "test.agent.package"
        private const val TEST_LABEL = "Test Agent"
        private val packagesAndLabels = mapOf(TEST_PACKAGE_NAME to TEST_LABEL)
    }
}
