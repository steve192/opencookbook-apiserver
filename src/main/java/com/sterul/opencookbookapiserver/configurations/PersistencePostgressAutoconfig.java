package com.sterul.opencookbookapiserver.configurations;

import java.util.HashMap;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@PropertySource({ "classpath:application.properties" })
@EnableJpaRepositories(basePackages = "com.sterul.opencookbookapiserver.repositoriespostgress", entityManagerFactoryRef = "postgressEntityManager", transactionManagerRef = "postgressTransactionManager")
public class PersistencePostgressAutoconfig {

    @Bean
    @ConfigurationProperties(prefix = "spring.second-datasource")
    public DataSource postgressDataSource() {
        return DataSourceBuilder.create().build();
    }

    
 @Bean
    public LocalContainerEntityManagerFactoryBean postgressEntityManager() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(postgressDataSource());
        em.setPackagesToScan(
                new String[] { "com.sterul.opencookbookapiserver.repositoriespostgress" });

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto",
       "create-only");
        properties.put("hibernate.dialect",
        "org.hibernate.dialect.PostgreSQLDialect");
        em.setJpaPropertyMap(properties);

        return em;
    }


    @Bean
    public PlatformTransactionManager postgressTransactionManager() {

        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(
                postgressEntityManager().getObject());
        return transactionManager;
    }
}