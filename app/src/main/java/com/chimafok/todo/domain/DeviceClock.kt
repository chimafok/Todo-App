package com.chimafok.todo.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Long-lived repositories must follow a timezone change without recreating the ViewModel. */
object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun instant(): Instant = Instant.now()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
}
