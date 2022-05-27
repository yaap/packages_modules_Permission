/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.permissioncontroller.safetycenter.ui

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import androidx.preference.PreferenceGroup
import com.android.permissioncontroller.R
import com.android.permissioncontroller.safetycenter.SafetyCenterConstants.EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY

/**
 * Helper class to hide issue cards if over a predefined limit and handle revealing hidden issue
 * cards when the more issues preference is clicked
 *
 * @param context Current context
 * @param issueCardPreferences {@link List} of {@link IssueCardPreference} to add to the preference
 * fragment
 * @param expandIssueCards {@code true} if issue cards should be initially expanded, {@code false}
 * otherwise
 * @param expandPreferencesListener Listener used to inform preference fragment of expansion
 */
class CollapsableIssuesCardHelper(
    val context: Context,
    val issueCardPreferences: List<IssueCardPreference>,
    val expandIssueCards: Boolean,
    val isQuickSettingsFragment: Boolean = false,
    val expandPreferencesListener: () -> Unit
) {
    private val numberOfHiddenIssues = issueCardPreferences.size - MAX_SHOWN_ISSUES_COLLAPSED
    private val hasHiddenIssueCards = !expandIssueCards && numberOfHiddenIssues > 0
    private var moreIssuesCardPreference: MoreIssuesCardPreference? =
        getMoreIssuesCardPreferenceOrNull(context)

    /**
     * Add the [IssueCardPreference] managed by this helper to the specified [ ]
     *
     * @param preferenceGroup Preference group to add preference to
     */
    fun addIssues(preferenceGroup: PreferenceGroup) {
        if (issueCardPreferences.isEmpty()) {
            return
        }
        for (i in issueCardPreferences.indices) {
            val issueCardPreference: IssueCardPreference = issueCardPreferences[i]
            if (i == MAX_SHOWN_ISSUES_COLLAPSED &&
                hasHiddenIssueCards &&
                moreIssuesCardPreference != null) {
                preferenceGroup.addPreference(moreIssuesCardPreference)
            }
            issueCardPreference.isVisible = i < MAX_SHOWN_ISSUES_COLLAPSED || expandIssueCards
            preferenceGroup.addPreference(issueCardPreference)
        }
    }

    private fun getMoreIssuesCardPreferenceOrNull(context: Context): MoreIssuesCardPreference? {
        if (!hasHiddenIssueCards) {
            // Not enough issues show more issues card
            return null
        }
        val firstHiddenIssue = issueCardPreferences[MAX_SHOWN_ISSUES_COLLAPSED]
        val prefIconResourceId =
            if (isQuickSettingsFragment) R.drawable.ic_chevron_right else R.drawable.ic_expand_more
        return MoreIssuesCardPreference(
            context, prefIconResourceId, numberOfHiddenIssues, firstHiddenIssue.severityLevel) {
            expand()
            true
        }
    }

    private fun expand() {
        if (isQuickSettingsFragment) {
            // Navigate to Safety center with issues expanded
            val safetyCenterIntent = Intent(ACTION_SAFETY_CENTER)
            safetyCenterIntent.putExtra(EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY, true)
            context.startActivity(safetyCenterIntent)
            return
        }

        for (preference in issueCardPreferences) {
            preference.isVisible = true
        }
        moreIssuesCardPreference?.isVisible = false
        // Notify host so cards can stay expanded on refresh or restart of fragment
        expandPreferencesListener()
    }

    companion object {
        private const val MAX_SHOWN_ISSUES_COLLAPSED = 1
    }
}
