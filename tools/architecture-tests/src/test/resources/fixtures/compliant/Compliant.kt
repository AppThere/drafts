package fixtures.compliant

import kotlin.math.max

// The mirror image of the violating fixtures: source that every architecture rule should pass.
// Its job is to catch a rule that matches everything, which is as useless as one that matches
// nothing and looks just as green.
fun widest(values: List<Int>): Int = values.fold(0) { acc, value -> max(acc, value) }
