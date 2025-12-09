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
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_GRANTED
import android.app.appfunctions.AppFunctionManager.ACCESS_REQUEST_STATE_UNREQUESTABLE
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.android.permissioncontroller.appfunctions.data.repository.AppFunctionRepository
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAccessRequestStateUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.GetAppFunctionPackageInfoUseCase
import com.android.permissioncontroller.appfunctions.domain.usecase.UpdateAccessUseCase
import com.android.permissioncontroller.common.model.Stateful
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RequestAppFunctionAccessViewModel(
    application: Application,
    user: UserHandle,
    private val agentPackageName: String,
    private val targetPackageName: String,
    getAppFunctionPackageInfoUseCase: GetAppFunctionPackageInfoUseCase,
    private val getAccessRequestStateUseCase: GetAccessRequestStateUseCase,
    private val updateAccessUseCase: UpdateAccessUseCase,
    scope: CoroutineScope? = null,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AndroidViewModel(application) {
    private val coroutineScope = scope ?: viewModelScope

    @VisibleForTesting
    private val _uiStateFlow = MutableStateFlow<Stateful<RequestAccessUiState>>(Stateful.Loading())
    val uiStateFlow: StateFlow<Stateful<RequestAccessUiState>> = _uiStateFlow

    init {
        coroutineScope.launch(dispatcher) {
            try {
                val grantState = getAccessRequestStateUseCase(agentPackageName, targetPackageName)
                val agentPackageInfo = getAppFunctionPackageInfoUseCase(agentPackageName, user)
                val targetPackageInfo = getAppFunctionPackageInfoUseCase(targetPackageName, user)
                val isRequestable =
                    grantState != ACCESS_REQUEST_STATE_GRANTED &&
                        grantState != ACCESS_REQUEST_STATE_UNREQUESTABLE
                _uiStateFlow.value =
                    Stateful.Success(
                        RequestAccessUiState(
                            agentPackageInfo.label,
                            targetPackageInfo.label,
                            agentPackageInfo.icon,
                            targetPackageInfo.icon,
                            isRequestable,
                        )
                    )
            } catch (e: Exception) {
                _uiStateFlow.value = Stateful.Failure(throwable = e)
            }
        }
    }

    fun grantAccess() {
        coroutineScope.launch(dispatcher) {
            updateAccessUseCase(agentPackageName, targetPackageName, true)
        }
    }
}

/** The data class for UI state of RequestAppFunctionAccess dialog. */
data class RequestAccessUiState(
    val agentLabel: String,
    val targetLabel: String,
    val agentIcon: Drawable?,
    val targetIcon: Drawable?,
    val isRequestable: Boolean,
)

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
class RequestAppFunctionAccessViewModelFactory(
    private val application: Application,
    private val user: UserHandle,
    private val agentPackageName: String,
    private val targetPackageName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val packageRepository = PackageRepository.getInstance(application)
        val appFunctionRepository = AppFunctionRepository.getInstance(application)
        val getAppFunctionPackageInfoUseCase = GetAppFunctionPackageInfoUseCase(packageRepository)
        val getAccessRequestStateUseCase = GetAccessRequestStateUseCase(appFunctionRepository)
        val updateAccessUseCase = UpdateAccessUseCase(appFunctionRepository)
        return RequestAppFunctionAccessViewModel(
            application,
            user,
            agentPackageName,
            targetPackageName,
            getAppFunctionPackageInfoUseCase,
            getAccessRequestStateUseCase,
            updateAccessUseCase,
        )
            as T
    }
}
