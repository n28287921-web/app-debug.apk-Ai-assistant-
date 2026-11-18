package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AssistantCoreState
import com.example.data.model.DeviceTelemetry
import com.example.data.model.UiState
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HudDashboardScreen
import com.example.ui.theme.JarvisVipTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name matches Jarvis VIP`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Jarvis VIP", appName)
    }

    @Test
    fun `firestore database id resource is provisioned`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbId = context.getString(R.string.firestore_database_id)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun `auth screen renders Google Sign-In button`() {
        composeTestRule.setContent {
            JarvisVipTheme {
                AuthScreen(onAuthSuccess = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag("google_sign_in_button")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `hud dashboard renders mic control and device telemetry`() {
        composeTestRule.setContent {
            JarvisVipTheme {
                HudDashboardScreen(
                    displayName = "Commander",
                    coreState = AssistantCoreState.STANDBY,
                    rmsLevel = 0.2f,
                    partialTranscript = "",
                    wakeWordActive = false,
                    telemetry = DeviceTelemetry(
                        batteryPercent = 88,
                        charging = true,
                        batteryTempC = 31.5f,
                        freeStorageGb = 64.2,
                        totalStorageGb = 128.0,
                        availableRamGb = 3.1,
                        totalRamGb = 6.0,
                        deviceModel = "Galaxy A51"
                    ),
                    weather = null,
                    news = emptyList(),
                    actionLogsState = UiState.Success(emptyList()),
                    backendConfigured = false,
                    onMicClick = {},
                    onStopVoiceOrSpeech = {},
                    onToggleWakeWord = {},
                    onQuickCommand = {},
                    onRefreshTelemetry = {},
                    onOpenSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hud_mic_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_wake_word_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("88%").performScrollTo().assertIsDisplayed()
    }
}
