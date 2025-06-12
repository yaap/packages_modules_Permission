/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.permissioncontroller.wear.permission.components.theme

import android.content.Context
import android.os.SystemProperties
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DoNotInline
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color

object ResourceHelper {

    private const val MATERIAL3_ENABLED_SYSPROP = "persist.cw_build.bluechip.enabled"

    /* This controls in app permission controller experience. */
    private val material3Enabled: Boolean
        get() {
            return SystemProperties.getBoolean(MATERIAL3_ENABLED_SYSPROP, false)
        }

    val materialUIVersionInApp: WearPermissionMaterialUIVersion =
        if (material3Enabled) {
            WearPermissionMaterialUIVersion.MATERIAL3
        } else {
            WearPermissionMaterialUIVersion.MATERIAL2_5
        }

    /*
    This is to control the permission controller screens in settings.
    Currently it is set as false. We will either use the flag or a common property from settings
    based on settings implementation when we are ready" */
    private val material3EnabledInSettings: Boolean
        get() {
            return false
        }

    val materialUIVersionInSettings: WearPermissionMaterialUIVersion =
        if (material3EnabledInSettings) {
            WearPermissionMaterialUIVersion.MATERIAL3
        } else {
            WearPermissionMaterialUIVersion.MATERIAL2_5
        }

    @DoNotInline
    fun getColor(context: Context, @ColorRes id: Int): Color? {
        return try {
            val colorInt = context.resources.getColor(id, context.theme)
            Color(colorInt)
        } catch (_: Exception) {
            null
        }
    }

    @DoNotInline
    fun getString(context: Context, @StringRes id: Int): String? {
        return try {
            context.resources.getString(id)
        } catch (_: Exception) {
            null
        }
    }

    @DoNotInline
    fun getDimen(context: Context, @DimenRes id: Int): Float? {
        return try {
            context.resources.getDimension(id) / context.resources.displayMetrics.density
        } catch (_: Exception) {
            null
        }
    }
}
