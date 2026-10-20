package io.github.siloverse.pagination.spring.keyset

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
        fun selectsOn(table: String): List<String> = statements.filter { it.startsWith("select") && it.contains(" $table ") }
    }
}
