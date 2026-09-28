package com.appthere.drafts.platform.files

/** Android's wall clock, which is the JVM's. */
actual fun epochMillis(): Long = System.currentTimeMillis()
