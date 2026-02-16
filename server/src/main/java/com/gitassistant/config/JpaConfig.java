package com.gitassistant.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * JPA configuration with explicit transaction manager.
 * This is the PRIMARY transaction manager used by default.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.gitassistant.repositories",
        transactionManagerRef = "transactionManager"
)
public class JpaConfig {

        @Bean
        @Primary
        public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
                return new JpaTransactionManager(entityManagerFactory);
        }
}