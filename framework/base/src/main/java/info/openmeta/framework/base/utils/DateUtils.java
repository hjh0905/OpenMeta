package info.openmeta.framework.base.utils;

import info.openmeta.framework.base.constant.TimeConstant;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.TimeZone;

/**
 * Date and time utility class
 */
public abstract class DateUtils {

    /**
     * Get current UTC instant.
     */
    public static Instant getInstantNow(){
        return Instant.now();
    }

    /**
     * Get current zoned datetime with the specified zone.
     * Used in datetime computation during multi-zone operations.
     * @return current zoned datetime
     */
    public static ZonedDateTime getZonedDateTimeNow(String zoneId){
        return ZonedDateTime.now(ZoneId.of(zoneId));
    }

    /**
     * Get current zoned datetime in string format with the specified zone.
     * @return datetime string
     */
    public static String getZonedDateTimeNowString(String zoneId){
        return getZonedDateTimeNow(zoneId).format(TimeConstant.DATETIME_FORMATTER);
    }

    /**
     * Get current date in string format with the specified zone.
     * @return datetime string
     */
    public static String  getCurrentLocalDateString(String zoneId){
        return getZonedDateTimeNow(zoneId).format(TimeConstant.DATE_FORMATTER);
    }

    /**
     * Get current date in string format with the system default zone.
     * @return datetime string
     */
    public static String getCurrentSimpleDateString(){
        return ZonedDateTime.now(TimeZone.getDefault().toZoneId()).format(TimeConstant.SIMPLE_DATE_FORMATTER);
    }

    /**
     * Get current time in string format with the specified zone.
     * @return datetime string
     */
    public static String getCurrentLocalTimeString(String zoneId){
        return getZonedDateTimeNow(zoneId).format(TimeConstant.DATETIME_FORMATTER);
    }

    /**
     * Get current year with the specified zone.
     */
    public static Integer getCurrentYear(String zoneId) {
        return getZonedDateTimeNow(zoneId).getYear();
    }

    /**
     * Get current month with the specified zone.
     */
    public static Integer getCurrentMonth(String zoneId) {
        return getZonedDateTimeNow(zoneId).getMonthValue();
    }

    /**
     * Get current local day with the specified zone.
     */
    public static Integer getCurrentDay(String zoneId) {
        return getZonedDateTimeNow(zoneId).getDayOfMonth();
    }


    /** Instant to LocalDate */
    public static LocalDate instantToLocalDate(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Instant to LocalDateTime */
    public static LocalDateTime instantToLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * String date to specified type date object
     * @param value date string
     * @param dateType date object type
     * @return date object
     */
    public static Object stringToDateObject(String value, Class<?> dateType) {
        if (dateType.equals(LocalDate.class)) {
            return LocalDate.parse(value, TimeConstant.DATE_FORMATTER);
        } else if (dateType.equals(LocalDateTime.class)) {
            return LocalDateTime.parse(value, TimeConstant.DATETIME_FORMATTER);
        } else if (dateType.equals(Date.class)) {
            return Date.from(LocalDateTime.parse(value, TimeConstant.DATETIME_FORMATTER).atZone(ZoneId.systemDefault()).toInstant());
        }
        return value;
    }

    /**
     * String date to LocalDate
     * @param value date string
     * @return LocalDate
     */
    public static LocalDate stringToLocalDate(String value) {
        return LocalDate.parse(value, TimeConstant.DATE_FORMATTER);
    }

    /**
     * String date to LocalDateTime
     * @param value date string
     * @return LocalDateTime
     */
    public static LocalDateTime stringToLocalDateTime(String value) {
        return LocalDateTime.parse(value, TimeConstant.DATETIME_FORMATTER);
    }

    /**
     * Date object to string, compatible with LocalDate, LocalDateTime, Date, null
     * @param date date object
     * @param formatter date time formatter
     * @return date string
     */
    public static String dateToString(Object date, DateTimeFormatter formatter) {
        if (date == null) {
            return null;
        } else if (date instanceof LocalDate) {
            return formatter.format((LocalDate) date);
        } else if (date instanceof Date) {
            return formatter.format(dateToLocalDate(date));
        } else {
            return date.toString();
        }
    }

    /**
     * Date object to string, compatible with LocalDate, LocalDateTime, Date, null
     * @param date date object
     * @param dateFormat date time format
     * @return date string
     */
    public static String dateToString(Object date, String dateFormat) {
        return dateToString(date, DateTimeFormatter.ofPattern(dateFormat));
    }

    /**
     * Date object to string, compatible with LocalDate, LocalDateTime, Date, null
     * @param date date object
     * @return date string
     */
    public static String dateToString(Object date) {
        return dateToString(date, TimeConstant.DATE_FORMATTER);
    }

    /**
     * DateTime object to string, compatible with LocalDateTime, Date, null
     * @param dateTime date time object
     * @return date time string
     */
    public static String dateTimeToString(Object dateTime) {
        if (dateTime == null) {
            return null;
        } else if (dateTime instanceof LocalDateTime) {
            return TimeConstant.DATETIME_FORMATTER.format((LocalDateTime) dateTime);
        } else if (dateTime instanceof Date) {
            return TimeConstant.DATETIME_FORMATTER.format(dateToLocalDateTime(dateTime));
        } else {
            return dateTime.toString();
        }
    }

    /**
     * Date string, date object to LocalDate object, compatible with java.sql.Date
     * @param date date string or Date
     * @return LocalDate
     */
    public static LocalDate dateToLocalDate(Object date) {
        if (date == null) {
            return null;
        } else if (date instanceof java.sql.Date) {
            return ((java.sql.Date) date).toLocalDate();
        } else if (date instanceof Date) {
            return ((Date) date).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        } else if (date instanceof String) {
            return LocalDate.parse((String) date, TimeConstant.DATE_FORMATTER);
        } else {
            return (LocalDate) date;
        }
    }

    /**
     * Date string, date object to LocalDateTime object, compatible with java.sql.Date.
     * Keep nano the parameter is date object
     * @param dateTime dateTime string or Date
     * @return LocalDate
     */
    public static LocalDateTime dateToLocalDateTime(Object dateTime) {
        return dateToLocalDateTime(dateTime, true);
    }

    /**
     * Date string, date object to LocalDateTime object, compatible with java.sql.Date.
     * It is necessary to consider whether to keep the millisecond/nanosecond,
     * when converting Date object to LocalDateTime and comparing whether the values are equal.
     * Treat the string as ISO-8601 format, when the string to LocalDateTime exception occurs,
     * such as '2012-03-12T16:20:55.123'
     * @param dateTime date time string or Date
     * @param keepNano whether to keep nano
     * @return LocalDateTime object
     */
    public static LocalDateTime dateToLocalDateTime(Object dateTime, boolean keepNano) {
        if (dateTime == null) {
            return null;
        } else if (dateTime instanceof Date) {
            LocalDateTime localDateTime = ((Date) dateTime).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
            return keepNano ? localDateTime : localDateTime.withNano(0);
        } else if (dateTime instanceof String) {
            try {
                return LocalDateTime.parse((String) dateTime, TimeConstant.DATETIME_FORMATTER);
            } catch (DateTimeParseException e) {
                // Treat the string as ISO-8601 format
                return LocalDateTime.parse((String) dateTime);
            }
        } else {
            return (LocalDateTime) dateTime;
        }
    }

    /**
     * Determine if the date is within the two time intervals
     * start time <= date <= end time
     */
    public static boolean between(LocalDate date,
                                  LocalDate startDate, LocalDate endDate) {
        return !startDate.isAfter(date) && !endDate.isBefore(date);
    }
}
