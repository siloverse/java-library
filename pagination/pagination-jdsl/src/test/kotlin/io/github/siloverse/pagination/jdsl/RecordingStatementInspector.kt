package io.github.siloverse.pagination.jdsl

import org.hibernate.resource.jdbc.spi.StatementInspector

class RecordingStatementInspector : StatementInspector {

    override fun inspect(sql: String): String {
        statements += sql
        return sql
    }

    companion object {
        val statements = mutableListOf<String>()
        fun reset() = statements.clear()
        fun lastSelect(): String = statements.last { it.startsWith("select") }
    }
}
