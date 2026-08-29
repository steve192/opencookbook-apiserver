package com.sterul.opencookbookapiserver.configurations;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.cfg.ConstructorDetector;

@Configuration
public class JacksonConfiguration {

    /**
     * The API models are mutable POJOs: Lombok gives them a no-arg constructor, setters and -
     * for the builders - an all-args constructor. Jackson 3 would treat that all-args constructor
     * as an implicit creator, which binds by constructor parameter name rather than by property
     * name ({@code isPublicIngredient} instead of {@code publicIngredient}) and turns every absent
     * primitive into a {@code null} it cannot map.
     * <p>
     * Binding through the no-arg constructor and the setters keeps the published JSON contract
     * symmetric with what these models serialize to.
     */
    @Bean
    JsonMapperBuilderCustomizer bindThroughSettersWhenADefaultConstructorExists() {
        return builder -> builder.constructorDetector(
                ConstructorDetector.DEFAULT.withAllowImplicitWithDefaultConstructor(false));
    }
}
