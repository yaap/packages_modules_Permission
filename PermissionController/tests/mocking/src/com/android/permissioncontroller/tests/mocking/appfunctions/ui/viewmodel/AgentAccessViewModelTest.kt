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
package com.android.permissioncontroller.tests.mocking.appfunctions.ui.viewmodel

import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_USER_DENIED
import android.app.appfunctions.AppFunctionManager.ACCESS_FLAG_USER_GRANTED
import android.icu.text.Collator
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.R
import com.android.permissioncontroller.appfunctions.data.repository.AppFunctionRepository
import com.android.permissioncontroller.appfunctions.domain.model.AppFunctionPackageInfo
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAccessRequestStateUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAppFunctionPackageInfoUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.GetDeviceSettingsTargetIconUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.UpdateAccessUseCase
import com.android.permissioncontroller.appfunctions.ui.viewmodel.AgentAccessUiState
import com.android.permissioncontroller.appfunctions.ui.viewmodel.AgentAccessViewModel
import com.android.permissioncontroller.common.model.Stateful
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import com.android.permissioncontroller.tests.mocking.appfunctions.data.repository.FakeAppFunctionRepository
import com.android.permissioncontroller.tests.mocking.coroutines.collectLastValue
import com.android.permissioncontroller.tests.mocking.pm.data.repository.FakePackageRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations
import org.mockito.MockitoSession
import org.mockito.quality.Strictness

// TODO(b/424004217): Update this to the correct version code
/**
 * These unit tests are for app function agent access implementation, the view model class is
 * [AgentAccessViewModel]
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA)
@RunWith(AndroidJUnit4::class)
class AgentAccessViewModelTest {
    @Mock private lateinit var application: PermissionControllerApplication

    private lateinit var appFunctionRepository: AppFunctionRepository
    private lateinit var packageRepository: PackageRepository

    private var mockitoSession: MockitoSession? = null

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockitoSession =
            ExtendedMockito.mockitoSession()
                .mockStatic(PermissionControllerApplication::class.java)
                .strictness(Strictness.LENIENT)
                .startMocking()
        ExtendedMockito.doReturn(application).`when` { PermissionControllerApplication.get() }

        whenever(application.applicationContext).thenReturn(application)
        whenever(application.registerReceiverForAllUsers(any(), any(), any(), any()))
            .thenReturn(null)
        whenever(application.getString(R.string.app_function_device_settings_target_title))
            .thenReturn(TEST_DEVICE_SETTINGS_LABEL)

        appFunctionRepository =
            FakeAppFunctionRepository(
                agents = agentPackageNames,
                targets = systemTargetPackageNames + targetPackageNames,
                accessFlags = accessFlags,
            )
        packageRepository = FakePackageRepository(packagesAndLabels = packagesToLabelMap)
    }

    @After
    fun finish() {
        mockitoSession?.finishMocking()
    }

    @Test
    fun validAgentShown_emptySystemTargets_emptyAppTargets() = runTest {
        val testAgent = TEST_AGENT_PACKAGE_NAME

        // Set all appFunctionRepository with no accessFlags defined
        appFunctionRepository =
            FakeAppFunctionRepository(
                agents = agentPackageNames,
                targets = systemTargetPackageNames + targetPackageNames,
            )

        val viewModel = getViewModel(testAgent)
        val uiState = getAgentAccessUiState(viewModel)

        assertTrue(uiState is Stateful.Success)
        // Correct Agent AppFunctionPackageInfo returned
        assertThat(uiState.value!!.agent)
            .isEqualTo(AppFunctionPackageInfo(testAgent, packagesToLabelMap[testAgent]!!, null))

        // Correct Device Settings Target returned
        assertThat(uiState.value!!.deviceSettings).isNull()

        // Correct Targets returned
        assertThat(uiState.value!!.targets).isEmpty()
    }

    @Test
    fun validAgentShown_expectedSystemTargets_expectedAppTargets() = runTest {
        val testAgent = TEST_AGENT_PACKAGE_NAME
        val expectedAccessStates =
            accessFlags
                .filter { it.key.first == testAgent }
                .filter {
                    it.value == ACCESS_FLAG_USER_GRANTED || it.value == ACCESS_FLAG_USER_DENIED
                }
                .mapValues { it.value == ACCESS_FLAG_USER_GRANTED }
        val expectedTargets =
            targetPackageNames
                .filter { expectedAccessStates.containsKey(testAgent to it) }
                .map { AppFunctionPackageInfo(it, packagesToLabelMap[it]!!, null) }

        val viewModel = getViewModel(testAgent)
        val uiState = getAgentAccessUiState(viewModel)

        assertTrue(uiState is Stateful.Success)
        // Correct Agent AppFunctionPackageInfo returned
        assertThat(uiState.value!!.agent)
            .isEqualTo(AppFunctionPackageInfo(testAgent, packagesToLabelMap[testAgent]!!, null))

        // Correct Device Settings Target returned
        assertThat(uiState.value!!.deviceSettings).isNotNull()

        // Correct Targets returned
        assertThat(uiState.value!!.targets.map { it.packageInfo })
            .containsExactlyElementsIn(expectedTargets)
    }

    @Test
    fun updateDeviceSettingsAccessState() = runTest {
        val viewModel = getViewModel(TEST_AGENT_PACKAGE_NAME)

        viewModel.updateDeviceSettingsAccessState(false)
        assertThat(getAgentAccessUiState(viewModel).value!!.deviceSettings?.accessGranted ?: true)
            .isFalse()

        viewModel.updateDeviceSettingsAccessState(true)
        assertThat(getAgentAccessUiState(viewModel).value!!.deviceSettings?.accessGranted ?: false)
            .isTrue()
    }

    @Test
    fun updateAccessState() = runTest {
        val testTarget = TEST_TARGET_PACKAGE_NAME
        val viewModel = getViewModel(TEST_AGENT_PACKAGE_NAME)

        viewModel.updateAccessState(testTarget, false)
        assertThat(
                getAgentAccessUiState(viewModel)
                    .value!!
                    .targets
                    .find { it.packageInfo.packageName == testTarget }
                    ?.accessGranted ?: true
            )
            .isFalse()

        viewModel.updateAccessState(testTarget, true)
        assertThat(
                getAgentAccessUiState(viewModel)
                    .value!!
                    .targets
                    .find { it.packageInfo.packageName == testTarget }
                    ?.accessGranted ?: false
            )
            .isTrue()
    }

    private fun TestScope.getViewModel(agentPackageName: String): AgentAccessViewModel {
        return AgentAccessViewModel(
            application,
            agentPackageName,
            appFunctionRepository,
            GetDeviceSettingsTargetIconUseCase(packageRepository),
            GetAppFunctionPackageInfoUseCase(packageRepository),
            GetAccessRequestStateUseCase(appFunctionRepository),
            UpdateAccessUseCase(appFunctionRepository),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            Collator.getInstance(),
        )
    }

    private fun TestScope.getAgentAccessUiState(
        viewModel: AgentAccessViewModel
    ): Stateful<AgentAccessUiState> {
        val result by collectLastValue(viewModel.uiStateFlow)
        return result!!
    }

    companion object {
        private const val TEST_AGENT_PACKAGE_NAME = "test.agent.package"
        private const val TEST_AGENT_PACKAGE_NAME2 = "test.agent.package2"
        private const val DEVICE_SETTINGS_TARGET_PACKAGE_NAME = "android"
        private const val TEST_TARGET_PACKAGE_NAME = "test.target.package"
        private const val TEST_TARGET_PACKAGE_NAME2 = "test.target.package2"
        private const val TEST_TARGET_PACKAGE_NAME3 = "test.target.package3"
        private const val TEST_AGENT_LABEL = "Test Agent"
        private const val TEST_AGENT_LABEL2 = "Test Agent 2"
        private const val TEST_DEVICE_SETTINGS_LABEL = "Device Settings"
        private const val TEST_TARGET_LABEL = "Test Target"
        private const val TEST_TARGET_LABEL2 = "Test Target 2"
        private const val TEST_TARGET_LABEL3 = "Test Target 3"
        private val agentPackageNames = listOf(TEST_AGENT_PACKAGE_NAME, TEST_AGENT_PACKAGE_NAME2)
        private val systemTargetPackageNames = listOf(DEVICE_SETTINGS_TARGET_PACKAGE_NAME)
        private val targetPackageNames =
            listOf(TEST_TARGET_PACKAGE_NAME, TEST_TARGET_PACKAGE_NAME2, TEST_TARGET_PACKAGE_NAME3)
        private val packagesToLabelMap =
            mapOf(
                TEST_AGENT_PACKAGE_NAME to TEST_AGENT_LABEL,
                TEST_AGENT_PACKAGE_NAME2 to TEST_AGENT_LABEL2,
                DEVICE_SETTINGS_TARGET_PACKAGE_NAME to TEST_DEVICE_SETTINGS_LABEL,
                TEST_TARGET_PACKAGE_NAME to TEST_TARGET_LABEL,
                TEST_TARGET_PACKAGE_NAME2 to TEST_TARGET_LABEL2,
                TEST_TARGET_PACKAGE_NAME3 to TEST_TARGET_LABEL3,
            )
        private val accessFlags =
            mapOf(
                (TEST_AGENT_PACKAGE_NAME to DEVICE_SETTINGS_TARGET_PACKAGE_NAME) to
                    ACCESS_FLAG_USER_GRANTED,
                (TEST_AGENT_PACKAGE_NAME to TEST_TARGET_PACKAGE_NAME) to ACCESS_FLAG_USER_GRANTED,
                (TEST_AGENT_PACKAGE_NAME to TEST_TARGET_PACKAGE_NAME2) to ACCESS_FLAG_USER_DENIED,
                (TEST_AGENT_PACKAGE_NAME to TEST_TARGET_PACKAGE_NAME3) to 0, // invalid
                (TEST_AGENT_PACKAGE_NAME2 to DEVICE_SETTINGS_TARGET_PACKAGE_NAME) to
                    ACCESS_FLAG_USER_GRANTED,
                (TEST_AGENT_PACKAGE_NAME2 to TEST_TARGET_PACKAGE_NAME) to ACCESS_FLAG_USER_DENIED,
                (TEST_AGENT_PACKAGE_NAME2 to TEST_TARGET_PACKAGE_NAME2) to ACCESS_FLAG_USER_GRANTED,
                (TEST_AGENT_PACKAGE_NAME2 to TEST_TARGET_PACKAGE_NAME3) to 0, // invalid
            )
    }
}
