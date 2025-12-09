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
package com.android.permissioncontroller.appfunctions.domain.usecase

import android.os.UserHandle
import com.android.permissioncontroller.appfunctions.domain.model.AppFunctionPackageInfo
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository

/**
 * This use case returns a [AppFunctionPackageInfo] for the specified package and user
 *
 * @param packageRepository The repository to use to get the package labels and icons.
 */
class GetAppFunctionPackageInfoUseCase(private val packageRepository: PackageRepository) {
    operator fun invoke(packageName: String, user: UserHandle): AppFunctionPackageInfo {
        val label = packageRepository.getPackageLabel(packageName, user)
        val icon = packageRepository.getBadgedPackageIcon(packageName, user)
        return AppFunctionPackageInfo(packageName, label, icon)
    }
}
