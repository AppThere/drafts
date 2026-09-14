package fixtures.violating

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Violates: core-model does not depend on Compose.
@Composable
fun Offending(modifier: Modifier) = Unit
