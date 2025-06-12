/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.permissioncontroller.role.ui.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.android.permissioncontroller.role.ui.RoleApplicationItem
import com.android.permissioncontroller.role.ui.wear.model.ConfirmDialogArgs
import com.android.permissioncontroller.wear.permission.components.ScrollableScreen
import com.android.permissioncontroller.wear.permission.components.material3.DialogButtonContent
import com.android.permissioncontroller.wear.permission.components.material3.WearPermissionConfirmationDialog
import com.android.permissioncontroller.wear.permission.components.material3.WearPermissionIconBuilder
import com.android.permissioncontroller.wear.permission.components.material3.WearPermissionListFooter
import com.android.permissioncontroller.wear.permission.components.material3.WearPermissionToggleControl
import com.android.permissioncontroller.wear.permission.components.material3.WearPermissionToggleControlStyle
import com.android.permissioncontroller.wear.permission.components.material3.WearPermissionToggleControlType
import com.android.permissioncontroller.wear.permission.components.theme.ResourceHelper
import com.android.permissioncontroller.wear.permission.components.theme.WearPermissionMaterialUIVersion

@Composable
fun WearDefaultAppScreen(helper: WearDefaultAppHelper) {
    val roleLiveData = helper.viewModel.liveData.observeAsState(emptyList())
    val showConfirmDialog =
        helper.confirmDialogViewModel.showConfirmDialogLiveData.observeAsState(false)
    var isLoading by remember { mutableStateOf(true) }
    val materialUIVersion = ResourceHelper.materialUIVersionInSettings
    Box {
        WearDefaultAppContent(isLoading, roleLiveData.value, helper)
        ConfirmDialog(
            materialUIVersion = materialUIVersion,
            showDialog = showConfirmDialog.value,
            args = helper.confirmDialogViewModel.confirmDialogArgs,
        )
    }
    if (isLoading && roleLiveData.value.isNotEmpty()) {
        isLoading = false
    }
}

@Composable
private fun WearDefaultAppContent(
    isLoading: Boolean,
    applicationItems: List<RoleApplicationItem>,
    helper: WearDefaultAppHelper,
) {
    ScrollableScreen(title = helper.getTitle(), isLoading = isLoading) {
        helper.getNonePreference(applicationItems)?.let {
            item {
                WearPermissionToggleControl(
                    label = it.title.toString(),
                    iconBuilder = it.icon?.let { WearPermissionIconBuilder.builder(it) },
                    checked = it.checked,
                    onCheckedChanged = it.onDefaultCheckChanged,
                    toggleControl = WearPermissionToggleControlType.Radio,
                    labelMaxLines = Integer.MAX_VALUE,
                )
            }
        }
        for (pref in helper.getPreferences(applicationItems)) {
            item {
                WearPermissionToggleControl(
                    label = pref.title.toString(),
                    iconBuilder = pref.icon?.let { WearPermissionIconBuilder.builder(it) },
                    style =
                        if (pref.isEnabled) {
                            WearPermissionToggleControlStyle.Default
                        } else {
                            WearPermissionToggleControlStyle.DisabledLike
                        },
                    secondaryLabel = pref.summary?.toString(),
                    checked = pref.checked,
                    onCheckedChanged = pref.getOnCheckChanged(),
                    toggleControl = WearPermissionToggleControlType.Radio,
                    labelMaxLines = Integer.MAX_VALUE,
                    secondaryLabelMaxLines = Integer.MAX_VALUE,
                )
            }
        }

        item { WearPermissionListFooter(label = helper.getDescription()) }
    }
}

@Composable
private fun ConfirmDialog(
    materialUIVersion: WearPermissionMaterialUIVersion,
    showDialog: Boolean,
    args: ConfirmDialogArgs?,
) {
    args?.run {
        WearPermissionConfirmationDialog(
            materialUIVersion = materialUIVersion,
            show = showDialog,
            message = message,
            positiveButtonContent = DialogButtonContent(onClick = onOkButtonClick),
            negativeButtonContent = DialogButtonContent(onClick = onCancelButtonClick),
        )
    }
}
