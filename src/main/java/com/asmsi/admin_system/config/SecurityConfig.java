package com.asmsi.admin_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import com.asmsi.admin_system.repository.UserRepository;
import com.asmsi.admin_system.service.AccessService;
import com.asmsi.admin_system.service.UserService;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final AccessService accessService;

    public SecurityConfig(UserService userService, BCryptPasswordEncoder passwordEncoder,
                          UserRepository userRepository, AccessService accessService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
        this.accessService = accessService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Reload a signed-in person's account after an admin changes it (see PrincipalRefreshFilter).
                .addFilterBefore(new PrincipalRefreshFilter(userRepository, accessService), AnonymousAuthenticationFilter.class)
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        // Public website, sign-in pages and static files
                        .requestMatchers(
                                "/", "/login", "/signup", "/request-account",
                                "/forget-password", "/forgot-password", "/reset-password", "/activate",
                                "/inquiries/public", "/Clients/public",
                                "/css/**", "/js/**", "/images/**", "/vendor/**", "/webjars/**",
                                "/favicon.ico", "/error")
                        .permitAll()
                        // User approval and administration: admins only
                        .requestMatchers("/users/**", "/Admin/**").hasAnyRole("ADMIN", "CEO")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/home", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
