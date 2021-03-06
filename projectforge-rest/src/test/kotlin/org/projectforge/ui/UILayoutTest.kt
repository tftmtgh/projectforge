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

package org.projectforge.ui

import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.projectforge.business.book.BookDO
import org.projectforge.framework.json.JsonValidator
import org.projectforge.rest.AddressRest
import org.projectforge.rest.BookRest
import org.projectforge.rest.dto.Address
import org.projectforge.test.AbstractTestBase
import org.springframework.beans.factory.annotation.Autowired

class UILayoutTest : AbstractTestBase() {
    @Autowired
    lateinit var bookRest: BookRest

    @Autowired
    lateinit var addressRest: AddressRest

    @Test
    fun testAddressEditLayout() {
        logon(TEST_ADMIN_USER) // Needed for getting address books.
        val gson = GsonBuilder().create()
        val address = Address()
        val jsonString = gson.toJson(addressRest.createEditLayout(address, UILayout.UserAccess(true, true, true, true)))
        val jsonValidator = JsonValidator(jsonString)

        var map = jsonValidator.findParentMap("id", "addressStatus")
        assertEquals(true, map!!["required"] as Boolean)

        map = jsonValidator.findParentMap("id", "form")
        assertEquals(true, map!!["required"] as Boolean)
    }

    @Test
    fun testEditBookActionButtons() {
        val gson = GsonBuilder().create()
        val userAccess = UILayout.UserAccess(true, true, true, true)
        val book = BookDO()
        var jsonString = gson.toJson(bookRest.createEditLayout(book, userAccess))
        var jsonValidator = JsonValidator(jsonString)
        assertEquals("cancel", jsonValidator.get("actions[0].id"))
        assertEquals("create", jsonValidator.get("actions[1].id"))
        assertEquals(2, jsonValidator.getList("actions")?.size)

        book.pk = 42
        jsonString = gson.toJson(bookRest.createEditLayout(book, userAccess))
        jsonValidator = JsonValidator(jsonString)
        assertEquals("cancel", jsonValidator.get("actions[0].id"))
        assertEquals("markAsDeleted", jsonValidator.get("actions[1].id"))
        assertEquals("update", jsonValidator.get("actions[2].id"))
        assertEquals(3, jsonValidator.getList("actions")?.size)

        book.isDeleted = true
        jsonString = gson.toJson(bookRest.createEditLayout(book, userAccess))
        jsonValidator = JsonValidator(jsonString)
        assertEquals("cancel", jsonValidator.get("actions[0].id"))
        assertEquals("undelete", jsonValidator.get("actions[1].id"))
        assertEquals(2, jsonValidator.getList("actions")?.size)
    }

    @Test
    fun testEditBookLayout() {
        val gson = GsonBuilder().create()
        val book = BookDO()
        val userAccess = UILayout.UserAccess(true, true, true, true)
        book.id = 42 // So lend-out component will be visible (only in edit mode)
        val jsonString = gson.toJson(bookRest.createEditLayout(book, userAccess))
        val jsonValidator = JsonValidator(jsonString)

        assertEquals("???book.title.edit???", jsonValidator.get("title")) // translations not available in test.
        val title = jsonValidator.getMap("layout[0]")
        assertField(title, "title", 255.0, "STRING", "???book.title???", type = "INPUT", key = "el-1")
        assertEquals(true, title!!["focus"])

        val authors = jsonValidator.getMap("layout[1]")
        assertField(authors, "authors", 1000.0, null, "???book.authors???", type = "TEXTAREA", key = "el-2")
        assertNull(jsonValidator.getBoolean("layout[1].focus"))

        assertEquals("ROW", jsonValidator.get("layout[2].type"))
        assertEquals("el-3", jsonValidator.get("layout[2].key"))

        assertEquals(6.0, jsonValidator.getDouble("layout[2].content[0].length"))
        assertEquals("COL", jsonValidator.get("layout[2].content[0].type"))
        assertEquals("el-4", jsonValidator.get("layout[2].content[0].key"))
    }

    @Test
    fun testBookListLayout() {
        val gson = GsonBuilder().create()
        val jsonString = gson.toJson(bookRest.createListLayout())
        val jsonValidator = JsonValidator(jsonString)

        assertEquals("resultSet", jsonValidator.get("layout[0].id"))
        assertEquals("TABLE", jsonValidator.get("layout[0].type"))
        assertEquals("el-1", jsonValidator.get("layout[0].key"))

        assertEquals(7, jsonValidator.getList("layout[0].columns")?.size)

        assertEquals("created", jsonValidator.get("layout[0].columns[0].id"))
        assertEquals("???created???", jsonValidator.get("layout[0].columns[0].title"))
        assertEquals("DATE", jsonValidator.get("layout[0].columns[0].dataType"))
        assertEquals(true, jsonValidator.getBoolean("layout[0].columns[0].sortable"))
        assertEquals("TABLE_COLUMN", jsonValidator.get("layout[0].columns[0].type"))
        assertEquals("el-2", jsonValidator.get("layout[0].columns[0].key"))

        assertEquals("yearOfPublishing", jsonValidator.get("layout[0].columns[1].id"))
        assertEquals("???book.yearOfPublishing???", jsonValidator.get("layout[0].columns[1].title"))
        assertEquals("STRING", jsonValidator.get("layout[0].columns[1].dataType"))
        assertEquals(true, jsonValidator.getBoolean("layout[0].columns[1].sortable"))
        assertNull(jsonValidator.get("layout[0].columns[1].formatter"))
        assertEquals("TABLE_COLUMN", jsonValidator.get("layout[0].columns[1].type"))
        assertEquals("el-3", jsonValidator.get("layout[0].columns[1].key"))

        assertEquals(1, jsonValidator.getList("namedContainers")?.size)
        assertEquals("filterOptions", jsonValidator.get("namedContainers[0].id"))
        assertEquals("NAMED_CONTAINER", jsonValidator.get("namedContainers[0].type"))
        assertEquals("nc-1", jsonValidator.get("namedContainers[0].key"))

        assertEquals(1, jsonValidator.getList("namedContainers[0].content")?.size)

        assertEquals(1, jsonValidator.getList("namedContainers[0].content[0].content")?.size)

        assertEquals("deleted", jsonValidator.get("namedContainers[0].content[0].content[0].id"))
        assertEquals("???onlyDeleted.tooltip???", jsonValidator.get("namedContainers[0].content[0].content[0].tooltip"))

        assertEquals(2, jsonValidator.getList("actions")?.size)
        assertEquals("reset", jsonValidator.get("actions[0].id"))
        assertEquals("???reset???", jsonValidator.get("actions[0].title"))
        assertEquals("SECONDARY", jsonValidator.get("actions[0].color")) // Gson doesn't know JsonProperty of Jacskon (DANGER -> danger.)
        assertEquals("BUTTON", jsonValidator.get("actions[0].type"))
        assertEquals("el-11", jsonValidator.get("actions[0].key"))

        assertEquals("PRIMARY", jsonValidator.get("actions[1].color")) // Gson doesn't know JsonProperty of Jacskon.
    }

    private fun assertField(element: Map<String, *>?, id: String, maxLength: Double, dataType: String?, label: String, type: String, key: String) {
        assertNotNull(element)
        assertEquals(id, element!!.get("id"))
        assertEquals(maxLength, element.get("maxLength"))
        if (dataType != null)
            assertEquals(dataType, element.get("dataType"))
        else
            assertNull(dataType)
        assertEquals(label, element.get("label"))
        assertEquals(type, element.get("type"))
        assertEquals(key, element.get("key"))
    }
}
