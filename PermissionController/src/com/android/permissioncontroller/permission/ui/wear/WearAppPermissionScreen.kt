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

package com.android.permissioncontroller.permission.ui.wear

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material.ToggleChipDefaults
import com.android.permissioncontroller.R
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonState
import com.android.permissioncontroller.permission.ui.model.AppPermissionViewModel.ButtonType
import com.android.permissioncontroller.permission.ui.v33.AdvancedConfirmDialogArgs
import com.android.permissioncontroller.permission.ui.wear.model.AppPermissionConfirmDialogViewModel
import com.android.permissioncontroller.permission.ui.wear.model.ConfirmDialogArgs
import com.android.permissioncontroller.wear.permission.components.ScrollableScreen
import com.android.permissioncontroller.wear.permission.components.material2.ListFooter
import com.android.permissioncontroller.wear.permission.components.material2.ToggleChip
import com.android.permissioncontroller.wear.permission.components.material2.toggleChipDisabledColors
import com.android.permissioncontroller.wear.permission.components.material3.DialogButtonContent
import com.android.permissioncontroller.wear.permission.components.material3.WearPermissionConfirmationDialog
import com.android.permissioncontroller.wear.permission.components.material3.WearPermissionIconBuilder
import com.android.permissioncontroller.wear.permission.components.material3.WearPermissionToggleControlType
import com.android.permissioncontroller.wear.permission.components.material3.defaultAlertConfirmIcon
import com.android.permissioncontroller.wear.permission.components.material3.defaultAlertDismissIcon
import com.android.permissioncontroller.wear.permission.components.theme.ResourceHelper
import com.android.permissioncontroller.wear.permission.components.theme.WearPermissionMaterialUIVersion
import com.android.settingslib.RestrictedLockUtils

@Composable
fun WearAppPermissionScreen(
    title: String,
    viewModel: AppPermissionViewModel,
    confirmDialogViewModel: AppPermissionConfirmDialogViewModel,
    onLocationSwitchChanged: (Boolean) -> Unit,
    onGrantedStateChanged: (ButtonType, Boolean) -> Unit,
    onFooterClicked: (RestrictedLockUtils.EnforcedAdmin) -> Unit,
    onConfirmDialogOkButtonClick: (ConfirmDialogArgs) -> Unit,
    onConfirmDialogCancelButtonClick: () -> Unit,
    onAdvancedConfirmDialogOkButtonClick: (AdvancedConfirmDialogArgs) -> Unit,
    onAdvancedConfirmDialogCancelButtonClick: () -> Unit,
    onDisabledAllowButtonClick: () -> Unit,
) {
    val materialUIVersion = ResourceHelper.materialUIVersionInSettings
    val buttonState = viewModel.buttonStateLiveData.observeAsState(null)
    val detailResIds = viewModel.detailResIdLiveData.observeAsState(null)
    val admin = viewModel.showAdminSupportLiveData.observeAsState(null)
    var isLoading by remember { mutableStateOf(true) }
    val showConfirmDialog = confirmDialogViewModel.showConfirmDialogLiveData.observeAsState(false)
    val showAdvancedConfirmDialog =
        confirmDialogViewModel.showAdvancedConfirmDialogLiveData.observeAsState(false)

    Box {
        WearAppPermissionContent(
            title,
            buttonState.value,
            detailResIds.value,
            admin.value,
            isLoading,
            onLocationSwitchChanged,
            onGrantedStateChanged,
            onFooterClicked,
            onDisabledAllowButtonClick,
        )
        ConfirmDialog(
            materialUIVersion = materialUIVersion,
            showDialog = showConfirmDialog.value,
            args = confirmDialogViewModel.confirmDialogArgs,
            onOkButtonClick = onConfirmDialogOkButtonClick,
            onCancelButtonClick = onConfirmDialogCancelButtonClick,
        )
        AdvancedConfirmDialog(
            materialUIVersion = materialUIVersion,
            showDialog = showAdvancedConfirmDialog.value,
            args = confirmDialogViewModel.advancedConfirmDialogArgs,
            onOkButtonClick = onAdvancedConfirmDialogOkButtonClick,
            onCancelButtonClick = onAdvancedConfirmDialogCancelButtonClick,
        )
    }
    if (isLoading && !buttonState.value.isNullOrEmpty()) {
        isLoading = false
    }
}

@Composable
internal fun WearAppPermissionContent(
    title: String,
    buttonState: Map<ButtonType, ButtonState>?,
    detailResIds: Pair<Int, Int?>?,
    admin: RestrictedLockUtils.EnforcedAdmin?,
    isLoading: Boolean,
    onLocationSwitchChanged: (Boolean) -> Unit,
    onGrantedStateChanged: (ButtonType, Boolean) -> Unit,
    onFooterClicked: (RestrictedLockUtils.EnforcedAdmin) -> Unit,
    onDisabledAllowButtonClick: () -> Unit,
) {
    ScrollableScreen(title = title, isLoading = isLoading) {
        buttonState?.get(ButtonType.LOCATION_ACCURACY)?.let {
            if (it.isShown) {
                item {
                    ToggleChip(
                        checked = it.isChecked,
                        enabled = it.isEnabled,
                        label = stringResource(R.string.app_permission_location_accuracy),
                        toggleControl = WearPermissionToggleControlType.Switch,
                        onCheckedChanged = onLocationSwitchChanged,
                        labelMaxLine = Integer.MAX_VALUE,
                    )
                }
            }
        }
        for (buttonType in buttonTypeOrder) {
            buttonState?.get(buttonType)?.let {
                if (it.isShown) {
                    item {
                        ToggleChip(
                            checked = it.isChecked,
                            colors =
                                if (it.isEnabled) {
                                    ToggleChipDefaults.toggleChipColors()
                                } else {
                                    toggleChipDisabledColors()
                                },
                            label = labelsByButton(buttonType),
                            toggleControl = WearPermissionToggleControlType.Radio,
                            onCheckedChanged = { checked ->
                                if (it.isEnabled) {
                                    onGrantedStateChanged(buttonType, checked)
                                } else {
                                    onDisabledAllowButtonClick()
                                }
                            },
                            labelMaxLine = Integer.MAX_VALUE,
                        )
                    }
                }
            }
        }
        detailResIds?.let {
            item {
                ListFooter(
                    description = stringResource(detailResIds.first),
                    iconRes = R.drawable.ic_info,
                    onClick =
                        if (admin != null) {
                            { onFooterClicked(admin) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

internal val buttonTypeOrder =
    listOf(
        ButtonType.ALLOW,
        ButtonType.ALLOW_ALWAYS,
        ButtonType.ALLOW_FOREGROUND,
        ButtonType.ASK_ONCE,
        ButtonType.ASK,
        ButtonType.DENY,
        ButtonType.DENY_FOREGROUND,
    )

@Composable
internal fun labelsByButton(buttonType: ButtonType) =
    when (buttonType) {
        ButtonType.ALLOW -> stringResource(R.string.app_permission_button_allow)
        ButtonType.ALLOW_ALWAYS -> stringResource(R.string.app_permission_button_allow_always)
        ButtonType.ALLOW_FOREGROUND ->
            stringResource(R.string.app_permission_button_allow_foreground)
        ButtonType.ASK_ONCE -> stringResource(R.string.app_permission_button_ask)
        ButtonType.ASK -> stringResource(R.string.app_permission_button_ask)
        ButtonType.DENY -> stringResource(R.string.app_permission_button_deny)
        ButtonType.DENY_FOREGROUND -> stringResource(R.string.app_permission_button_deny)
        else -> ""
    }

@Composable
internal fun ConfirmDialog(
    materialUIVersion: WearPermissionMaterialUIVersion,
    showDialog: Boolean,
    args: ConfirmDialogArgs?,
    onOkButtonClick: (ConfirmDialogArgs) -> Unit,
    onCancelButtonClick: () -> Unit,
) {
    args?.run {
        WearPermissionConfirmationDialog(
            materialUIVersion = materialUIVersion,
            show = showDialog,
            message = stringResource(messageId),
            positiveButtonContent = DialogButtonContent(onClick = { onOkButtonClick(this) }),
            negativeButtonContent = DialogButtonContent(onClick = { onCancelButtonClick() }),
        )
    }
}

@Composable
internal fun AdvancedConfirmDialog(
    materialUIVersion: WearPermissionMaterialUIVersion,
    showDialog: Boolean,
    args: AdvancedConfirmDialogArgs?,
    onOkButtonClick: (AdvancedConfirmDialogArgs) -> Unit,
    onCancelButtonClick: () -> Unit,
) {
    args?.run {
        val title =
            if (titleId != 0) {
                stringResource(titleId)
            } else {
                ""
            }
        val okButtonIconBuilder =
            WearPermissionIconBuilder.defaultAlertConfirmIcon()
                .contentDescription(stringResource(positiveButtonTextId))
        val cancelButtonIconBuilder =
            WearPermissionIconBuilder.defaultAlertDismissIcon()
                .contentDescription(stringResource(negativeButtonTextId))
        WearPermissionConfirmationDialog(
            materialUIVersion = materialUIVersion,
            show = showDialog,
            title = title,
            iconRes = WearPermissionIconBuilder.builder(iconId),
            message = stringResource(messageId),
            positiveButtonContent =
                DialogButtonContent(
                    icon = okButtonIconBuilder,
                    onClick = { onOkButtonClick(this) },
                ),
            negativeButtonContent =
                DialogButtonContent(
                    icon = cancelButtonIconBuilder,
                    onClick = { onCancelButtonClick() },
                ),
        )
    }
}
