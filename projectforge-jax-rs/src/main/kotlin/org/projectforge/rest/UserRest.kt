package org.projectforge.rest

import org.projectforge.business.group.service.GroupService
import org.projectforge.business.user.UserDao
import org.projectforge.framework.persistence.api.BaseSearchFilter
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.rest.config.Rest
import org.projectforge.rest.core.AbstractDTORest
import org.projectforge.rest.dto.User
import org.projectforge.ui.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("${Rest.URL}/user")
class UserRest()
    : AbstractDTORest<PFUserDO, User, UserDao, BaseSearchFilter>(UserDao::class.java, BaseSearchFilter::class.java, "user.title") {

    override fun transformDO(obj: PFUserDO, editMode : Boolean): User {
        val user = User()
        val copy = PFUserDO.createCopyWithoutSecretFields(obj)
        if(copy != null) {
            user.copyFrom(copy)
        }
        return user
    }

    override fun transformDTO(dto: User): PFUserDO {
        val userDO = PFUserDO()
        dto.copyTo(userDO)
        return userDO
    }

    @Autowired
    private lateinit var userDao: UserDao

    /**
     * LAYOUT List page
     */
    override fun createListLayout(): UILayout {
        val layout = super.createListLayout()
                .add(UITable.UIResultSetTable()
                        .add(lc, "username", "deactivated", "lastname", "firstname", "personalPhoneIdentifiers",
                                "description", "rights", "ldapValues"))
        return LayoutUtils.processListPage(layout)
    }

    /**
     * LAYOUT Edit page
     */
    override fun createEditLayout(dataObject: PFUserDO): UILayout {
        val layout = super.createEditLayout(dataObject)
                .add(UIRow()
                        .add(UICol()
                                .add(lc, "username", "firstname", "lastname", "organization", "email",
                                        /*"authenticationToken",*/
                                        "jiraUsername", "hrPlanning", "deactivated"/*, "password"*/))
                        .add(UICol()
                                .add(lc, /*"lastLogin", "language", */ "dateformat", "dateformat.xls", "timeNotation",
                                        "_timeZoneObject", "personalPhoneIdentifiers", "sshPublicKey")))
                /*.add(UISelect<Int>("readonlyAccessUsers", lc,
                        multi = true,
                        label = "user.assignedGroups",
                        additionalLabel = "access.groups",
                        autoCompletion = AutoCompletion<Int>(url = "group/aco"),
                        labelProperty = "name",
                        valueProperty = "id"))
                .add(UISelect<Int>("readonlyAccessUsers", lc,
                        multi = true,
                        label = "multitenancy.assignedTenants",
                        additionalLabel = "access.groups",
                        autoCompletion = AutoCompletion<Int>(url = "group/aco"),
                        labelProperty = "name",
                        valueProperty = "id"))*/
                .add(lc, "description")

        return LayoutUtils.processEditPage(layout, dataObject)
    }

    override val autoCompleteSearchFields = arrayOf("username", "firstname", "lastname", "email")

    override fun getAutoCompletionObjects(@RequestParam("search") searchString: String?): MutableList<PFUserDO> {
        val result = super.getAutoCompletionObjects(searchString)
        if (searchString.isNullOrBlank())
            result.removeIf { it.deactivated } // Remove deactivated users when returning all. Show deactivated users only if search string is given.
        return result
    }
}