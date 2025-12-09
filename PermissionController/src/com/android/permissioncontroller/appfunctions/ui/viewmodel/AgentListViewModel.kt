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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.permissioncontroller.appfunctions.data.repository.AppFunctionRepository
import com.android.permissioncontroller.appfunctions.domain.model.AppFunctionPackageInfo
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAgentListUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAppFunctionPackageInfoUseCase
import com.android.permissioncontroller.common.model.Stateful
import com.android.permissioncontroller.data.repository.v31.PackageChangeListener
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AgentListViewModel(
    application: Application,
    private val getAgentListUseCase: GetAgentListUseCase,
    scope: CoroutineScope? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AndroidViewModel(application) {
    private val coroutineScope = scope ?: viewModelScope

    private val packageChangeListener = PackageChangeListener(::refresh)

    // Backing property to avoid state updates from other classes
    private val _uiStateFlow = MutableStateFlow<Stateful<AgentListUiState>>(Stateful.Loading())
    val uiStateFlow: StateFlow<Stateful<AgentListUiState>> = _uiStateFlow

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
                _uiStateFlow.value = Stateful.Success(AgentListUiState(getAgentListUseCase()))
            } catch (e: Exception) {
                _uiStateFlow.value = Stateful.Failure(throwable = e)
            }
        }
    }
}

data class AgentListUiState(val agents: List<AppFunctionPackageInfo> = emptyList())

/** Factory for [AgentListViewModel]. */
class AgentListViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val appFunctionRepository = AppFunctionRepository.getInstance(application)
        val packageRepository = PackageRepository.getInstance(application)
        val getAppFunctionPackageInfoUseCase = GetAppFunctionPackageInfoUseCase(packageRepository)
        val getAgentListUseCase =
            GetAgentListUseCase(appFunctionRepository, getAppFunctionPackageInfoUseCase)
        return AgentListViewModel(application, getAgentListUseCase) as T
    }
}
