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
package com.android.permissioncontroller.appfunctions.ui.viewmodel

import android.app.Application
import android.graphics.drawable.Drawable
import android.icu.text.Collator
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.permissioncontroller.appfunctions.data.repository.AppFunctionRepository
import com.android.permissioncontroller.appfunctions.data.repository.AppFunctionRepository.Companion.DEVICE_SETTINGS_TARGET_PACKAGE_NAME
import com.android.permissioncontroller.appfunctions.domain.model.AppFunctionPackageInfo
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAccessRequestStateUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAppFunctionPackageInfoUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.GetDeviceSettingsTargetIconUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.UpdateAccessUseCase
import com.android.permissioncontroller.common.model.Stateful
import com.android.permissioncontroller.data.repository.v31.PackageChangeListener
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AgentAccessViewModel(
    private val application: Application,
    private val agentPackageName: String,
    private val appFunctionRepository: AppFunctionRepository,
    private val getDeviceSettingsTargetIconUseCase: GetDeviceSettingsTargetIconUseCase,
    private val getAppFunctionPackageInfoUseCase: GetAppFunctionPackageInfoUseCase,
    private val getAccessRequestStateUseCase: GetAccessRequestStateUseCase,
    private val updateAccessUseCase: UpdateAccessUseCase,
    scope: CoroutineScope? = null,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    collator: Collator =
        Collator.getInstance(application.resources.configuration.getLocales().get(0)),
) : AndroidViewModel(application) {
    private val coroutineScope = scope ?: viewModelScope
    private val targetListComparator: Comparator<TargetItem> =
        compareBy(collator) { it.packageInfo.label }

    private val packageChangeListener = PackageChangeListener(::refresh)

    // Backing property to avoid state updates from other classes
    private val _uiStateFlow = MutableStateFlow<Stateful<AgentAccessUiState>>(Stateful.Loading())
    val uiStateFlow: StateFlow<Stateful<AgentAccessUiState>> = _uiStateFlow

    init {
        packageChangeListener.register()
        refresh()
    }

    override fun onCleared() {
        packageChangeListener.unregister()
    }

    // TODO(b/432096594): refresh on app function manager change listener
    private fun refresh() {
        coroutineScope.launch(dispatcher) {
            try {
                val agentPackageInfo =
                    getAppFunctionPackageInfoUseCase(agentPackageName, Process.myUserHandle())
                val targetPackageNames = appFunctionRepository.getValidTargets()
                val accessRequestStates =
                    getAccessRequestStateUseCase(agentPackageName, targetPackageNames)
                _uiStateFlow.value =
                    Stateful.Success(
                        createAgentAccessUiState(agentPackageInfo, accessRequestStates)
                    )
            } catch (e: Exception) {
                _uiStateFlow.value = Stateful.Failure(throwable = e)
            }
        }
    }

    private fun createAgentAccessUiState(
        agentPackageInfo: AppFunctionPackageInfo,
        accessRequestStates: Map<String, Boolean> = emptyMap(),
    ): AgentAccessUiState {
        val deviceSettings =
            accessRequestStates.get(DEVICE_SETTINGS_TARGET_PACKAGE_NAME)?.let {
                DeviceSettingsItem(getDeviceSettingsTargetIconUseCase(Process.myUserHandle()), it)
            }
        val targets =
            accessRequestStates
                .filter { it.key != DEVICE_SETTINGS_TARGET_PACKAGE_NAME }
                .mapNotNull {
                    val targetPackageInfo =
                        getAppFunctionPackageInfoUseCase(it.key, Process.myUserHandle())
                    return@mapNotNull TargetItem(targetPackageInfo, it.value)
                }
                .sortedWith(targetListComparator)
        return AgentAccessUiState(agentPackageInfo, deviceSettings, targets)
    }

    fun updateDeviceSettingsAccessState(granted: Boolean) {
        coroutineScope.launch(dispatcher) {
            updateAccessUseCase(agentPackageName, DEVICE_SETTINGS_TARGET_PACKAGE_NAME, granted)
            refresh()
        }
    }

    fun updateAccessState(targetPackageName: String, granted: Boolean) {
        coroutineScope.launch(dispatcher) {
            updateAccessUseCase(agentPackageName, targetPackageName, granted)
            refresh()
        }
    }
}

data class AgentAccessUiState(
    val agent: AppFunctionPackageInfo,
    val deviceSettings: DeviceSettingsItem? = null,
    val targets: List<TargetItem> = emptyList(),
)

data class DeviceSettingsItem(val icon: Drawable?, val accessGranted: Boolean)

data class TargetItem(val packageInfo: AppFunctionPackageInfo, val accessGranted: Boolean)

/** Factory for [AgentAccessViewModel]. */
class AgentAccessViewModelFactory(
    private val application: Application,
    private val agentPackageName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val appFunctionRepository = AppFunctionRepository.getInstance(application)
        val packageRepository = PackageRepository.getInstance(application)
        val getDeviceSettingsTargetIconUseCase =
            GetDeviceSettingsTargetIconUseCase(packageRepository)
        val getAppFunctionPackageInfoUseCase = GetAppFunctionPackageInfoUseCase(packageRepository)
        val getAccessRequestStateUseCase = GetAccessRequestStateUseCase(appFunctionRepository)
        val updateAccessUseCase = UpdateAccessUseCase(appFunctionRepository)
        return AgentAccessViewModel(
            application,
            agentPackageName,
            appFunctionRepository,
            getDeviceSettingsTargetIconUseCase,
            getAppFunctionPackageInfoUseCase,
            getAccessRequestStateUseCase,
            updateAccessUseCase,
        )
            as T
    }
}
