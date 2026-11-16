package com.relaybook.model;

import com.relaybook.exception.ValidationException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable "when is this person reachable" window: a start time, an end time,
 * and optional days of the week. Parsed from inputs such as
 * {@code "Weekdays 18:00-21:00"}, {@code "Mon,Wed,Fri 10:00-12:00"},
 * {@code "09:00-17:00"} (every day), or {@code "any time"}.
 *
 * <p>A window whose end is before its start crosses midnight (for example
 * {@code 22:00-02:00}). {@link #isOpenAt(ZonedDateTime)} answers whether an
 * instant falls inside the window in the contact's own time zone.</p>
 */
public final class TimeWindow {
    private static final LocalTime ALL_DAY_START = LocalTime.MIN;
    private static final LocalTime ALL_DAY_END = LocalTime.of(23, 59, 59);
    private static final Set<DayOfWeek> WEEKDAYS = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
    private static final Set<DayOfWeek> WEEKENDS = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private final LocalTime start;
    private final LocalTime end;
    private final Set<DayOfWeek> days; // empty set = every day

    private TimeWindow(LocalTime start, LocalTime end, Set<DayOfWeek> days) {
        this.start = start;
        this.end = end;
        this.days = days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days);
    }

    /** A window that is open every day between start and end. */
    public static TimeWindow daily(LocalTime start, LocalTime end) {
        return new TimeWindow(start, end, EnumSet.noneOf(DayOfWeek.class));
    }

    public static TimeWindow of(LocalTime start, LocalTime end, Set<DayOfWeek> days) {
        return new TimeWindow(start, end, days);
    }

    /**
     * Parses a user-entered window; {@link java.util.Optional#empty()} when the
     * text is not a structured window (best time stays free text, which is valid).
     */
    public static Optional<TimeWindow> tryParse(String input) {
        try {
            return Optional.of(parse(input));
        } catch (ValidationException ignored) {
            return Optional.empty();
        }
    }

    /** Strict parse used where an explicit window is required; throws on bad input. */
    public static TimeWindow parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            throw new ValidationException("Time window is empty. Examples: 'Weekdays 18:00-21:00' or 'any time'.");
        }
        String normalised = input.trim().toLowerCase(Locale.ROOT);
        if (normalised.equals("any time") || normalised.equals("anytime")
                || normalised.equals("always") || normalised.equals("24x7") || normalised.equals("24/7")) {
            return new TimeWindow(ALL_DAY_START, ALL_DAY_END, EnumSet.noneOf(DayOfWeek.class));
        }

        String dayPart = null;
        String timePart = normalised;
        int space = normalised.indexOf(' ');
        if (space > 0) {
            dayPart = normalised.substring(0, space).trim();
            timePart = normalised.substring(space + 1).trim();
        }
        Set<DayOfWeek> days = dayPart == null ? EnumSet.noneOf(DayOfWeek.class) : parseDays(dayPart);
        String[] times = timePart.split("\\s*(?:-|to|until)\\s*");
        if (times.length != 2) {
            throw new ValidationException("Use a start and end time, for example '18:00-21:00'.");
        }
        LocalTime start = parseTime(times[0]);
        LocalTime end = parseTime(times[1]);
        if (start.equals(end)) {
            throw new ValidationException("Start and end time cannot be identical.");
        }
        return new TimeWindow(start, end, days);
    }

    private static Set<DayOfWeek> parseDays(String dayPart) {
        if (dayPart.equals("weekdays") || dayPart.equals("weekday")) {
            return WEEKDAYS;
        }
        if (dayPart.equals("weekends") || dayPart.equals("weekend")) {
            return WEEKENDS;
        }
        if (dayPart.equals("daily") || dayPart.equals("every day") || dayPart.equals("all days")) {
            return EnumSet.noneOf(DayOfWeek.class);
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (String token : dayPart.split(",")) {
            days.add(dayFromName(token.trim()));
        }
        if (days.isEmpty()) {
            throw new ValidationException("Unknown day list: " + dayPart);
        }
        return days;
    }

    private static DayOfWeek dayFromName(String name) {
        String token = name.toLowerCase(Locale.ROOT);
        for (DayOfWeek day : DayOfWeek.values()) {
            String fullName = day.name().toLowerCase(Locale.ROOT);   // "monday"
            String shortName = fullName.substring(0, 3);             // "mon"
            if (token.equals(fullName) || token.equals(shortName) || token.equals(shortName + ".")) {
                return day;
            }
        }
        throw new ValidationException("Unknown day: " + name);
    }

    private static LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value.trim(), DateTimeFormatter.ofPattern("H:mm"));
        } catch (Exception ex) {
            throw new ValidationException("Times must use 24-hour HH:MM, for example 18:00.");
        }
    }

    /** True when the instant falls inside this window (in the instant's own zone). */
    public boolean isOpenAt(ZonedDateTime when) {
        if (when == null) {
            return false;
        }
        if (!days.isEmpty() && !days.contains(when.getDayOfWeek())) {
            return false;
        }
        LocalTime time = when.toLocalTime();
        if (start.isBefore(end) || start.equals(end)) {
            return !time.isBefore(start) && !time.isAfter(end);
        }
        // Overnight window, e.g. 22:00-02:00.
        return !time.isBefore(start) || time.isBefore(end);
    }

    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
    /** Empty set means every day. */
    public Set<DayOfWeek> getDays() { return EnumSet.copyOf(days); }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TimeWindow)) {
            return false;
        }
        TimeWindow that = (TimeWindow) other;
        return start.equals(that.start) && end.equals(that.end) && days.equals(that.days);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(start, end, days);
    }

    @Override
    public String toString() {
        String daysLabel;
        if (days.isEmpty()) {
            daysLabel = isAllDay() ? "Any time" : "Daily";
        } else if (days.equals(WEEKDAYS)) {
            daysLabel = "Weekdays";
        } else if (days.equals(WEEKENDS)) {
            daysLabel = "Weekends";
        } else {
            StringBuilder label = new StringBuilder();
            for (DayOfWeek day : days) {
                if (label.length() > 0) {
                    label.append(',');
                }
                label.append(day.name().substring(0, 3).charAt(0)).append(day.name().substring(1, 3).toLowerCase(Locale.ROOT));
            }
            daysLabel = label.toString();
        }
        if (isAllDay()) {
            return daysLabel;
        }
        return daysLabel + " " + start.format(DateTimeFormatter.ofPattern("HH:mm"))
                + "-" + end.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private boolean isAllDay() {
        return start.equals(ALL_DAY_START) && end.equals(ALL_DAY_END);
    }
}
