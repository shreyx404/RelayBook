package com.relaybook.model;

import com.relaybook.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parser and isOpenAt behaviour of the structured "is now a good time?" window. */
class TimeWindowTest {
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

    private ZonedDateTime at(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(LocalDateTime.of(2026, month, day, hour, minute), KOLKATA);
    }

    @Test
    void parsesWeekdaysWithRange() {
        TimeWindow window = TimeWindow.parse("Weekdays 18:00-21:00");
        assertEquals(LocalTime.of(18, 0), window.getStart());
        assertEquals(LocalTime.of(21, 0), window.getEnd());
        assertTrue(window.isOpenAt(at(2026, 11, 4, 19, 0)));   // Wednesday evening
        assertTrue(window.isOpenAt(at(2026, 11, 4, 18, 0)));   // boundary start included
        assertFalse(window.isOpenAt(at(2026, 11, 4, 21, 1)));  // after end
        assertFalse(window.isOpenAt(at(2026, 11, 7, 19, 0)));  // Saturday
    }

    @Test
    void parsesAnyTimeAsAlwaysOpen() {
        TimeWindow window = TimeWindow.parse("any time");
        assertTrue(window.isOpenAt(at(2026, 11, 8, 3, 30))); // Sunday night
        assertEquals("Any time", window.toString());
    }

    @Test
    void parsesBareRangeAsDaily() {
        TimeWindow window = TimeWindow.parse("09:00-17:00");
        assertTrue(window.isOpenAt(at(2026, 11, 8, 10, 0)));  // Sunday counts: daily
        assertFalse(window.isOpenAt(at(2026, 11, 8, 17, 1)));
        assertEquals("Daily 09:00-17:00", window.toString());
    }

    @Test
    void parsesDayLists() {
        TimeWindow window = TimeWindow.parse("mon,wed,fri 10:00-12:00");
        assertTrue(window.isOpenAt(at(2026, 11, 2, 11, 0)));  // Monday
        assertFalse(window.isOpenAt(at(2026, 11, 3, 11, 0))); // Tuesday
        assertEquals(DayOfWeek.MONDAY, window.getDays().iterator().next());
    }

    @Test
    void overnightWindowWrapsMidnight() {
        TimeWindow window = TimeWindow.parse("22:00-02:00");
        assertTrue(window.isOpenAt(at(2026, 11, 4, 23, 0)));
        assertTrue(window.isOpenAt(at(2026, 11, 5, 1, 30)));  // after midnight
        assertFalse(window.isOpenAt(at(2026, 11, 4, 12, 0)));
    }

    @Test
    void weekendsSupported() {
        TimeWindow window = TimeWindow.parse("Weekends 09:00-12:00");
        assertTrue(window.isOpenAt(at(2026, 11, 7, 10, 0)));  // Saturday
        assertFalse(window.isOpenAt(at(2026, 11, 4, 10, 0))); // Wednesday
    }

    @Test
    void tryParseReturnsEmptyForFreeText() {
        assertFalse(TimeWindow.tryParse("Weekdays after 6 pm").isPresent());
        assertFalse(TimeWindow.tryParse(null).isPresent());
        assertFalse(TimeWindow.tryParse("").isPresent());
    }

    @Test
    void strictParseThrowsValidationExceptionWithGuidance() {
        assertThrows(ValidationException.class, () -> TimeWindow.parse("sometime later"));
        assertThrows(ValidationException.class, () -> TimeWindow.parse("Weekdays 6pm"));
        assertThrows(ValidationException.class, () -> TimeWindow.parse("18:00-18:00"));
        assertThrows(ValidationException.class, () -> TimeWindow.parse("Someday 10:00-12:00"));
    }

    @Test
    void toStringRoundTripsThroughTheParser() {
        TimeWindow window = TimeWindow.parse("Weekdays 18:00-21:00");
        assertEquals(window, TimeWindow.tryParse(window.toString()).orElseThrow());
    }
}
