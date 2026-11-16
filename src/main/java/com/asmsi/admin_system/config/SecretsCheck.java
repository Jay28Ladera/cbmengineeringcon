package com.asmsi.admin_system.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Stops start-up with a clear message when the database settings are missing
 * (no secrets.properties on this PC and no environment variables), instead of a long driver error.
 */
public class SecretsCheck implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String url;
        try { url = env.getProperty("spring.datasource.url"); } catch (IllegalArgumentException e) { url = null; }
        if (url == null || url.isBlank() || url.contains("${")) {
            throw new IllegalStateException("""

                    ==========================================================================
                    The database settings are missing (DB_URL, DB_USERNAME, DB_PASSWORD).
                    On your PC: copy secrets.example.properties to secrets.properties in the
                    project folder (next to pom.xml), fill in the values, and run again from
                    that folder. Working folder now: %s
                    On Render: add them in the service's Environment tab.
                    ==========================================================================
                    """.formatted(System.getProperty("user.dir")));
        }
    }
}
