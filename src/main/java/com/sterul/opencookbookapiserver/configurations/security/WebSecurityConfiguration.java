package com.sterul.opencookbookapiserver.configurations.security;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authorization.AllRequiredFactorsAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.header.writers.ContentSecurityPolicyHeaderWriter;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.controllers.admin.AdminPaths;
import com.sterul.opencookbookapiserver.controllers.errors.ApiErrorWriter;
import com.sterul.opencookbookapiserver.controllers.legal.LegalPaths;
import com.sterul.opencookbookapiserver.controllers.setup.SetupPaths;
import com.sterul.opencookbookapiserver.controllers.sharing.SharePaths;
import com.sterul.opencookbookapiserver.entities.account.Role;
import com.sterul.opencookbookapiserver.errors.ApiErrorCode;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@SuppressWarnings("java:S1075") // Paths of this server's own endpoints, matched where they are served.
public class WebSecurityConfiguration {

        /**
         * Everything a stranger can reach to set up, sign up or sign in. Also what the auth rate
         * limit counts, so the two cannot drift apart and leave a new endpoint uncounted.
         */
        static final String[] UNAUTHENTICATED_ACCOUNT_PATHS = {
                        SetupPaths.BASE,
                        "/api/v1/users/signup",
                        "/api/v1/users/activate",
                        "/api/v1/users/resendActivationLink",
                        "/api/v1/users/requestPasswordReset",
                        "/api/v1/users/resetPassword",
                        "/api/v1/users/login",
                        "/api/v1/users/login/google"
        };

        /**
         * Public as well, but left out of the budget: the app renews every few minutes, so a
         * household behind one address would exhaust any sane limit in normal use.
         */
        private static final String REFRESH_TOKEN_PATH = "/api/v1/users/refreshToken";

        /** Signs out with the refresh token alone, as the access token may have run out. */
        private static final String LOGOUT_PATH = "/api/v1/users/logout";

        /** Fetched by Bring without a token; creating an export needs one. */
        private static final String BRING_EXPORT_PATH = "/api/v1/bringexport";

        private static final String ADMIN_PANEL = "/admin/**";

        private static final String[] AUTH_WHITELIST = Stream.concat(
                        Arrays.stream(UNAUTHENTICATED_ACCOUNT_PATHS),
                        Stream.of(
                                        REFRESH_TOKEN_PATH,
                                        LOGOUT_PATH,
                                        "/swagger-ui/**",
                                        "/api-docs/**",
                                        "/api/v1/instance/**",
                                        SharePaths.PUBLIC_PATTERN,
                                        LegalPaths.PUBLIC_PATTERN,
                                        "/error",
                                        "/actuator/health",
                                        ADMIN_PANEL))
                        .toArray(String[]::new);

        private static final RequestMatcher PUBLIC_REQUESTS = new OrRequestMatcher(Stream.concat(
                        Arrays.stream(AUTH_WHITELIST).map(PathPatternRequestMatcher.withDefaults()::matcher),
                        Stream.of(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, BRING_EXPORT_PATH)))
                        .toList());

        /** The admin panel's bundle; everything it loads comes from this server. */
        private static final String ADMIN_PANEL_POLICY = "default-src 'self'; script-src 'self'; "
                        + "style-src 'self' 'unsafe-inline'; img-src 'self' data: blob:; font-src 'self' data:; "
                        + "connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; "
                        + "frame-ancestors 'self'";

        /** Applied by Spring Security to every filter chain, the api key one included. */
        @Bean
        Customizer<HttpSecurity> statelessApi(AuthenticationEntryPoint authenticationRequired) {
                return http -> http
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationRequired));
        }

        @Bean
        AuthenticationEntryPoint authenticationRequired(ApiErrorWriter errorWriter) {
                return errorWriter.entryPoint(ApiErrorCode.AUTHENTICATION_REQUIRED);
        }

        @Bean
        AccessDeniedHandler accessDenied(ApiErrorWriter errorWriter) {
                return errorWriter.deniedHandler(ApiErrorCode.ACCESS_DENIED);
        }

        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE)
        public SecurityFilterChain filterChain(HttpSecurity http,
                        AuthenticationEntryPoint authenticationRequired,
                        AccessDeniedHandler accessDenied,
                        ApiErrorWriter errorWriter,
                        AccessTokenAuthenticationConverter accessTokenConverter,
                        OpencookbookConfiguration configuration) {

                // Cors not needed in an api server
                http.cors(configurer -> configurer.configurationSource(c -> allowAllCorsConfig()));

                // Allow frames needed for h2 console; the admin panel may only run what it ships
                http.headers(config -> config.frameOptions(FrameOptionsConfig::sameOrigin)
                                .addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
                                                PathPatternRequestMatcher.withDefaults().matcher(ADMIN_PANEL),
                                                new ContentSecurityPolicyHeaderWriter(ADMIN_PANEL_POLICY))));

                // The role first, so that anybody else is refused rather than asked for a password
                http.authorizeHttpRequests(
                                authorize -> authorize.requestMatchers(PUBLIC_REQUESTS).permitAll()
                                                .requestMatchers(AdminPaths.BASE + "/**").access(AuthorizationManagers.allOf(
                                                                AuthorityAuthorizationManager.hasRole(Role.ADMIN.name()),
                                                                recentPassword(configuration)))
                                                .anyRequest().authenticated());

                http.oauth2ResourceServer(resourceServer -> resourceServer
                                .bearerTokenResolver(WebSecurityConfiguration::accessTokenOf)
                                .jwt(jwt -> jwt.jwtAuthenticationConverter(accessTokenConverter))
                                .authenticationEntryPoint(authenticationRequired)
                                // Not in exceptionHandling: a handler set there bypasses the one for a missing password
                                .accessDeniedHandler(accessDenied));

                http.exceptionHandling(configurer -> configurer.defaultDeniedHandlerForMissingAuthority(
                                errorWriter.entryPoint(ApiErrorCode.REAUTHENTICATION_REQUIRED),
                                FactorGrantedAuthority.PASSWORD_AUTHORITY));

                return http.build();
        }

        private static AuthorizationManager<RequestAuthorizationContext> recentPassword(
                        OpencookbookConfiguration configuration) {
                var validFor = configuration.getAuth().getAdminSignInValidity();
                return AllRequiredFactorsAuthorizationManager.<RequestAuthorizationContext>builder()
                                .requireFactor(factor -> factor.passwordAuthority().validDuration(validFor))
                                .build();
        }

        /**
         * From the header, or a socket's subprotocol. Public endpoints never look at a token, so one that
         * has run out cannot turn them away.
         */
        private static String accessTokenOf(HttpServletRequest request) {
                return PUBLIC_REQUESTS.matches(request) ? null : BearerTokens.of(request).orElse(null);
        }

        private CorsConfiguration allowAllCorsConfig() {
                List<String> permittedCorsMethods = Collections.unmodifiableList(Arrays.asList(
                                HttpMethod.GET.name(),
                                HttpMethod.HEAD.name(),
                                HttpMethod.POST.name(),
                                HttpMethod.PUT.name(),
                                HttpMethod.DELETE.name()));

                var corsConfiguration = new CorsConfiguration().applyPermitDefaultValues();
                corsConfiguration.setAllowedMethods(permittedCorsMethods);
                return corsConfiguration;

        }

        /**
         * The state of an account is only checked once the password is right, so that it is told to
         * whoever holds the password and to nobody who merely knows the address.
         */
        @Bean
        AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                        PasswordEncoder passwordEncoder) {
                var provider = new DaoAuthenticationProvider(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder);
                provider.setPreAuthenticationChecks(user -> {
                });
                provider.setPostAuthenticationChecks(new AccountStatusUserDetailsChecker());
                return new ProviderManager(provider);
        }

}
