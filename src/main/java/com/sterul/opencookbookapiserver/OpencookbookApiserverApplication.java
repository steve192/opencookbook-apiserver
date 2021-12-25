package com.sterul.opencookbookapiserver;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(OpencookbookConfiguration.class)
public class OpencookbookApiserverApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpencookbookApiserverApplication.class, args);
	}

}
