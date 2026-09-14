package com.appthere.drafts

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform