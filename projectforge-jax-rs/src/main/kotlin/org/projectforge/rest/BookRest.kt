package org.projectforge.rest

import org.projectforge.Const
import org.projectforge.business.book.*
import org.projectforge.framework.persistence.user.api.ThreadLocalUserContext.getUserId
import org.projectforge.ui.ValidationError
import org.projectforge.ui.translate
import org.springframework.stereotype.Controller
import java.util.*
import javax.ws.rs.Consumes
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Controller
@Path("books")
open class BookRest() : AbstractDORest<BookDO, BookDao, BookFilter>(BookDao::class.java, BookFilter::class.java) {

    private val log = org.slf4j.LoggerFactory.getLogger(BookRest::class.java)

    /**
     * Initializes new books for adding.
     */
    override fun newBaseDO(): BookDO {
        val book = BookDO()
        book.status = BookStatus.PRESENT
        book.type = BookType.BOOK
        return book
    }

    override fun validate(obj: BookDO): List<ValidationError>? {
        val errorsList = mutableListOf<ValidationError>()
        try {
            val year = Integer.parseInt(obj.yearOfPublishing)
            if (year < Const.MINYEAR || year > Const.MAXYEAR) {
                errorsList.add(ValidationError(translate("error.yearOutOfRange", Const.MINYEAR, Const.MAXYEAR), fieldId = "yearOfPublishing"))
            }
        } catch (ex: NumberFormatException) {
            errorsList.add(ValidationError(translate("book.error.number"), fieldId = "yearOfPublishing"))
        }
        if (baseDao!!.doesSignatureAlreadyExist(obj)) {
            errorsList.add(ValidationError(translate("book.error.signatureAlreadyExists"), fieldId = "signature"))
        }
        if (errorsList.isEmpty()) return null
        return errorsList
    }

    /**
     * Lends the given book out by the logged-in user.
     */
    @POST
    @Path("lendOut")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun lendOut(book: BookDO): Response {
        book.setLendOutDate(Date())
        baseDao!!.setLendOutBy(book, getUserId())
        return RestHelper.saveOrUpdate(baseDao, book, validate(book))
    }

    /**
     * Lends the given book out by the logged-in user.
     */
    @POST
    @Path("returnBook")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun returnBook(book: BookDO): Response {
        book.lendOutBy = null
        book.lendOutDate = null
        book.lendOutComment = null
        return RestHelper.saveOrUpdate(baseDao, book, validate(book))
    }

}