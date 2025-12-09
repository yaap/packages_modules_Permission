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
import android.icu.text.Collator
import android.os.Process
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.permissioncontroller.appfunctions.data.repository.AppFunctionRepository
import com.android.permissioncontroller.appfunctions.domain.model.AppFunctionPackageInfo
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAccessRequestStateUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAppFunctionPackageInfoUseCase
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

class TargetAccessViewModel(
    application: Application,
    private val targetPackageName: String,
    private val appFunctionRepository: AppFunctionRepository,
    private val getAppFunctionPackageInfoUseCase: GetAppFunctionPackageInfoUseCase,
    private val getAccessRequestStateUseCase: GetAccessRequestStateUseCase,
    private val updateAccessUseCase: UpdateAccessUseCase,
    scope: CoroutineScope? = null,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    collator: Collator =
        Collator.getInstance(application.resources.configuration.getLocales().get(0)),
) : AndroidViewModel(application) {
    private val coroutineScope = scope ?: viewModelScope
    private val agentListComparator: Comparator<AgentItem> =
        compareBy(collator) { it.packageInfo.label }

    private val packageChangeListener = PackageChangeListener(::refresh)

    // Backing property to avoid state updates from other classes
    private val _uiStateFlow = MutableStateFlow<Stateful<TargetAccessUiState>>(Stateful.Loading())
    val uiStateFlow: StateFlow<Stateful<TargetAccessUiState>> = _uiStateFlow

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
                // FIXME: What if this needs to be DEVICE_SETTINGS_TARGET_PACKAGE_NAME?
                val targetPackageInfo =
                    getAppFunctionPackageInfoUseCase(targetPackageName, Process.myUserHandle())
                val agentPackageNames = appFunctionRepository.getValidAgents()
                val accessRequestStates =
                    getAccessRequestStateUseCase(agentPackageNames, targetPackageName)
                _uiStateFlow.value =
                    Stateful.Success(
                        createTargetAccessUiState(targetPackageInfo, accessRequestStates)
                    )
            } catch (e: Exception) {
                _uiStateFlow.value = Stateful.Failure(throwable = e)
            }
        }
    }

    private fun createTargetAccessUiState(
        targetPackageInfo: AppFunctionPackageInfo,
        accessRequestStates: Map<String, Boolean> = emptyMap(),
    ): TargetAccessUiState {
        val agents =
            accessRequestStates
                .mapNotNull {
                    val agentPackageInfo =
                        getAppFunctionPackageInfoUseCase(it.key, Process.myUserHandle())
                    return@mapNotNull AgentItem(agentPackageInfo, it.value)
                }
                .sortedWith(agentListComparator)
        return TargetAccessUiState(targetPackageInfo, agents)
    }

    fun updateAccessState(agentPackageName: String, granted: Boolean) {
        coroutineScope.launch(dispatcher) {
            updateAccessUseCase(agentPackageName, targetPackageName, granted)
            refresh()
        }
    }
}

data class TargetAccessUiState(
    val target: AppFunctionPackageInfo,
    val agents: List<AgentItem> = emptyList(),
)

data class AgentItem(val packageInfo: AppFunctionPackageInfo, val accessGranted: Boolean)

/** Factory for [TargetAccessViewModel]. */
class TargetAccessViewModelFactory(
    private val application: Application,
    private val targetPackageName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val appFunctionRepository = AppFunctionRepository.getInstance(application)
        val packageRepository = PackageRepository.getInstance(application)
        val getAppFunctionPackageInfoUseCase = GetAppFunctionPackageInfoUseCase(packageRepository)
        val getAccessRequestStateUseCase = GetAccessRequestStateUseCase(appFunctionRepository)
        val updateAccessUseCase = UpdateAccessUseCase(appFunctionRepository)
        return TargetAccessViewModel(
            application,
            targetPackageName,
            appFunctionRepository,
            getAppFunctionPackageInfoUseCase,
            getAccessRequestStateUseCase,
            updateAccessUseCase,
        )
            as T
    }
}
