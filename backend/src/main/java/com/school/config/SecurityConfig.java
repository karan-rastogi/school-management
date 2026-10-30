package com.school.config;

import com.school.auth.security.AuthCookieService;
import com.school.auth.security.JwtAuthenticationFilter;
import com.school.auth.security.JwtService;
import com.school.auth.security.OAuth2LoginFailureHandler;
import com.school.auth.security.OAuth2LoginSuccessHandler;
import com.school.auth.security.UserAccountService;
import com.school.common.error.JsonAccessDeniedHandler;
import com.school.common.error.JsonAuthenticationEntryPoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * The Google sign-in chain. It only exists when {@code app.auth.oauth.enabled=true} (the
     * default); with the flag off the OAuth endpoints fall through to the API chain, where they are
     * permitted but unhandled, so they answer 404 and no real OAuth client keys are required.
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = "app.auth.oauth.enabled", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain oauth2LoginFilterChain(
            HttpSecurity http,
            OAuth2LoginSuccessHandler successHandler,
            OAuth2LoginFailureHandler failureHandler
    ) throws Exception {
        SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(new HttpSessionOAuth2AuthorizationRequestRepository()))
                        .successHandler(successHandler)
                        .failureHandler(failureHandler));

        securityHeaders(http);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            UserAccountService userAccountService,
            AuthCookieService authCookieService,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(jwtService, userAccountService, authCookieService);

        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Default-deny: only the paths below are reachable. Every protected path is listed
                // explicitly, so a new endpoint is denied until it is deliberately exposed here
                // (together with its method-security role check on the controller).
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers(
                                "/auth/login",
                                "/auth/config",
                                "/auth/otp/request",
                                "/auth/otp/verify",
                                "/auth/refresh",
                                "/auth/logout").permitAll()
                        // With app.auth.oauth.enabled=false these fall through to the dispatcher and
                        // answer 404; with the flag on the Order(1) chain above already handled them.
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // Signed-URL attachment downloads: the HMAC signature is the grant.
                        .requestMatchers("/notices/attachments/**").permitAll()
                        .requestMatchers("/me", "/me/**").authenticated()
                        .requestMatchers("/auth/change-password").authenticated()
                        .requestMatchers("/admin/**").authenticated()
                        .requestMatchers("/parent/**").authenticated()
                        .requestMatchers("/attendance/**").authenticated()
                        .requestMatchers("/classes/**").authenticated()
                        .requestMatchers("/notices/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        securityHeaders(http);
        return http.build();
    }

    /**
     * Response security headers for a JSON API: HSTS, no MIME sniffing, framing denied, no referrer
     * leakage and a restrictive CSP. HSTS is only sent on secure requests by Spring Security.
     */
    private static void securityHeaders(HttpSecurity http) throws Exception {
        http.headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .preload(true)
                        .maxAgeInSeconds(31_536_000))
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frameOptions -> frameOptions.deny())
                .referrerPolicy(referrer -> referrer
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(csp -> csp
                        .policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                .addHeaderWriter(new StaticHeadersWriter(
                        "Permissions-Policy", "geolocation=(), microphone=(), camera=()")));
    }
}
