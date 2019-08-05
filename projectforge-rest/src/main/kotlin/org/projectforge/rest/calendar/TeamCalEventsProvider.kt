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

package org.projectforge.rest.calendar

import org.projectforge.business.calendar.CalendarStyleMap
import org.projectforge.business.teamcal.event.TeamEventDao
import org.projectforge.business.teamcal.event.TeamEventFilter
import org.projectforge.business.teamcal.event.TeamRecurrenceEvent
import org.projectforge.business.teamcal.event.model.TeamEventDO
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext
import org.projectforge.framework.time.PFDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Provides the events of a calendar (team calendar).
 */
@Deprecated("Will be replaced by CalendarEventsProvider.")
@Component
class TeamCalEventsProvider() {

    @Autowired
    private lateinit var teamEventDao: TeamEventDao

    fun addEvents(start: PFDateTime,
                  end: PFDateTime,
                  events: MutableList<BigCalendarEvent>,
                  teamCalendarIds: List<Int>?,
                  styleMap: CalendarStyleMap) {
        if (teamCalendarIds.isNullOrEmpty())
            return
        val eventFilter = TeamEventFilter()
        eventFilter.teamCals = teamCalendarIds
        eventFilter.startDate = start.utilDate
        eventFilter.endDate = end.utilDate
        eventFilter.user = ThreadLocalUserContext.getUser()
        val teamEvents = teamEventDao.getEventList(eventFilter, true)
        teamEvents?.forEach {
            val eventDO: TeamEventDO
            val recurrentEvent: Boolean
            if (it is TeamEventDO) {
                eventDO = it
                recurrentEvent = false
            } else {
                eventDO = (it as TeamRecurrenceEvent).master
                recurrentEvent = true
            }
            val recurrentDate = if (recurrentEvent) "?recurrentDate=${it.startDate.time / 1000}" else ""
            //val link = "teamEvent/edit/${eventDO.id}$recurrentDate"
            val allDay = eventDO.isAllDay()
            val style = styleMap.get(eventDO.calendarId)
            val dbId: Int?
            val uid:String?
            if (eventDO.id > 0){
                dbId = eventDO.id
                uid = null
            } else {
                dbId = null
                uid = "${eventDO.calendarId}-${eventDO.uid}"
            }
            val event = BigCalendarEvent(
                    it.subject,
                    it.startDate,
                    it.endDate,
                    allDay,
                    location = it.location,
                    desc = it.note,
                    category = "teamEvent",
                    dbId = dbId,
                    uid = uid,
                    bgColor = style?.bgColor,
                    fgColor = style?.fgColor)
            events.add(event)
        }
    }
}
