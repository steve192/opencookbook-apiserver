package com.sterul.opencookbookapiserver;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableConfigurationProperties(OpencookbookConfiguration.class)
@EnableCaching
public class OpencookbookApiserverApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpencookbookApiserverApplication.class, args);
	}

}
