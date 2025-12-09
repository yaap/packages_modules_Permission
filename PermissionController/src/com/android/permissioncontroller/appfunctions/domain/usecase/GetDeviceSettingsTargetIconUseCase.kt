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

import android.graphics.drawable.Drawable
import android.os.UserHandle
import com.android.permissioncontroller.pm.data.repository.v31.PackageRepository

/**
 * This use case returns a [Drawable] for the Device Settings target for the specified user. Returns
 * null if the settings package or its icon cannot be found.
 *
 * @param packageRepository The repository to use to get the package labels and icons.
 */
class GetDeviceSettingsTargetIconUseCase(private val packageRepository: PackageRepository) {
    operator fun invoke(user: UserHandle): Drawable? {
        val settingsPackageName = packageRepository.getSettingsPackageName(user)
        return settingsPackageName?.let {
            packageRepository.getBadgedPackageIcon(settingsPackageName, user)
        }
    }
}
