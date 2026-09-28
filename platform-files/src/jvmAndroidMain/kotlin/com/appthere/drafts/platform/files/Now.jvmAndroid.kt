package com.appthere.drafts.platform.files

/** The JVM's wall clock, which is also Android's. */
actual fun epochMillis(): Long = System.currentTimeMillis()
