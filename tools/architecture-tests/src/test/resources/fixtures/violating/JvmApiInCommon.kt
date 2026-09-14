package fixtures.violating

import java.io.File
import java.nio.file.Files

// Violates: commonMain does not use JVM APIs, and: only :platform-files writes to disk.
fun read(path: String): String = Files.readString(File(path).toPath())
