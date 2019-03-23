package org.projectforge.rest.json

import com.google.gson.GsonBuilder
import org.projectforge.framework.persistence.user.entities.PFUserDO
import org.projectforge.framework.persistence.user.entities.TenantDO
import org.projectforge.rest.converter.UTCDateTimeTypeAdapter
import org.projectforge.rest.converter.UTCDateTypeAdapter
import java.util.*

object JsonCreator {
    private val typeAdapterMap = HashMap<Class<*>, Any>()

    init {
        add(java.sql.Date::class.java, UTCDateTypeAdapter())
        add(java.util.Date::class.java, UTCDateTimeTypeAdapter())
        add(PFUserDO::class.java, PFUserDOSerializer())
        add(TenantDO::class.java, TenantDOSerializer())
    }

    fun add(cls: Class<*>, typeAdapter: Any) {
        typeAdapterMap[cls] = typeAdapter
    }

    fun toJson(obj: Any): String {
        val builder = GsonBuilder()
        for ((key, value) in typeAdapterMap) {
            builder.registerTypeHierarchyAdapter(key, value)
        }
        val gson = builder.create()
        return gson.toJson(obj)
    }
}