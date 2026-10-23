package ph.pusoalalay.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ph.pusoalalay.app.domain.*

@RunWith(AndroidJUnit4::class)
class RecordFormTest {
    @get:Rule val compose = createComposeRule()

    @Test fun invalidInputDoesNotSubmitAndCorrectionPreservesNotes() {
        var submitted: HealthRecord? = null
        compose.setContent {
            PusoTheme { RecordForm(null, RecordKind.HEART_RATE, "en", false, {}, { submitted = it }) }
        }
        compose.onNodeWithTag("record_value").performTextInput("0")
        compose.onNodeWithTag("record_note").performScrollTo().performTextInput("After a walk")
        compose.onNodeWithTag("record_save").performScrollTo().performClick()
        compose.runOnIdle { assertNull(submitted) }
        compose.onNodeWithTag("record_value").performScrollTo().performTextReplacement("84")
        compose.onNodeWithTag("record_save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(84.0, submitted!!.value!!, 0.0)
            assertEquals("After a walk", submitted!!.note)
        }
    }
}
