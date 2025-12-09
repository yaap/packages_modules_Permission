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
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.TwoStatePreference
import com.android.permissioncontroller.R
import com.android.permissioncontroller.appfunctions.ui.viewmodel.AgentItem
import com.android.permissioncontroller.appfunctions.ui.viewmodel.TargetAccessUiState
import com.android.permissioncontroller.appfunctions.ui.viewmodel.TargetAccessViewModel
import com.android.permissioncontroller.appfunctions.ui.viewmodel.TargetAccessViewModelFactory
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
class TargetAccessChildFragment<PF>() : Fragment(), Preference.OnPreferenceClickListener where
PF : PreferenceFragmentCompat,
PF : TargetAccessChildFragment.Parent {
    private lateinit var targetPackageName: String

    private lateinit var viewModel: TargetAccessViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        targetPackageName = arguments!!.getString(Intent.EXTRA_PACKAGE_NAME)!!

        val factory = TargetAccessViewModelFactory(requireActivity().application, targetPackageName)
        viewModel = ViewModelProvider(this, factory).get(TargetAccessViewModel::class.java)

        val preferenceFragment = requirePreferenceFragment()
        preferenceFragment.lifecycleScope.launch {
            preferenceFragment.lifecycle.repeatOnLifecycle(State.STARTED) {
                viewModel.uiStateFlow.collect(::onUiStateChanged)
            }
        }
    }

    private fun onUiStateChanged(uiState: Stateful<TargetAccessUiState>) {
        if (uiState is Stateful.Loading) {
            // do nothing
            return
        }

        val preferenceFragment = requirePreferenceFragment()
        val preferenceManager = preferenceFragment.preferenceManager
        var preferenceScreen = preferenceFragment.preferenceScreen
        val context = preferenceManager.context
        val oldPreferences = mutableMapOf<String, Preference>()
        if (preferenceScreen == null) {
            preferenceScreen = preferenceManager.createPreferenceScreen(context)
            preferenceFragment.preferenceScreen = preferenceScreen
        } else {
            clearPreferences(preferenceScreen, oldPreferences)
        }

        if (uiState is Stateful.Failure) {
            Log.e(LOG_TAG, "Failed to load agent list", uiState.throwable)
            val targetLabel = uiState.value?.target?.label ?: targetPackageName
            val targetIcon = uiState.value?.target?.icon
            addHeaderPreference(preferenceScreen, targetLabel, targetIcon, oldPreferences)
            addEmptyStatePreference(preferenceScreen, oldPreferences)
        } else if (uiState is Stateful.Success) {
            val targetLabel = uiState.value.target.label
            val targetIcon = uiState.value.target.icon
            val targets = uiState.value.agents

            addHeaderPreference(preferenceScreen, targetLabel, targetIcon, oldPreferences)
            if (targets.isEmpty()) {
                addEmptyStatePreference(preferenceScreen, oldPreferences)
            } else {
                addTargetPreferences(preferenceScreen, targets, oldPreferences)
            }
        }

        preferenceFragment.onPreferenceScreenChanged()
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
        targetLabel: String,
        targetIcon: Drawable?,
        oldPreferences: Map<String, Preference>,
    ) {
        val preference =
            oldPreferences[PREFERENCE_KEY_INTRO]
                ?: requirePreferenceFragment().createHeaderPreference().apply {
                    key = PREFERENCE_KEY_INTRO
                    setSummary(R.string.app_function_target_access_summary)
                }
        // Old preference might need to be updated
        preference.apply {
            icon = targetIcon
            title = targetLabel
        }
        preferenceGroup.addPreference(preference)
    }

    private fun addEmptyStatePreference(
        preferenceGroup: PreferenceGroup,
        oldPreferences: Map<String, Preference>,
    ) {
        val preference =
            oldPreferences[PREFERENCE_KEY_ZERO_STATE]
                ?: requirePreferenceFragment().createEmptyStatePreference().apply {
                    key = PREFERENCE_KEY_ZERO_STATE
                    setTitle(R.string.app_function_agent_list_empty_title)
                    setSummary(R.string.app_function_agent_list_empty_summary)
                }
        preferenceGroup.addPreference(preference)
    }

    private fun addTargetPreferences(
        preferenceGroup: PreferenceGroup,
        agents: List<AgentItem>,
        oldPreferences: Map<String, Preference>,
    ) {
        val preferenceFragment = requirePreferenceFragment()
        for (agent in agents) {
            val agentPackageInfo = agent.packageInfo
            val agentPackageName = agentPackageInfo.packageName
            val preference =
                oldPreferences[agentPackageName] as TwoStatePreference?
                    ?: preferenceFragment.createPreference().apply {
                        key = agentPackageName
                        onPreferenceClickListener = this@TargetAccessChildFragment
                    }
            // Ensure current ux state reflects the data state
            preference.apply {
                title = agentPackageInfo.label
                icon = agentPackageInfo.icon
                isChecked = agent.accessGranted
            }
            preferenceGroup.addPreference(preference)
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        preference as TwoStatePreference
        viewModel.updateAccessState(preference.key, preference.isChecked)
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

        /** Creates a new preference for a target app. */
        fun createPreference(): TwoStatePreference

        /**
         * Callback when changes have been made to the {@link PreferenceScreen} of the parent {@link
         * PreferenceFragmentCompat}.
         */
        fun onPreferenceScreenChanged()
    }

    companion object {
        private val LOG_TAG = TargetAccessChildFragment::class.java.simpleName
        private val PREFERENCE_KEY_INTRO =
            TargetAccessChildFragment::class.java.name + ".preference.INTRO"
        private val PREFERENCE_KEY_ZERO_STATE =
            TargetAccessChildFragment::class.java.name + ".preference.ZERO_STATE"

        /**
         * Create a new instance of TargetAccessChildFragment
         *
         * @param targetPackageName target package to modify access for
         * @return a new instance of TargetAccessChildFragment
         */
        fun newInstance(targetPackageName: String): TargetAccessChildFragment<*> {
            val arguments =
                Bundle().apply { putString(Intent.EXTRA_PACKAGE_NAME, targetPackageName) }
            return TargetAccessChildFragment<Nothing>().apply { setArguments(arguments) }
        }
    }
}
