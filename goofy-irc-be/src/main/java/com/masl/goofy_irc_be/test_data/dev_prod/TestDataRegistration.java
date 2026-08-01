package com.masl.goofy_irc_be.test_data.dev_prod;

import com.masl.goofy_irc_be.entity.RegistrationCode;
import com.masl.goofy_irc_be.service.RegistrationService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
@Profile({"dev", "prod"})
public class TestDataRegistration implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(TestDataRegistration.class);

    private final RegistrationService registrationService;

    public TestDataRegistration(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @Transactional
    @Override
    public void run(String... args) {
        log.info("> Checking for existing registration codes...");
        if (registrationService.anyUsedCodesExist())  {
            log.info("> Registration codes already exist and have been used.");
            return;
        }

        if (registrationService.anyCodesExist()) {
            log.info("> Registration codes already exist, but have not been used!");
            RegistrationCode code = registrationService.getAllUnusedCodes().getFirst();
            log.warn("> Existing unused registration code: {}", code.getCode());
            return;
        }

        // Create Starter Admin Code
        RegistrationCode code = registrationService.createNewRegistrationCode(true);
        log.warn("> Created initial admin registration code: {}", code.getCode());
    }
}
