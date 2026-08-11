package com.hearyet.app.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.hearyet.app.core.ui.theme.HearYetTheme
import com.hearyet.app.core.model.SessionState
import com.hearyet.app.core.model.SyncHealth
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class SharedComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun syncHealthDot_alwaysShowsTheStateLabel() {
        composeTestRule.setContent {
            HearYetTheme {
                SyncHealthDot(health = SyncHealth.DEGRADED, reduceMotion = true)
            }
        }

        composeTestRule.onNodeWithText("Degraded").assertIsDisplayed()
    }

    @Test
    fun primaryCard_invokesClickHandler() {
        var clicked = false
        composeTestRule.setContent {
            HearYetTheme {
                HearYetPrimaryCard(
                    label = "Create",
                    onClick = { clicked = true },
                    reduceMotion = true,
                )
            }
        }

        composeTestRule.onNodeWithText("Create").performClick()

        assertTrue(clicked)
    }

    @Test
    fun stateLadder_displaysCurrentState() {
        composeTestRule.setContent {
            HearYetTheme {
                SessionStateLadder(
                    currentState = SessionState.ClockSyncing,
                )
            }
        }

        composeTestRule.onNodeWithText("Syncing clock…").assertIsDisplayed()
    }

    @Test
    fun confirmationDialog_displaysBothActions() {
        composeTestRule.setContent {
            HearYetTheme {
                ConfirmationDialog(
                    title = "End session?",
                    message = "Everyone will leave this session.",
                    confirmLabel = "End session",
                    onDismiss = {},
                    onConfirm = {},
                    destructive = true,
                )
            }
        }

        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("End session").assertIsDisplayed()
    }
}
