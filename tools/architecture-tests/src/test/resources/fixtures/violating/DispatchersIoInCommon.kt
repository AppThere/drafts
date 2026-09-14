package fixtures.violating

import kotlinx.coroutines.Dispatchers

// Violates: commonMain does not reference Dispatchers.IO (it does not exist on native).
val offending = Dispatchers.IO
