/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.framework.persistence.api.impl

import org.projectforge.common.BeanHelper
import org.slf4j.LoggerFactory
import java.util.*
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

/**
 * After querying, every result entry is matched against matchers (for fields not supported by the full text query).
 */
interface DBResultMatcher {
    fun match(obj: Any): Boolean
    fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate

    companion object {
        private val log = LoggerFactory.getLogger(DBResultMatcher::class.java)
    }

    class Equals(
            val field: String,
            val expectedValue: Any)
        : DBResultMatcher {
        override fun match(obj: Any): Boolean {
            val value = BeanHelper.getProperty(obj, field)
            return Objects.equals(expectedValue, value)
        }

        /**
         * Convert this matcher to JPA criteria for where clause in select.
         */
        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.equal(root.get<Any>(field), expectedValue)
        }
    }

    class NotEquals(
            val field: String,
            val notExpectedValue: Any)
        : DBResultMatcher {
        override fun match(obj: Any): Boolean {
            val value = BeanHelper.getProperty(obj, field)
            return !Objects.equals(notExpectedValue, value)
        }

        /**
         * Convert this matcher to JPA criteria for where clause in select.
         */
        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.notEqual(root.get<Any>(field), notExpectedValue)
        }
    }

    class AnyOf<O>(
            val field: String,
            vararg val values: O)
        : DBResultMatcher {
        override fun match(obj: Any): Boolean {
            val value = BeanHelper.getProperty(obj, field) ?: return false
            for (v in values) {
                if (Objects.equals(v, value)) {
                    return true
                }
            }
            return false
        }

        /**
         * Convert this matcher to JPA criteria for where clause in select.
         */
        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            val exp = root.get<Any>(field)
            val predicate = exp.`in`(values)
            return cb.`in`(predicate)
        }
    }

    class Between<O : Comparable<O>>(
            val field: String,
            val from: O,
            val to: O)
        : DBResultMatcher {
        override fun match(obj: Any): Boolean {
            val value = BeanHelper.getProperty(obj, field) ?: return false
            if (from::class.java.isAssignableFrom(value::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return from <= value as O && value <= to
            }
            log.warn("Between operator fails, because value isn't of type ${from::class.java}: $value")
            return false
        }

        /**
         * Convert this matcher to JPA criteria for where clause in select.
         */
        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.between(root.get<O>(field), from, to)
        }
    }

    class Greater<O : Comparable<O>>(
            val field: String,
            val from: O)
        : DBResultMatcher {
        override fun match(obj: Any): Boolean {
            val value = BeanHelper.getProperty(obj, field) ?: return false
            if (value::class.java.isAssignableFrom(value::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return from < value as O
            }
            log.warn("GreaterEqual operator fails, because value isn't of type ${from::class.java}: $value")
            return false
        }

        /**
         * Convert this matcher to JPA criteria for where clause in select.
         */
        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.greaterThan(root.get<O>(field), from)
        }
    }

    class GreaterEqual<O : Comparable<O>>(
            val field: String,
            val from: O)
        : DBResultMatcher {
        override fun match(obj: Any): Boolean {
            val value = BeanHelper.getProperty(obj, field) ?: return false
            if (value::class.java.isAssignableFrom(value::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return from <= value as O
            }
            log.warn("GreaterEqual operator fails, because value isn't of type ${from::class.java}: $value")
            return false
        }

        /**
         * Convert this matcher to JPA criteria for where clause in select.
         */
        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.greaterThanOrEqualTo(root.get<O>(field), from)
        }
    }

    class Less<O : Comparable<O>>(
            val field: String,
            val to: O)
        : DBResultMatcher {
        override fun match(obj: Any): Boolean {
            val value = BeanHelper.getProperty(obj, field) ?: return false
            if (value::class.java.isAssignableFrom(value::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return (value as O) < to
            }
            log.warn("Less operator fails, because value isn't of type ${to::class.java}: $value")
            return false
        }

        /**
         * Convert this matcher to JPA criteria for where clause in select.
         */
        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.lessThan(root.get<O>(field), to)
        }
    }

    class LessEqual<O : Comparable<O>>(
            val field: String,
            val to: O)
        : DBResultMatcher {
        override fun match(obj: Any): Boolean {
            val value = BeanHelper.getProperty(obj, field) ?: return false
            if (value::class.java.isAssignableFrom(value::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return value as O <= to
            }
            log.warn("LessEqual operator fails, because value isn't of type ${to::class.java}: $value")
            return false
        }

        /**
         * Convert this matcher to JPA criteria for where clause in select.
         */
        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.lessThanOrEqualTo(root.get<O>(field), to)
        }
    }

    class Like(
            val field: String,
            val expectedValue: String)
        : DBResultMatcher {
        var plainString: String
        val matchType: MatchType

        init {
            plainString = expectedValue.trim().replace('%', '*')
            if (plainString.startsWith("*")) {
                plainString = plainString.substring(1)
                if (plainString.endsWith('*')) {
                    matchType = MatchType.CONTAINS
                    plainString = plainString.substring(0, plainString.length - 1)
                } else {
                    matchType = MatchType.STARTS_WITH
                }
            } else if (plainString.endsWith('*')) {
                matchType = MatchType.ENDS_WITH
                plainString = plainString.substring(0, plainString.length - 1)
            } else {
                matchType = MatchType.EXACT
            }
        }

        override fun match(obj: Any): Boolean {
            val value = BeanHelper.getProperty(obj, field)?.toString() ?: return false
            return when (matchType) {
                MatchType.CONTAINS -> value.contains(plainString)
                MatchType.EXACT -> value.equals(plainString)
                MatchType.STARTS_WITH -> value.startsWith(plainString)
                MatchType.ENDS_WITH -> value.endsWith(plainString)
            }
        }

        /**
         * Convert this matcher to JPA criteria for where clause in select.
         */
        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.like(cb.lower(root.get<String>(field)), expectedValue)
        }
    }

    class IsNull(
            val field: String)
        : DBResultMatcher {
        override fun match(obj: Any): Boolean {
            return BeanHelper.getProperty(obj, field) == null
        }

        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.isNull(root.get<Any>(field))
        }
    }

    class IsNotNull(
            val field: String)
        : DBResultMatcher {
        override fun match(obj: Any): Boolean {
            return BeanHelper.getProperty(obj, field) != null
        }

        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.isNotNull(root.get<Any>(field))
        }
    }

    class Not(val matcher: DBResultMatcher
    ) : DBResultMatcher {

        override fun match(obj: Any): Boolean {
            return !matcher.match(obj)
        }

        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.not(matcher.asPredicate(cb, root))
        }
    }

    class And(
            vararg matchers: DBResultMatcher
    ) : DBResultMatcher {
        private val matcherList = matchers

        override fun match(obj: Any): Boolean {
            if (matcherList.isNullOrEmpty()) {
                return false
            }
            for (matcher in matcherList) {
                if (!matcher.match(obj)) {
                    return false
                }
            }
            return true
        }

        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.and(*matcherList.map { it.asPredicate(cb, root) }.toTypedArray())
        }
    }

    class Or(
            vararg matcher: DBResultMatcher
    ) : DBResultMatcher {
        val matcherList = matcher

        override fun match(obj: Any): Boolean {
            if (matcherList.isNullOrEmpty()) {
                return false
            }
            for (matcher in matcherList) {
                if (matcher.match(obj)) {
                    return true
                }
            }
            return false
        }

        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            return cb.or(*matcherList.map { it.asPredicate(cb, root) }.toTypedArray())
        }
    }

    class In(val field: String,
             vararg values: Any
    ) : DBResultMatcher {
        val values: List<Any>

        init {
            this.values = listOf(values)
        }

        override fun match(obj: Any): Boolean {
            if (values.isNullOrEmpty()) {
                return false
            }
            for (value in values) {
                if (value == obj) {
                    return true
                }
            }
            return false
        }

        override fun asPredicate(cb: CriteriaBuilder, root: Root<*>): Predicate {
            val inClause = cb.`in`(root.get<Any>(field))
            for (value in values) {
                inClause.value(value)
            }
            return inClause
        }
    }
}
