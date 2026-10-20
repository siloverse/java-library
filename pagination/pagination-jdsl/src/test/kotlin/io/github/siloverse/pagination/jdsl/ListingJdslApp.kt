package io.github.siloverse.pagination.jdsl

import com.linecorp.kotlinjdsl.render.jpql.JpqlRenderContext
import com.linecorp.kotlinjdsl.support.spring.data.jpa.autoconfigure.KotlinJdslJpaRepositoryFactoryBeanPostProcessor
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutor
import com.linecorp.kotlinjdsl.support.spring.data.jpa.repository.KotlinJdslJpqlExecutorImpl
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

/**
 * A plain Spring JPA context with the Kotlin JDSL executor wired by hand, no Boot.
 * Data: 1000 listings, ten per second (created_at ties), status open/closed/draft in turn,
 * price = 1000 - row so price order differs from insertion order. Index on (created_at, id).
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackageClasses = [ListingRepository::class])
@Import(KotlinJdslJpaRepositoryFactoryBeanPostProcessor::class)
open class ListingJdslApp {

    @Bean open fun dataSource(): DataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)

    @Bean open fun entityManagerFactory(dataSource: DataSource) =
        LocalContainerEntityManagerFactoryBean().apply {
            this.dataSource = dataSource
            setPackagesToScan(Listing::class.java.packageName)
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(
                mapOf(
                    "hibernate.hbm2ddl.auto" to "none",
                    "hibernate.session_factory.statement_inspector" to RecordingStatementInspector::class.java.name,
                ),
            )
        }

    @Bean open fun transactionManager(emf: EntityManagerFactory): PlatformTransactionManager = JpaTransactionManager(emf)

    @Bean open fun entityManager(emf: EntityManagerFactory): EntityManager = SharedEntityManagerCreator.createSharedEntityManager(emf)

    @Bean open fun jpqlRenderContext(): JpqlRenderContext = JpqlRenderContext()

    @Bean open fun kotlinJdslJpqlExecutor(entityManager: EntityManager, renderContext: JpqlRenderContext): KotlinJdslJpqlExecutor =
        KotlinJdslJpqlExecutorImpl(entityManager, renderContext, metadata = null)

    companion object {
        lateinit var postgres: PostgreSQLContainer

        fun start(): AnnotationConfigApplicationContext {
            postgres = PostgreSQLContainer("postgres:17-alpine").also { it.start() }
            seed(JdbcTemplate(DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)))
            return AnnotationConfigApplicationContext(ListingJdslApp::class.java)
        }

        private fun seed(jdbc: JdbcTemplate) {
            jdbc.execute("CREATE TABLE listing (id uuid PRIMARY KEY, status text NOT NULL, price bigint NOT NULL, created_at timestamptz NOT NULL)")
            jdbc.execute("CREATE INDEX listing_created_at_id ON listing (created_at, id)")
            jdbc.execute(
                """
                INSERT INTO listing (id, status, price, created_at)
                SELECT gen_random_uuid(), (ARRAY['open','closed','draft'])[g % 3 + 1], 1000 - g,
                       timestamptz '2026-01-01 00:00:00+00' + (g / 10) * interval '1 second'
                FROM generate_series(1, 1000) g
                """,
            )
            jdbc.execute("ANALYZE listing")
        }
    }
}
