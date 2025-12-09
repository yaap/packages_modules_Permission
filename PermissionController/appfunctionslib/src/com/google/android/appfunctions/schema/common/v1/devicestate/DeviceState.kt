/*
 * Copyright 2025 The Android Open Source Project
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

package com.google.android.appfunctions.schema.common.v1.devicestate

import android.content.Context
import androidx.appsearch.annotation.Document
import java.util.Objects

private const val DEVICE_STATE_CATEGORY = "device_state"

/** The execution context of app function. */
public interface AppFunctionContext {
    /** The Android context. */
    public val context: Context

    /**
     * Return the name of the package that invoked this AppFunction. You can use this information to
     * validate the caller.
     */
    public val callingPackageName: String
}

/** Annotates an interface that defines the app function schema interface. */
// Binary because it's used to determine the schema name and version from the
// compiled schema library.
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
public annotation class AppFunctionSchemaDefinition(
    val name: String,
    val version: Int,
    val category: String,
)

/** Gets uncategorized device states. */
@AppFunctionSchemaDefinition(
    name = "getUncategorizedDeviceState",
    version = 1,
    category = DEVICE_STATE_CATEGORY,
)
interface GetUncategorizedDeviceState {
    /**
     * Gets uncategorized device states.
     *
     * @param appFunctionContext The AppFunction execution context.
     */
    suspend fun getUncategorizedDeviceState(
        appFunctionContext: AppFunctionContext
    ): DeviceStateResponse
}

/** Gets storage device state. */
@AppFunctionSchemaDefinition(
    name = "getStorageDeviceState",
    version = 1,
    category = DEVICE_STATE_CATEGORY,
)
interface GetStorageDeviceState {
    /**
     * Gets storage device states.
     *
     * @param appFunctionContext The AppFunction execution context.
     */
    suspend fun getStorageDeviceState(appFunctionContext: AppFunctionContext): DeviceStateResponse
}

/** Gets battery device state. */
@AppFunctionSchemaDefinition(
    name = "getBatteryDeviceState",
    version = 1,
    category = DEVICE_STATE_CATEGORY,
)
interface GetBatteryDeviceState {
    /**
     * Gets battery device states.
     *
     * @param appFunctionContext The AppFunction execution context.
     */
    suspend fun getBatteryDeviceState(appFunctionContext: AppFunctionContext): DeviceStateResponse
}

/** Gets mobile data usage device state. */
@AppFunctionSchemaDefinition(
    name = "getMobileDataUsageDeviceState",
    version = 1,
    category = DEVICE_STATE_CATEGORY,
)
interface GetMobileDataUsageDeviceState {
    /**
     * Gets mobile data suage device states.
     *
     * @param appFunctionContext The AppFunction execution context.
     */
    suspend fun getMobileDataUsageDeviceState(
        appFunctionContext: AppFunctionContext
    ): DeviceStateResponse
}

/** Gets permissions device state. */
@AppFunctionSchemaDefinition(
    name = "getPermissionsDeviceState",
    version = 1,
    category = DEVICE_STATE_CATEGORY,
)
interface GetPermissionsDeviceState {
    /**
     * Gets permissions device states.
     *
     * @param appFunctionContext The AppFunction execution context.
     */
    suspend fun getPermissionsDeviceState(
        appFunctionContext: AppFunctionContext,
        getPermissionsDeviceStateParams: GetPermissionsDeviceStateParams,
    ): DeviceStateResponse
}

/** Gets wellbeing device state. */
@AppFunctionSchemaDefinition(
    name = "getWellbeingDeviceState",
    version = 1,
    category = DEVICE_STATE_CATEGORY,
)
interface GetWellbeingDeviceState {
    /**
     * Gets wellbeing device states.
     *
     * @param appFunctionContext The AppFunction execution context.
     */
    suspend fun getWellbeingDeviceState(appFunctionContext: AppFunctionContext): DeviceStateResponse
}

/** Represents the request that is passed in to get a certain device state. */
@Document(
    name =
        "com.google.android.appfunctions.schema.common.v1.devicestate.GetPermissionsDeviceStateParams"
)
class GetPermissionsDeviceStateParams(
    @Document.Namespace val namespace: String = "", // unused
    @Document.Id val id: String = "", // unused
    /**
     * Indicates that the request was initiated by the user while the device was unlocked and so the
     * app function should skip the device lock state check before executing the. This should only
     * be used by the calling agent for scheduled requests and for handling ongoing requests that
     * were started before the device was locked. Defaults to checking for the lock state if not
     * provided.
     */
    @Document.BooleanProperty val requestInitiatedWhileUnlocked: Boolean? = null,
) {
    override fun equals(other: Any?) =
        other is GetPermissionsDeviceStateParams &&
            requestInitiatedWhileUnlocked == other.requestInitiatedWhileUnlocked

    override fun hashCode() = Objects.hash(requestInitiatedWhileUnlocked)
}

/**
 * Represents the overall state of relevant device settings, structured for consumption by an LLM.
 * This serves as the top-level response object when querying device state.
 */
@Document(name = "com.google.android.appfunctions.schema.common.v1.devicestate.DeviceStateResponse")
class DeviceStateResponse(
    @Document.Namespace val namespace: String = "", // unused
    @Document.Id val id: String = "", // unused
    /** List of per-screen device states. */
    @Document.DocumentProperty val perScreenDeviceStates: List<PerScreenDeviceStates> = emptyList(),
    /**
     * The device's locale, represented as a BCP 47 language tag.
     *
     * Examples: "en-US", "fr-CA", "zh-Hans-CN".
     */
    @Document.StringProperty(required = true) val deviceLocale: String,
) {
    override fun equals(other: Any?) =
        other is DeviceStateResponse &&
            perScreenDeviceStates == other.perScreenDeviceStates &&
            deviceLocale == other.deviceLocale

    override fun hashCode() = Objects.hash(perScreenDeviceStates, deviceLocale)
}

/** A list of device states, logically grouped by the Settings screen or area where they appear. */
@Document(
    name = "com.google.android.appfunctions.schema.common.v1.devicestate.PerScreenDeviceStates"
)
class PerScreenDeviceStates(
    @Document.Namespace val namespace: String = "", // unused
    @Document.Id val id: String = "", // unused
    /**
     * Optional natural language description providing context about this group of settings. Useful
     * for the LLM to understand the purpose or scope of this screen/section. Use LLM-interpretable
     * language. Avoid internal jargon. Can include additional hints that would be interpretable by
     * the LLM
     */
    @Document.StringProperty(required = true) val description: String,
    /**
     * The user-visible navigation path within the Settings app to reach this screen, represented as
     * a list of localized strings. This helps users find the setting manually if needed. Example:
     * ["Settings", "Network & internet", "Wi-Fi"] For deeper settings, including parent elements
     * can improve robustness against UI changes. Optional, as `intentUri` is preferred for direct
     * navigation, but valuable as a fallback or for user guidance. Assumes `LocalizedString`
     * handles the actual localized text based on `deviceStateLocale`.
     */
    @Document.DocumentProperty val paths: List<LocalizedString> = emptyList(),
    /** Intent uri for the screen, or the nearest parent screen that makes sense. */
    @Document.StringProperty val intentUri: String? = null,
    /** List of device state items on the screen. */
    @Document.DocumentProperty val deviceStateItems: List<DeviceStateItem> = emptyList(),
) {
    override fun equals(other: Any?) =
        other is PerScreenDeviceStates &&
            description == other.description &&
            paths == other.paths &&
            intentUri == other.intentUri &&
            deviceStateItems == other.deviceStateItems

    override fun hashCode() = Objects.hash(description, paths, intentUri, deviceStateItems)
}

/** Class for a device state item. */
@Document(name = "com.google.android.appfunctions.schema.common.v1.devicestate.DeviceStateItem")
class DeviceStateItem(
    @Document.Namespace val namespace: String = "", // unused
    @Document.Id val id: String = "", // unused
    /** A key identifying this specific setting. MUST be designed to be understood by LLMs */
    @Document.StringProperty(required = true) val key: String,
    /** Name from the UI - optional. */
    @Document.DocumentProperty val name: LocalizedString? = null,
    /**
     * The human-readable name or label for this setting as it appears *exactly* in the Settings UI,
     * localized according to `deviceStateLocale`. Example: "Wi-Fi", "Brightness level", "Show
     * notifications". Optional: Might not always be available or easily scrapable. Primarily useful
     * for display verification or showing back to the user, less critical for LLM logic than the
     * `key`. Assumes `LocalizedString` handles the actual localized text.
     */
    @Document.StringProperty val jsonValue: String? = null,
    /**
     * This JSON string serves as a direct pass-through to LLM It is intended only for consumption
     * by the LLM, and developers are not expected to parse it manually. This is optional - we don't
     * necessarily have the value but we still want to let Gemini know the item exists so it can
     * point the user towards the relevant screen
     */
    @Document.LongProperty val lastUpdatedEpochMillis: Long? = null,
    /**
     * Optional natural language hints or instructions for the LLM on how to interpret the `key` and
     * `jsonValue`. This can clarify units, scales, valid ranges, relationships to other settings,
     * or constraints. Examples:
     * - "Value is a percentage (0-100)."
     * - "Enum values: 'ENABLED', 'DISABLED', 'ASK'."
     * - "Scale from 0 (off) to 10 (max)."
     * - "This setting is only effective if 'master.switch.key' is enabled."
     * - "This setting can be changed by the device care app, package name: com.oem.devicecare" Use
     *   clear, LLM-interpretable language.
     */
    @Document.StringProperty val hintText: String? = null,
) {
    override fun equals(other: Any?) =
        other is DeviceStateItem &&
            key == other.key &&
            name == other.name &&
            jsonValue == other.jsonValue &&
            lastUpdatedEpochMillis == other.lastUpdatedEpochMillis &&
            hintText == other.hintText

    override fun hashCode() = Objects.hash(key, name, jsonValue, lastUpdatedEpochMillis, hintText)
}

/** Class for a localized string. */
@Document(name = "com.google.android.appfunctions.schema.common.v1.devicestate.LocalizedString")
class LocalizedString(
    @Document.Namespace val namespace: String = "", // unused
    @Document.Id val id: String = "", // unused
    /** English version of the string. */
    @Document.StringProperty(required = true) val english: String,
    /** Localized version of the string. */
    @Document.StringProperty val localized: String? = null,
) {
    override fun equals(other: Any?) =
        other is LocalizedString && english == other.english && localized == other.localized

    override fun hashCode() = Objects.hash(english, localized)
}
