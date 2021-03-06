/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2020 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.framework.time

import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.Validate
import org.projectforge.framework.calendar.Holidays
import org.projectforge.framework.i18n.UserException
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import java.math.BigDecimal
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.*
import kotlin.math.absoluteValue

class PFDayUtils {
    companion object {
        @JvmStatic
        fun getBeginOfYear(date: LocalDate): LocalDate {
            return date.with(TemporalAdjusters.firstDayOfYear())
        }

        @JvmStatic
        fun getEndOfYear(date: LocalDate): LocalDate {
            return date.with(TemporalAdjusters.lastDayOfYear())
        }

        @JvmStatic
        fun getBeginOfMonth(date: LocalDate): LocalDate {
            return date.with(TemporalAdjusters.firstDayOfMonth())
        }

        @JvmStatic
        fun getEndOfMonth(date: LocalDate): LocalDate {
            return date.with(TemporalAdjusters.lastDayOfMonth())
        }

        @JvmStatic
        fun getBeginOfWeek(date: LocalDate): LocalDate {
            val field = WeekFields.of(getFirstDayOfWeek(), 1).dayOfWeek()
            return return date.with(field, 1)
        }

        @JvmStatic
        fun getEndOfWeek(date: LocalDate): LocalDate {
            return getBeginOfWeek(date).plusDays(6)
        }

        @JvmStatic
        fun getFirstDayOfWeek(): DayOfWeek {
            return ThreadLocalUserContext.getFirstDayOfWeek()
        }

        /**
         * 1 - first day of week (locale dependent, e. g. Monday or Sunday).
         * 7 - last day of week.
         */
        @JvmStatic
        fun <T : IPFDate<T>> withDayOfWeek(date: T, dayOfWeek: Int): T {
            if (dayOfWeek in 1..7) {
                return if (dayOfWeek == 1) date.beginOfWeek else date.beginOfWeek.plusDays((dayOfWeek - 1).toLong())
            } else {
                throw IllegalArgumentException("withDayOfWeek accepts only day of weeks from 1 (first day of week) to 7 (last day of week), but $dayOfWeek was given.")
            }
        }

        /**
         * dayNumber 1 - Monday, 2 - Tuesday, ..., 7 - Sunday
         */
        @JvmStatic
        fun getDayOfWeek(dayNumber: Int): DayOfWeek? {
            if (dayNumber in 1..7) {
                return when (dayNumber) {
                    1 -> DayOfWeek.MONDAY
                    2 -> DayOfWeek.TUESDAY
                    3 -> DayOfWeek.WEDNESDAY
                    4 -> DayOfWeek.THURSDAY
                    5 -> DayOfWeek.FRIDAY
                    6 -> DayOfWeek.SATURDAY
                    7 -> DayOfWeek.SUNDAY
                    else -> null
                }
            } else {
                throw IllegalArgumentException("getDayOfWeek accepts only day of weeks from 1 (first day of week) to 7 (last day of week), but $dayNumber was given.")
            }
        }

        /**
         * @return 1 - Monday, 2 - Tuesday, ..., 7 - Sunday
         */
        @JvmStatic
        fun getDayOfWeekValue(dayOfWeek: DayOfWeek?): Int? {
            return when (dayOfWeek) {
                DayOfWeek.MONDAY -> 1
                DayOfWeek.TUESDAY -> 2
                DayOfWeek.WEDNESDAY -> 3
                DayOfWeek.THURSDAY -> 4
                DayOfWeek.FRIDAY -> 5
                DayOfWeek.SATURDAY -> 6
                DayOfWeek.SUNDAY -> 7
                else -> null
            }
        }

        /**
         * monthNumber 1-based: 1 - January, ..., 12 - December
         */
        @JvmStatic
        fun getMonth(monthNumber: Int?): Month? {
            return if (monthNumber != null) {
                Month.of(monthNumber)
            } else {
                null
            }
        }

        /**
         * Convenient function for Java (is equivalent to Kotlin month?.value).
         * @return monthNumber 1-based: 1 - January, ..., 12 - December or null if given month is null.
         */
        @JvmStatic
        fun getMonthValue(month: Month?): Int? {
            return month?.value
        }

        /**
         * Validates if the given param is null or in 1..12. Otherwise an IllegalArgumentException will be thrown.
         */
        @Throws(IllegalArgumentException::class)
        @JvmStatic
        fun validateMonthValue(month: Int?): Int? {
            if (month != null && month !in 1..12)
                throw IllegalArgumentException("Month value out of range 1..12: $month")
            return month
        }

        @JvmStatic
        fun getNumberOfWorkingDays(from: LocalDate, to: LocalDate): BigDecimal {
            return getNumberOfWorkingDays(PFDay.from(from)!!, PFDay.from(to)!!)
        }

        @JvmStatic
        fun <T : IPFDate<T>> getNumberOfWorkingDays(from: T, to: T): BigDecimal {
            Validate.notNull(from)
            Validate.notNull(to)
            val holidays = Holidays.getInstance()
            if (to.isBefore(from)) {
                return BigDecimal.ZERO
            }
            var numberOfWorkingDays = BigDecimal.ZERO
            var numberOfFullWorkingDays = 0
            var dayCounter = 1
            var day = from
            do {
                if (dayCounter++ > 740) { // Endless loop protection, time period greater 2 years.
                    throw UserException(
                            "getNumberOfWorkingDays does not support calculation of working days for a time period greater than two years!")
                }
                if (holidays.isWorkingDay(day)) {
                    val workFraction = holidays.getWorkFraction(day)
                    if (workFraction != null) {
                        numberOfWorkingDays = numberOfWorkingDays.add(workFraction)
                    } else {
                        numberOfFullWorkingDays++
                    }
                }
                day = day.plusDays(1)
            } while (!day.isAfter(to))
            numberOfWorkingDays = numberOfWorkingDays.add(BigDecimal(numberOfFullWorkingDays))
            return numberOfWorkingDays
        }

        @JvmStatic
        fun <T : IPFDate<T>> addWorkingDays(date: T, days: Int): T {
            Validate.isTrue(days <= 10000)
            var currentDate = date
            val plus = days > 0
            val absDays = days.absoluteValue
            for (counter in 0..9999) {
                if (counter == absDays) {
                    break
                }
                for (paranoia in 0..100) {
                    currentDate = if (plus) currentDate.plusDays(1) else currentDate.minusDays(1)
                    if (isWorkingDay(currentDate)) {
                        break
                    }
                }
            }
            return currentDate
        }

        fun <T : IPFDate<T>> isWorkingDay(date: T): Boolean {
            return Holidays.getInstance().isWorkingDay(date)
        }

        /**
         * Parses the given date as UTC and converts it to the user's zoned date time.
         * @throws DateTimeParseException if the text cannot be parsed
         */
        @JvmStatic
        @JvmOverloads
        fun parseUTCDate(str: String?, dateTimeFormatter: DateTimeFormatter, zoneId: ZoneId = PFDateTime.getUsersZoneId(), locale: Locale = PFDateTime.getUsersLocale()): PFDateTime? {
            if (str.isNullOrBlank())
                return null
            val local = LocalDateTime.parse(str, dateTimeFormatter) // Parses UTC as local date.
            val utcZoned = ZonedDateTime.of(local, ZoneId.of("UTC"))
            val userZoned = utcZoned.withZoneSameInstant(zoneId)
            return PFDateTime(userZoned, locale, null)
        }

        /**
         * Parses the given date as UTC and converts it to the user's zoned date time.
         * Tries the following formatters:
         *
         * number (epoch in seconds), "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss.SSS.'Z'"
         * @throws DateTimeException if the text cannot be parsed
         */
        @JvmStatic
        @JvmOverloads
        fun parseUTCDate(str: String?, zoneId: ZoneId = PFDateTime.getUsersZoneId(), locale: Locale = PFDateTime.getUsersLocale()): PFDateTime? {
            if (str.isNullOrBlank())
                return null
            if (StringUtils.isNumeric(str)) {
                return PFDateTime.from(str.toLong())
            }
            if (str.contains("T")) { // yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
                return parseUTCDate(str, PFDateTime.jsDateTimeFormatter)
            }
            val colonPos = str.indexOf(':')
            return when {
                colonPos < 0 -> {
                    throw DateTimeException("Can't parse date string '$str'. Supported formats are 'yyyy-MM-dd HH:mm', 'yyyy-MM-dd HH:mm:ss', 'yyyy-MM-dd'T'HH:mm:ss.SSS'Z'' and numbers as epoch seconds.")
                }
                str.indexOf(':', colonPos + 1) < 0 -> { // yyyy-MM-dd HH:mm
                    parseUTCDate(str, PFDateTime.isoDateTimeFormatterMinutes, zoneId, locale)
                }
                else -> { // yyyy-MM-dd HH:mm:ss
                    parseUTCDate(str, PFDateTime.isoDateTimeFormatterSeconds, zoneId, locale)
                }
            }
        }

        /**
         * Substract 1 millisecond to get the end of last day.
         */
        private fun getEndOfPreviousDay(beginOfDay: ZonedDateTime): ZonedDateTime {
            return beginOfDay.minusNanos(1)
        }
    }
}
