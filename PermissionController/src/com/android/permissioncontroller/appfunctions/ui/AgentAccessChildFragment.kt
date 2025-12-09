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
package com.android.permissioncontroller.appfunctions.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import com.android.permissioncontroller.R
import com.android.permissioncontroller.appfunctions.ui.viewmodel.AgentAccessUiState
import com.android.permissioncontroller.appfunctions.ui.viewmodel.AgentAccessViewModel
import com.android.permissioncontroller.appfunctions.ui.viewmodel.AgentAccessViewModelFactory
import com.android.permissioncontroller.appfunctions.ui.viewmodel.DeviceSettingsItem
import com.android.permissioncontroller.appfunctions.ui.viewmodel.TargetItem
import com.android.permissioncontroller.common.model.Stateful
import kotlinx.coroutines.launch

/**
 * Child fragment for modifying agent access of target apps.
 *
 * <p>
 * Must be added as a child fragment and its parent fragment must be a {@link
 * PreferenceFragmentCompat} that implements {@link Parent}.
 *
 * @param <PF> type of the parent fragment
 */
class AgentAccessChildFragment<PF> : Fragment(), Preference.OnPreferenceClickListener where
PF : PreferenceFragmentCompat,
PF : AgentAccessChildFragment.Parent {
    private lateinit var agentPackageName: String

    private lateinit var viewModel: AgentAccessViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        agentPackageName = arguments!!.getString(Intent.EXTRA_PACKAGE_NAME)!!

        val factory = AgentAccessViewModelFactory(requireActivity().application, agentPackageName)
        viewModel = ViewModelProvider(this, factory).get(AgentAccessViewModel::class.java)

        val preferenceFragment = requirePreferenceFragment()
        preferenceFragment.lifecycleScope.launch {
            preferenceFragment.lifecycle.repeatOnLifecycle(State.STARTED) {
                viewModel.uiStateFlow.collect(::onUiStateChanged)
            }
        }
    }

    private fun onUiStateChanged(uiState: Stateful<AgentAccessUiState>) {
        if (uiState is Stateful.Loading) {
            // do nothing
            return
        }

        val preferenceFragment = requirePreferenceFragment()
        val preferenceManager = preferenceFragment.preferenceManager
        var preferenceScreen = preferenceFragment.preferenceScreen
        val context = preferenceManager.context
        val oldPreferences = mutableMapOf<String, Preference>()
        var oldSystemTargetPreferenceCategory: PreferenceCategory? = null
        val oldSystemTargetPreferences = mutableMapOf<String, Preference>()
        var oldAppTargetPreferenceCategory: PreferenceCategory? = null
        val oldAppTargetPreferences = mutableMapOf<String, Preference>()
        if (preferenceScreen == null) {
            preferenceScreen = preferenceManager.createPreferenceScreen(context)
            preferenceFragment.preferenceScreen = preferenceScreen
        } else {
            oldSystemTargetPreferenceCategory =
                preferenceScreen.findPreference(PREFERENCE_KEY_SYSTEM_CATEGORY)
            clearPreferenceCategory(oldSystemTargetPreferenceCategory, oldSystemTargetPreferences)

            oldAppTargetPreferenceCategory =
                preferenceScreen.findPreference(PREFERENCE_KEY_APP_CATEGORY)
            clearPreferenceCategory(oldAppTargetPreferenceCategory, oldAppTargetPreferences)

            clearPreferences(preferenceScreen, oldPreferences)
        }

        if (uiState is Stateful.Failure) {
            Log.e(LOG_TAG, "Failed to load target list", uiState.throwable)
            val agentLabel = uiState.value?.agent?.label ?: agentPackageName
            val agentIcon = uiState.value?.agent?.icon
            addHeaderPreference(preferenceScreen, agentLabel, agentIcon, oldPreferences)
            addEmptyStatePreference(preferenceScreen, agentLabel, oldPreferences)
        } else if (uiState is Stateful.Success) {
            val agentLabel = uiState.value.agent.label
            val agentIcon = uiState.value.agent.icon
            val targets = uiState.value.targets
            addHeaderPreference(preferenceScreen, agentLabel, agentIcon, oldPreferences)
            addSystemTargetPreferenceCategory(
                oldSystemTargetPreferenceCategory,
                preferenceScreen,
                uiState.value.deviceSettings,
                oldSystemTargetPreferences,
                context,
            )
            addTargetPreferenceCategory(
                oldAppTargetPreferenceCategory,
                preferenceScreen,
                agentLabel,
                targets,
                oldAppTargetPreferences,
                context,
            )
        }

        preferenceFragment.onPreferenceScreenChanged()
    }

    private fun clearPreferenceCategory(
        preferenceCategory: PreferenceCategory?,
        oldPreferences: MutableMap<String, Preference>,
    ) {
        if (preferenceCategory == null) {
            return
        }
        clearPreferences(preferenceCategory, oldPreferences)
        preferenceCategory.parent?.removePreference(preferenceCategory)
        preferenceCategory.order = Preference.DEFAULT_ORDER
    }

    private fun clearPreferences(
        preferenceGroup: PreferenceGroup,
        oldPreferences: MutableMap<String, Preference>,
    ) {
        for (i in preferenceGroup.preferenceCount - 1 downTo 0) {
            val preference = preferenceGroup.getPreference(i)
            preferenceGroup.removePreference(preference)
            preference.order = Preference.DEFAULT_ORDER
            oldPreferences[preference.key] = preference
        }
    }

    private fun addHeaderPreference(
        preferenceGroup: PreferenceGroup,
        agentLabel: String,
        agentIcon: Drawable?,
        oldPreferences: Map<String, Preference>,
    ) {
        val preference =
            oldPreferences[PREFERENCE_KEY_INTRO]
                ?: requirePreferenceFragment().createHeaderPreference().apply {
                    key = PREFERENCE_KEY_INTRO
                    setSummary(R.string.app_function_agent_access_summary)
                }
        // Old preference might need to be updated
        preference.apply {
            icon = agentIcon
            title = agentLabel
        }
        preferenceGroup.addPreference(preference)
    }

    private fun addSystemTargetPreferenceCategory(
        oldPreferenceCategory: PreferenceCategory?,
        preferenceScreen: PreferenceScreen,
        deviceSettings: DeviceSettingsItem?,
        oldPreferences: Map<String, Preference>,
        context: Context,
    ) {
        if (deviceSettings == null) {
            return
        }
        val preferenceCategory =
            oldPreferenceCategory
                ?: PreferenceCategory(context).apply {
                    key = PREFERENCE_KEY_SYSTEM_CATEGORY
                    setTitle(R.string.app_function_agent_access_system_targets_category_title)
                }
        preferenceScreen.addPreference(preferenceCategory)
        addDeviceSettingsTargetPreference(preferenceCategory, deviceSettings, oldPreferences)
    }

    private fun addTargetPreferenceCategory(
        oldPreferenceCategory: PreferenceCategory?,
        preferenceScreen: PreferenceScreen,
        agentLabel: String,
        targets: List<TargetItem>,
        oldPreferences: Map<String, Preference>,
        context: Context,
    ) {
        val preferenceCategory =
            oldPreferenceCategory
                ?: PreferenceCategory(context).apply {
                    key = PREFERENCE_KEY_APP_CATEGORY
                    setTitle(R.string.app_function_agent_access_app_targets_category_title)
                }
        preferenceScreen.addPreference(preferenceCategory)

        if (targets.isEmpty()) {
            addEmptyStatePreference(preferenceCategory, agentLabel, oldPreferences)
        } else {
            addTargetPreferences(preferenceCategory, targets, oldPreferences)
        }
    }

    private fun addEmptyStatePreference(
        preferenceGroup: PreferenceGroup,
        agentLabel: String,
        oldPreferences: Map<String, Preference>,
    ) {
        val preference =
            oldPreferences[PREFERENCE_KEY_ZERO_STATE]
                ?: requirePreferenceFragment().createEmptyStatePreference().apply {
                    key = PREFERENCE_KEY_ZERO_STATE
                    title =
                        getString(
                            R.string.app_function_agent_access_app_targets_empty_title,
                            agentLabel,
                        )
                }
        preferenceGroup.addPreference(preference)
    }

    private fun addDeviceSettingsTargetPreference(
        preferenceGroup: PreferenceGroup,
        target: DeviceSettingsItem,
        oldPreferences: Map<String, Preference>,
    ) {
        val preference =
            oldPreferences[PREFERENCE_KEY_DEVICE_SETTINGS] as TwoStatePreference?
                ?: requirePreferenceFragment().createPreference().apply {
                    key = PREFERENCE_KEY_DEVICE_SETTINGS
                    onPreferenceClickListener = this@AgentAccessChildFragment
                }
        // Ensure current ux state reflects the data state
        preference.apply {
            setTitle(R.string.app_function_device_settings_target_title)
            if (target.icon != null) {
                icon = target.icon
            } else {
                setIcon(R.drawable.ic_appfunction_target_device_settings)
            }
            isChecked = target.accessGranted
        }
        preferenceGroup.addPreference(preference)
    }

    private fun addTargetPreferences(
        preferenceGroup: PreferenceGroup,
        targets: List<TargetItem>,
        oldPreferences: Map<String, Preference>,
    ) {
        for (target in targets) {
            val targetPackageInfo = target.packageInfo
            val targetPackageName = targetPackageInfo.packageName
            val preference =
                oldPreferences[targetPackageName] as TwoStatePreference?
                    ?: requirePreferenceFragment().createPreference().apply {
                        key = targetPackageName
                        onPreferenceClickListener = this@AgentAccessChildFragment
                    }
            // Ensure current ux state reflects the data state
            preference.apply {
                title = targetPackageInfo.label
                icon = targetPackageInfo.icon
                isChecked = target.accessGranted
            }
            preferenceGroup.addPreference(preference)
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        preference as TwoStatePreference
        if (preference.key == PREFERENCE_KEY_DEVICE_SETTINGS) {
            viewModel.updateDeviceSettingsAccessState(preference.isChecked)
        } else {
            viewModel.updateAccessState(preference.key, preference.isChecked)
        }
        return true
    }

    private fun requirePreferenceFragment(): PF {
        @Suppress("UNCHECKED_CAST")
        return requireParentFragment() as PF
    }

    /** Interface that the parent fragment must implement. */
    interface Parent {
        /** Creates a new header preference for the screen */
        fun createHeaderPreference(): Preference

        /** Creates a new empty state preference for the screen */
        fun createEmptyStatePreference(): Preference

        /** Creates a new two state preference for a target app. */
        fun createPreference(): TwoStatePreference

        /**
         * Callback when changes have been made to the {@link PreferenceScreen} of the parent {@link
         * PreferenceFragmentCompat}.
         */
        fun onPreferenceScreenChanged()
    }

    companion object {
        private val LOG_TAG = AgentAccessChildFragment::class.java.simpleName
        private val PREFERENCE_KEY_INTRO =
            AgentAccessChildFragment::class.java.name + ".preference.INTRO"
        private val PREFERENCE_KEY_SYSTEM_CATEGORY =
            AgentAccessChildFragment::class.java.name + ".preference.SYSTEM_CATEGORY"
        private val PREFERENCE_KEY_APP_CATEGORY =
            AgentAccessChildFragment::class.java.name + ".preference.APP_CATEGORY"
        private val PREFERENCE_KEY_ZERO_STATE =
            AgentAccessChildFragment::class.java.name + ".preference.APP_ZERO_STATE"
        private val PREFERENCE_KEY_DEVICE_SETTINGS =
            AgentAccessChildFragment::class.java.name + ".preference.DEVICE_SETTINGS"

        /**
         * Create a new instance of AgentAccessChildFragment
         *
         * @param agentPackageName agent package to modify access for
         * @return a new instance of AgentAccessChildFragment
         */
        fun newInstance(agentPackageName: String): AgentAccessChildFragment<*> {
            val arguments =
                Bundle().apply { putString(Intent.EXTRA_PACKAGE_NAME, agentPackageName) }
            return AgentAccessChildFragment<Nothing>().apply { setArguments(arguments) }
        }
    }
}
