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

package android.safetycenter.cts

import android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterIssue
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterStatus
import android.safetycenter.SafetySourceIssue
import android.safetycenter.cts.testing.NotificationCharacteristics
import android.safetycenter.cts.testing.TestNotificationListener
import android.safetycenter.cts.testing.UiTestHelper.waitSourceIssueDisplayed
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.testing.Coroutines.TIMEOUT_SHORT
import com.android.safetycenter.testing.SafetyCenterActivityLauncher.executeBlockAndExit
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.clearAllSafetySourceDataForTestsWithPermission
import com.android.safetycenter.testing.SafetyCenterApisWithShellPermissions.dismissSafetyCenterIssueWithPermission
import com.android.safetycenter.testing.SafetyCenterFlags
import com.android.safetycenter.testing.SafetyCenterFlags.deviceSupportsSafetyCenter
import com.android.safetycenter.testing.SafetyCenterTestConfigs
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SINGLE_SOURCE_ID
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_1
import com.android.safetycenter.testing.SafetyCenterTestConfigs.Companion.SOURCE_ID_2
import com.android.safetycenter.testing.SafetyCenterTestData
import com.android.safetycenter.testing.SafetyCenterTestHelper
import com.android.safetycenter.testing.SafetySourceIntentHandler.Request
import com.android.safetycenter.testing.SafetySourceIntentHandler.Response
import com.android.safetycenter.testing.SafetySourceReceiver
import com.android.safetycenter.testing.SafetySourceTestData
import com.android.safetycenter.testing.ShellPermissions.callWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Notification-related tests for [SafetyCenterManager]. */
@RunWith(AndroidJUnit4::class)
class SafetyCenterNotificationTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterTestHelper = SafetyCenterTestHelper(context)
    private val safetySourceTestData = SafetySourceTestData(context)
    private val safetyCenterTestConfigs = SafetyCenterTestConfigs(context)
    private val safetyCenterManager =
        requireNotNull(context.getSystemService(SafetyCenterManager::class.java)) {
            "Could not get system service"
        }

    // JUnit's Assume is not supported in @BeforeClass by the CTS tests runner, so this is used to
    // manually skip the setup and teardown methods.
    private val shouldRunTests = context.deviceSupportsSafetyCenter()

    @Before
    fun assumeDeviceSupportsSafetyCenterToRunTests() {
        assumeTrue(shouldRunTests)
    }

    @Before
    fun setUp() {
        if (!shouldRunTests) {
            return
        }
        safetyCenterTestHelper.setup()
        TestNotificationListener.setup()
        SafetyCenterFlags.notificationsEnabled = true
        SafetyCenterFlags.notificationsAllowedSources = setOf(SINGLE_SOURCE_ID)
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.singleSourceConfig)
    }

    @After
    fun tearDown() {
        if (!shouldRunTests) {
            return
        }
        // It is important to reset the notification listener last because it waits/ensures that
        // all notifications have been removed before returning.
        safetyCenterTestHelper.reset()
        TestNotificationListener.reset()
    }

    @Test
    fun setSafetySourceData_withNoIssue_noNotification() {
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.information)

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withoutNotificationsAllowedSource_noNotification() {
        SafetyCenterFlags.notificationsAllowedSources = emptySet()

        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withFlagDisabled_noNotification() {
        SafetyCenterFlags.notificationsEnabled = false

        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_withNotificationBehaviorNever_noNotification() {
        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder()
                        .setNotificationBehavior(SafetySourceIssue.NOTIFICATION_BEHAVIOR_NEVER)
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_withNotificationBehaviorImmediately_sendsNotification() {
        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Notify immediately", "This is urgent!")
                        .setNotificationBehavior(
                            SafetySourceIssue.NOTIFICATION_BEHAVIOR_IMMEDIATELY
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Notify immediately",
                text = "This is urgent!",
                actions = listOf("See issue")
            )
        )
    }

    @Test
    fun setSafetySourceData_withNotificationsAllowedForSourceByFlag_sendsNotification() {
        SafetyCenterFlags.notificationsAllowedSources = setOf(SINGLE_SOURCE_ID)
        val data = safetySourceTestData.recommendationWithAccountIssue

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Recommendation issue title",
                text = "Recommendation issue summary",
                actions = listOf("See issue")
            )
        )
    }

    @Test
    fun setSafetySourceData_issueWithTwoActions_notificationWithTwoActions() {
        val intent1 = safetySourceTestData.testActivityRedirectPendingIntent(identifier = "1")
        val intent2 = safetySourceTestData.testActivityRedirectPendingIntent(identifier = "2")

        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder()
                        .clearActions()
                        .addAction(
                            SafetySourceIssue.Action.Builder("action1", "Action 1", intent1).build()
                        )
                        .addAction(
                            SafetySourceIssue.Action.Builder("action2", "Action 2", intent2).build()
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Recommendation issue title",
                text = "Recommendation issue summary",
                actions = listOf("Action 1", "Action 2")
            )
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_withNotificationsAllowedForSourceByConfig_sendsNotification() {
        safetyCenterTestHelper.setConfig(
            safetyCenterTestConfigs.singleSourceConfig(
                safetyCenterTestConfigs
                    .dynamicSafetySourceBuilder("MyNotifiableSource")
                    .setNotificationsAllowed(true)
                    .build()
            )
        )
        val data = safetySourceTestData.recommendationWithAccountIssue

        safetyCenterTestHelper.setData("MyNotifiableSource", data)

        TestNotificationListener.waitForSingleNotification()
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_withCustomNotification_usesCustomValues() {
        val intent1 = safetySourceTestData.testActivityRedirectPendingIntent(identifier = "1")
        val intent2 = safetySourceTestData.testActivityRedirectPendingIntent(identifier = "2")

        val notification =
            SafetySourceIssue.Notification.Builder("Custom title", "Custom text")
                .addAction(
                    SafetySourceIssue.Action.Builder("action1", "Custom action 1", intent1).build()
                )
                .addAction(
                    SafetySourceIssue.Action.Builder("action2", "Custom action 2", intent2).build()
                )
                .build()

        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Default title", "Default text")
                        .addAction(
                            SafetySourceIssue.Action.Builder(
                                    "default_action",
                                    "Default action",
                                    safetySourceTestData.testActivityRedirectPendingIntent
                                )
                                .build()
                        )
                        .setCustomNotification(notification)
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Custom title",
                text = "Custom text",
                actions = listOf("Custom action 1", "Custom action 2")
            )
        )
    }

    @Test
    @SdkSuppress(minSdkVersion = UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
    fun setSafetySourceData_withEmptyCustomActions_notificationHasNoActions() {
        val notification =
            SafetySourceIssue.Notification.Builder("Custom title", "Custom text")
                .clearActions()
                .build()

        val data =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Default title", "Default text")
                        .addAction(
                            SafetySourceIssue.Action.Builder(
                                    "default_action",
                                    "Default action",
                                    safetySourceTestData.testActivityRedirectPendingIntent
                                )
                                .build()
                        )
                        .setCustomNotification(notification)
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotificationMatching(
            NotificationCharacteristics(
                title = "Custom title",
                text = "Custom text",
                actions = emptyList()
            )
        )
    }

    @Test
    fun setSafetySourceData_twiceWithSameIssueId_updatesNotification() {
        val data1 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Initial", "Blah")
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data1)

        val initialNotification =
            TestNotificationListener.waitForSingleNotificationMatching(
                NotificationCharacteristics(
                    title = "Initial",
                    text = "Blah",
                    actions = listOf("See issue")
                )
            )

        val data2 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Revised", "Different")
                        .addAction(
                            SafetySourceIssue.Action.Builder(
                                    "new_action",
                                    "New action",
                                    safetySourceTestData.testActivityRedirectPendingIntent(
                                        identifier = "new_action"
                                    )
                                )
                                .build()
                        )
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data2)

        val revisedNotification =
            TestNotificationListener.waitForSingleNotificationMatching(
                NotificationCharacteristics(
                    title = "Revised",
                    text = "Different",
                    actions = listOf("See issue", "New action")
                )
            )
        assertThat(initialNotification.statusBarNotification.key)
            .isEqualTo(revisedNotification.statusBarNotification.key)
    }

    @Test
    fun setSafetySourceData_twiceWithExactSameIssue_doNotNotifyTwice() {
        val data = safetySourceTestData.recommendationWithAccountIssue

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotification()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForZeroNotificationEvents()
    }

    @Test
    fun setSafetySourceData_twiceRemovingAnIssue_cancelsNotification() {
        val data1 = safetySourceTestData.recommendationWithAccountIssue
        val data2 = safetySourceTestData.information

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data1)

        TestNotificationListener.waitForSingleNotification()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data2)

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withDismissedIssueId_doesNotNotify() {
        // We use two different issues/data here to ensure that the reason the notification is not
        // posted the second time is specifically because of the dismissal. Notifications are not
        // re-posted/updated for unchanged issues but that functionality is different and tested
        // separately.
        val data1 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Initial", "Blah")
                        .build()
                )
                .build()
        val data2 =
            safetySourceTestData
                .defaultRecommendationDataBuilder()
                .addIssue(
                    safetySourceTestData
                        .defaultRecommendationIssueBuilder("Revised", "Different")
                        .build()
                )
                .build()

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data1)

        val notificationWithChannel =
            TestNotificationListener.waitForSingleNotificationMatching(
                NotificationCharacteristics(
                    title = "Initial",
                    text = "Blah",
                    actions = listOf("See issue")
                )
            )

        TestNotificationListener.cancelAndWait(notificationWithChannel.statusBarNotification.key)

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data2)

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun setSafetySourceData_withInformationIssue_lowImportanceBlockableNotification() {
        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, safetySourceTestData.informationWithIssue)

        TestNotificationListener.waitForNotificationsMatching(
            NotificationCharacteristics(
                "Information issue title",
                "Information issue summary",
                actions = listOf("Review"),
                importance = NotificationManager.IMPORTANCE_LOW,
                blockable = true
            )
        )
    }

    @Test
    fun setSafetySourceData_withRecommendationIssue_defaultImportanceUnblockableNotification() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        TestNotificationListener.waitForNotificationsMatching(
            NotificationCharacteristics(
                "Recommendation issue title",
                "Recommendation issue summary",
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                actions = listOf("See issue"),
                blockable = false
            )
        )
    }

    @Test
    fun setSafetySourceData_withCriticalIssue_highImportanceUnblockableNotification() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingDeviceIssue
        )

        TestNotificationListener.waitForNotificationsMatching(
            NotificationCharacteristics(
                "Critical issue title",
                "Critical issue summary",
                actions = listOf("Solve issue"),
                importance = NotificationManager.IMPORTANCE_HIGH,
                blockable = false
            )
        )
    }

    @Test
    fun dismissSafetyCenterIssue_dismissesNotification() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithAccountIssue
        )

        TestNotificationListener.waitForSingleNotification()

        safetyCenterManager.dismissSafetyCenterIssueWithPermission(
            SafetyCenterTestData.issueId(
                SINGLE_SOURCE_ID,
                SafetySourceTestData.RECOMMENDATION_ISSUE_ID
            )
        )

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun dismissingNotification_doesNotUpdateSafetyCenterData() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        // Add the listener after setting the initial data so that we don't need to consume/receive
        // an update for that
        val listener = safetyCenterTestHelper.addListener()

        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()

        TestNotificationListener.cancelAndWait(notificationWithChannel.statusBarNotification.key)

        assertFailsWith(TimeoutCancellationException::class) {
            listener.receiveSafetyCenterData(TIMEOUT_SHORT)
        }
    }

    @Test
    fun clearSafetySourceData_cancelsAllNotifications() {
        val data = safetySourceTestData.recommendationWithAccountIssue

        safetyCenterTestHelper.setData(SINGLE_SOURCE_ID, data)

        TestNotificationListener.waitForSingleNotification()

        safetyCenterManager.clearAllSafetySourceDataForTestsWithPermission()

        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun sendActionPendingIntent_successful_updatesListenerRemovesNotification() {
        // Here we cause a notification with an action to be posted and prepare the fake receiver
        // to resolve that action successfully.
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val action =
            notificationWithChannel.statusBarNotification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(
            Request.ResolveAction(SINGLE_SOURCE_ID),
            Response.SetData(safetySourceTestData.information)
        )
        val listener = safetyCenterTestHelper.addListener()

        sendActionPendingIntentAndWaitWithPermission(action)

        val listenerData1 = listener.receiveSafetyCenterData()
        assertThat(listenerData1.inFlightActions).hasSize(1)
        val listenerData2 = listener.receiveSafetyCenterData()
        assertThat(listenerData2.issues).isEmpty()
        assertThat(listenerData2.status.severityLevel)
            .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_OK)
        TestNotificationListener.waitForZeroNotifications()
    }

    @Test
    fun sendActionPendingIntent_error_updatesListenerDoesNotRemoveNotification() {
        // Here we cause a notification with an action to be posted and prepare the fake receiver
        // to resolve that action successfully.
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val action =
            notificationWithChannel.statusBarNotification.notification.actions.firstOrNull()
        checkNotNull(action) { "Notification action unexpectedly null" }
        SafetySourceReceiver.setResponse(Request.ResolveAction(SINGLE_SOURCE_ID), Response.Error)
        val listener = safetyCenterTestHelper.addListener()

        sendActionPendingIntentAndWaitWithPermission(action)

        val listenerData1 = listener.receiveSafetyCenterData()
        assertThat(listenerData1.inFlightActions).hasSize(1)
        val listenerData2 = listener.receiveSafetyCenterData()
        assertThat(listenerData2.issues).hasSize(1)
        assertThat(listenerData2.inFlightActions).isEmpty()
        assertThat(listenerData2.status.severityLevel)
            .isEqualTo(SafetyCenterStatus.OVERALL_SEVERITY_LEVEL_CRITICAL_WARNING)
        TestNotificationListener.waitForSingleNotification()
    }

    @Test
    fun sendContentPendingIntent_singleIssue_opensSafetyCenterWithIssueVisible() {
        safetyCenterTestHelper.setData(
            SINGLE_SOURCE_ID,
            safetySourceTestData.recommendationWithDeviceIssue
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val contentIntent = notificationWithChannel.statusBarNotification.notification.contentIntent

        executeBlockAndExit(
            launchActivity = { contentIntent.send() },
            block = { waitSourceIssueDisplayed(safetySourceTestData.recommendationDeviceIssue) }
        )
    }

    @Test
    fun sendContentPendingIntent_anotherHigherSeverityIssue_opensSafetyCenterWithIssueVisible() {
        safetyCenterTestHelper.setConfig(safetyCenterTestConfigs.multipleSourcesConfig)
        SafetyCenterFlags.notificationsAllowedSources = setOf(SOURCE_ID_1)
        safetyCenterTestHelper.setData(
            SOURCE_ID_1,
            safetySourceTestData.recommendationWithDeviceIssue
        )
        safetyCenterTestHelper.setData(
            SOURCE_ID_2,
            safetySourceTestData.criticalWithResolvingGeneralIssue
        )
        val notificationWithChannel = TestNotificationListener.waitForSingleNotification()
        val contentIntent = notificationWithChannel.statusBarNotification.notification.contentIntent

        executeBlockAndExit(
            launchActivity = { contentIntent.send() },
            block = {
                waitSourceIssueDisplayed(safetySourceTestData.criticalResolvingGeneralIssue)
                waitSourceIssueDisplayed(safetySourceTestData.recommendationGeneralIssue)
            }
        )
    }

    companion object {
        private val SafetyCenterData.inFlightActions: List<SafetyCenterIssue.Action>
            get() = issues.flatMap { it.actions }.filter { it.isInFlight }

        private fun sendActionPendingIntentAndWaitWithPermission(action: Notification.Action) {
            callWithShellPermissionIdentity(SEND_SAFETY_CENTER_UPDATE) {
                action.actionIntent.send()
                // Sending the action's PendingIntent above is asynchronous and we need to wait for
                // it to be received by the fake receiver below.
                SafetySourceReceiver.receiveResolveAction()
            }
        }
    }
}