package com.sterul.opencookbookapiserver.configurations;

import java.util.HashMap;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@PropertySource({ "classpath:application.properties" })
@EnableJpaRepositories(basePackages = "com.sterul.opencookbookapiserver.repositories", entityManagerFactoryRef = "h2EntityManager", transactionManagerRef = "h2TransactionManager")
public class PersistenceH2Autoconfig {

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSource h2DataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean h2EntityManager() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(h2DataSource());
        em.setPackagesToScan(
                new String[] { "com.sterul.opencookbookapiserver.repositories" });

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        HashMap<String, Object> properties = new HashMap<>();
        // properties.put("hibernate.hbm2ddl.auto",
        //         "create-only");
        // properties.put("hibernate.dialect",
        //         "org.hibernate.dialect.H2Dialect");
        properties.put("hibernate.id.new_generator_mappings",
                true);
        // properties.put("hibernate.physical_naming_strategy",
        //         "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        properties.put("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        properties.put("hibernate.implicit_naming_strategy",
                "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
        properties.put("hibernate.archive.scanner",
                "org.hibernate.boot.archive.scan.internal.DisabledScanner");
        em.setJpaPropertyMap(properties);

        return em;
    }

    @Primary
    @Bean
    public PlatformTransactionManager h2TransactionManager() {

        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(
                h2EntityManager().getObject());
        return transactionManager;
    }
}
