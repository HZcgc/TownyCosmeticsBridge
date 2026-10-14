package net.townyreborn.journey;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** Awards and UI share a UTC calendar, independent of the host's JVM timezone. */
final class DailyTime {
    static String today() { return today(Clock.systemUTC()); }
    static String today(Clock clock) { return LocalDate.now(clock.withZone(ZoneOffset.UTC)).toString(); }
    private DailyTime() {}
}
