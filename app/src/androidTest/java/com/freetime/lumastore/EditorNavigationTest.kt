package com.freetime.lumastore

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun tabSwitchCanBeCancelledThenExplicitlyDiscarded() {
        val guard = EditorNavigationGuard()
        var destinations = 0
        var discards = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalEditorNavigationGuard provides guard) {
                    rememberEditorBackAction(dirty = true, busy = false, active = true,
                        onDiscard = { discards++ }, onBack = {})
                }
            }
        }
        compose.runOnIdle { guard.request { destinations++ } }
        compose.onNodeWithText(context.getString(R.string.editor_keep_editing)).performClick()
        compose.runOnIdle {
            assertEquals(0, destinations)
            assertEquals(0, discards)
            guard.request { destinations++ }
        }
        compose.onNodeWithText(context.getString(R.string.editor_discard)).performClick()
        compose.runOnIdle {
            assertEquals(1, destinations)
            assertEquals(1, discards)
        }
    }

    @Test fun inFlightSaveBlocksNavigation() {
        val guard = EditorNavigationGuard()
        var destinations = 0
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalEditorNavigationGuard provides guard) {
                    rememberEditorBackAction(dirty = true, busy = true, active = true,
                        onDiscard = {}, onBack = {})
                }
            }
        }
        compose.runOnIdle { guard.request { destinations++ } }
        compose.onNodeWithText(context.getString(R.string.editor_wait_for_save)).assertExists()
        compose.onNodeWithText(context.getString(android.R.string.ok)).performClick()
        compose.runOnIdle { assertEquals(0, destinations) }
    }
}
