package fixtures.violating

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Violates: Modifier is the first optional parameter.
// `enabled` takes that position, so a caller passing one positional optional argument silently
// sets `enabled` rather than the modifier.
@Composable
fun Offending(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) = Unit
