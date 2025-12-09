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

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle.State
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import com.android.permissioncontroller.R
import com.android.permissioncontroller.appfunctions.domain.model.AppFunctionPackageInfo
import com.android.permissioncontroller.appfunctions.ui.viewmodel.AgentListUiState
import com.android.permissioncontroller.appfunctions.ui.viewmodel.AgentListViewModel
import com.android.permissioncontroller.appfunctions.ui.viewmodel.AgentListViewModelFactory
import com.android.permissioncontroller.common.model.Stateful
import kotlinx.coroutines.launch

/**
 * Child fragment for the list of app function agents.
 *
 * <p>
 * Must be added as a child fragment and its parent fragment must be a {@link
 * PreferenceFragmentCompat} that implements {@link Parent}.
 *
 * @param <PF> type of the parent fragment
 */
class AgentListChildFragment<PF> : Fragment(), OnPreferenceClickListener where
PF : PreferenceFragmentCompat,
PF : AgentListChildFragment.Parent {
    private lateinit var viewModel: AgentListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val factory = AgentListViewModelFactory(requireActivity().application)
        viewModel = ViewModelProvider(this, factory).get(AgentListViewModel::class.java)

        val preferenceFragment = requirePreferenceFragment()
        preferenceFragment.lifecycleScope.launch {
            preferenceFragment.lifecycle.repeatOnLifecycle(State.STARTED) {
                viewModel.uiStateFlow.collect(::onUiStateChanged)
            }
        }
    }

    private fun onUiStateChanged(uiState: Stateful<AgentListUiState>) {
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

        addHeaderPreference(preferenceScreen, oldPreferences)

        when (uiState) {
            is Stateful.Loading -> {} // do nothing
            is Stateful.Failure -> {
                Log.e(LOG_TAG, "Failed to load agent list", uiState.throwable)
                addEmptyStatePreference(preferenceScreen, oldPreferences)
            }
            is Stateful.Success -> {
                val agents = uiState.value.agents
                if (agents.isEmpty()) {
                    addEmptyStatePreference(preferenceScreen, oldPreferences)
                } else {
                    addAgentPreferences(preferenceScreen, agents, oldPreferences)
                }
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
        oldPreferences: Map<String, Preference>,
    ) {
        val preferenceFragment = requirePreferenceFragment()
        val preference =
            oldPreferences[PREFERENCE_KEY_INTRO]
                ?: preferenceFragment.createHeaderPreference().apply {
                    key = PREFERENCE_KEY_INTRO
                    setTitle(R.string.app_function_agent_list_summary)
                }
        preferenceGroup.addPreference(preference)
    }

    private fun addEmptyStatePreference(
        preferenceGroup: PreferenceGroup,
        oldPreferences: Map<String, Preference>,
    ) {
        val preferenceFragment = requirePreferenceFragment()
        val preference =
            oldPreferences[PREFERENCE_KEY_ZERO_STATE]
                ?: preferenceFragment.createEmptyStatePreference().apply {
                    key = PREFERENCE_KEY_ZERO_STATE
                    setTitle(R.string.app_function_agent_list_empty_title)
                    setSummary(R.string.app_function_agent_list_empty_summary)
                }
        preferenceGroup.addPreference(preference)
    }

    private fun addAgentPreferences(
        preferenceGroup: PreferenceGroup,
        agents: List<AppFunctionPackageInfo>,
        oldPreferences: Map<String, Preference>,
    ) {
        val preferenceFragment = requirePreferenceFragment()
        for (agent in agents) {
            val preference =
                oldPreferences[agent.packageName]
                    ?: preferenceFragment.createPreference().apply {
                        key = agent.packageName
                        title = agent.label
                        icon = agent.icon
                        onPreferenceClickListener = this@AgentListChildFragment
                    }
            preferenceGroup.addPreference(preference)
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val agentPackageName = preference.key
        val context = requireContext()
        val intent = AgentAccessActivity.createIntent(context, agentPackageName)
        startActivity(intent)
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

        /** Creates a new preference for an agent. */
        fun createPreference(): Preference

        /**
         * Callback when changes have been made to the {@link PreferenceScreen} of the parent {@link
         * PreferenceFragmentCompat}.
         */
        fun onPreferenceScreenChanged()
    }

    companion object {
        private val LOG_TAG = AgentListChildFragment::class.java.simpleName
        private val PREFERENCE_KEY_INTRO =
            AgentListChildFragment::class.java.name + ".preference.INTRO"
        private val PREFERENCE_KEY_ZERO_STATE =
            AgentListChildFragment::class.java.name + ".preference.ZERO_STATE"

        /**
         * Create a new instance of AgentListChildFragment
         *
         * @return a new instance of AgentListChildFragment
         */
        fun newInstance(): AgentListChildFragment<*> = AgentListChildFragment<Nothing>()
    }
}
