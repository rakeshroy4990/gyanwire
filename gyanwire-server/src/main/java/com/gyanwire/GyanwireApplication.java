package com.gyanwire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class GyanwireApplication {
    private static final Logger log = LoggerFactory.getLogger(GyanwireApplication.class);
    private static final String PERSISTENCE_PROVIDER_ENV = "APP_PERSISTENCE_PROVIDER";

    public static void main(String[] args) {
        String persistence = resolvePersistenceProvider();
        if (!"postgres".equalsIgnoreCase(persistence)) {
            throw new IllegalStateException(
                    "Only PostgreSQL is supported. Set APP_PERSISTENCE_PROVIDER=postgres.");
        }

        SpringApplication app = new SpringApplication(GyanwireApplication.class);
        Map<String, Object> defaults = new HashMap<>();
        log.info("Using PostgreSQL persistence (Flyway + JPA).");
        defaults.put("app.persistence.provider", "postgres");
        defaults.put("spring.flyway.enabled", "true");
        defaults.put("spring.jpa.hibernate.ddl-auto", "none");
        defaults.put("spring.jpa.open-in-view", "false");
        app.setDefaultProperties(defaults);
        app.run(args);
    }

    private static String resolvePersistenceProvider() {
        String v = System.getenv(PERSISTENCE_PROVIDER_ENV);
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        v = System.getProperty("app.persistence.provider");
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        return "postgres";
    }
}
