package com.appthere.drafts.platform.files

/** The JVM's wall clock. */
actual fun epochMillis(): Long = System.currentTimeMillis()
