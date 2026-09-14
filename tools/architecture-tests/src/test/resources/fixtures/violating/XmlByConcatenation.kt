package fixtures.violating

// Violates: export backends do not build XML by concatenation.
fun paragraph(text: String): String = "<w:p><w:r><w:t>" + text + "</w:t></w:r></w:p>"
