package com.sterul.opencookbookapiserver;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.repositories.UserRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.PGUserRepository;
import com.sterul.opencookbookapiserver.repositoriespostgress.entities.account.CookpalUser;

import org.apache.tomcat.jni.User;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.event.EventListener;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableConfigurationProperties(OpencookbookConfiguration.class)
@EnableCaching
@EnableJpaAuditing
public class OpencookbookApiserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpencookbookApiserverApplication.class, args);
    }



}
