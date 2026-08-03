package com.masl.goofy_irc_be;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.UUID;

public class IsolatedTestConfig implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Logger log = LoggerFactory.getLogger(IsolatedTestConfig.class);

    @Override
    public void initialize(@NonNull ConfigurableApplicationContext ctx) {
        String dbName = "mem-" + UUID.randomUUID();
        TestPropertyValues.of("spring.datasource.url=jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1").applyTo(ctx);
        log.info("Initialized in-memory H2 database for test: {}", dbName);
    }
}
