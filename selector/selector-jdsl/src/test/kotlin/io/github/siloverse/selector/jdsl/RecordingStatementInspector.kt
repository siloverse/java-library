package io.github.siloverse.selector.jdsl

import org.hibernate.resource.jdbc.spi.StatementInspector

/** Hibernate hands every SQL statement through here before running it. The tests read what it wrote. */
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
