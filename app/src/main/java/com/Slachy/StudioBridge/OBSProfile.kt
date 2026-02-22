package com.Slachy.StudioBridge

import java.util.UUID

data class OBSProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 4455,
    val password: String = ""
)
