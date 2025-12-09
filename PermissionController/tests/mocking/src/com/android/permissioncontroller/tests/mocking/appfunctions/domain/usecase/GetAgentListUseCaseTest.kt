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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.permissioncontroller.appfunctions.domain.model.AppFunctionPackageInfo
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAgentListUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAppFunctionPackageInfoUseCase
import com.android.permissioncontroller.tests.mocking.appfunctions.data.repository.FakeAppFunctionRepository
import com.android.permissioncontroller.tests.mocking.pm.data.repository.FakePackageRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

// TODO(b/424004217): Update this to the correct version code
/** Unit tests for [GetAgentListUseCase]. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
class GetAgentListUseCaseTest {
    @Test
    fun getAgentList_returnsAgentList() = runTest {
        val expectedAgents =
            agentPackagesAndLabels.map { AppFunctionPackageInfo(it.key, it.value, null) }
        val useCase =
            GetAgentListUseCase(
                FakeAppFunctionRepository(agents = agentPackageNames),
                GetAppFunctionPackageInfoUseCase(
                    FakePackageRepository(packagesAndLabels = agentPackagesAndLabels)
                ),
            )
        val actualAgents = useCase()
        assertThat(actualAgents).containsExactlyElementsIn(expectedAgents)
    }

    companion object {
        private const val TEST_AGENT_PACKAGE_NAME = "test.agent.package"
        private const val TEST_AGENT_PACKAGE_NAME2 = "test.agent.package2"
        private const val TEST_AGENT_LABEL = "Test Agent"
        private const val TEST_AGENT_LABEL2 = "Test Agent 2"
        private val agentPackageNames = listOf(TEST_AGENT_PACKAGE_NAME, TEST_AGENT_PACKAGE_NAME2)
        private val agentPackagesAndLabels =
            mapOf(
                TEST_AGENT_PACKAGE_NAME to TEST_AGENT_LABEL,
                TEST_AGENT_PACKAGE_NAME2 to TEST_AGENT_LABEL2,
            )
    }
}
