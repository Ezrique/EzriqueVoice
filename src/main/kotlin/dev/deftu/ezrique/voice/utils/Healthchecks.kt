package dev.deftu.ezrique.voice.utils

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

object Healthchecks {

    fun start() {
        embeddedServer(Netty, port = 6139) {
            routing {
                get("/health") {
                    call.respondText("OK")
                }
            }
        }.start(wait = false)
    }

}