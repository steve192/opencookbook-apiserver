package com.sterul.opencookbookapiserver.configurations;

import com.sterul.opencookbookapiserver.requestfilters.AuthenticationFilter;
import com.sterul.opencookbookapiserver.requestfilters.AuthorizationFilter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;


@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration extends WebSecurityConfigurerAdapter{

    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String[] AUTH_WHITELIST = {
        "/api/v1/users/signup",
        "/login",
        "/swagger-ui/*",
        "/v3/api-docs",
        "/v3/api-docs/*"
    };
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
         http
             .cors().configurationSource(request -> new CorsConfiguration().applyPermitDefaultValues()).and()
             .csrf().disable()
             .authorizeRequests()
                 .antMatchers(AUTH_WHITELIST).permitAll()
                 .anyRequest().authenticated().and()
             .addFilter(new AuthenticationFilter(authenticationManager()))
             .addFilter(new AuthorizationFilter(authenticationManager()))
             .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }

    protected void configure(AuthenticationManagerBuilder authenticationManagerBuilder) throws Exception {
        // Use own authentication manager (UserDetailsServiceImpl)
        authenticationManagerBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
    }

  
}
