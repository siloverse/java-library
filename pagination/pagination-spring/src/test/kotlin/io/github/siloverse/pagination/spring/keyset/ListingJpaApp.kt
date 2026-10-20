package io.github.siloverse.pagination.spring.keyset

import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.PlatformTransactionManager
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource

/** A plain Spring JPA context over the fixture database. No Boot. Every SQL statement is recorded. */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackageClasses = [ListingRepository::class])
open class ListingJpaApp {

    @Bean open fun dataSource(): DataSource = ListingFixture.dataSource(postgres)

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

    @Bean open fun transactionManager(emf: jakarta.persistence.EntityManagerFactory): PlatformTransactionManager =
        JpaTransactionManager(emf)

    companion object {
        lateinit var postgres: PostgreSQLContainer

        fun start(container: PostgreSQLContainer): AnnotationConfigApplicationContext {
            postgres = container
            return AnnotationConfigApplicationContext(ListingJpaApp::class.java)
        }
    }
}
