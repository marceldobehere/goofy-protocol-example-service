package com.masl.goofy_irc_be.test_data.test_only;

import com.masl.goofy_protocol_core.crypto.connected.HandleCrypto;
import com.masl.goofy_protocol_core.crypto.connected.HandleCryptoHelper;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.AsymmCrypto;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.GlobAsymmCrypto;
import com.masl.goofy_irc_be.entity.User;
import com.masl.goofy_irc_be.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@Order(1)
public class TestDataUser {
    private static final Logger log = LoggerFactory.getLogger(TestDataUser.class);
    private static final GlobAsymmCrypto asymmCrypto = new GlobAsymmCrypto();

    private final UserRepository userRepository;
    private final HandleCrypto handleCrypto;

    public AsymmCrypto.AsymmFullKeyPair testUser;
    public AsymmCrypto.AsymmFullKeyPair testUserTwo;
    public AsymmCrypto.AsymmFullKeyPair testAdmin;

    public TestDataUser(UserRepository userRepository, HandleCryptoHelper helper) {
        this.userRepository = userRepository;
        this.handleCrypto = new HandleCrypto(helper);
    }

    @PostConstruct
    public void init() {
        // Check if Users already exist?
        if (userRepository.count() > 0) {
            log.error("TestDataUser: USERS ALREADY EXIST IN DB, EVEN THOUGH IT SHOULD BE A FRESH IN-MEMORY DB???");
            throw new RuntimeException("USERS ALREADY EXIST IN DB");
        }

        // Test User
        testUser = asymmCrypto.generateKeypair();
        User testUserEntity = new User();
        testUserEntity.setPubSplitKey(testUser.pub().serialize());
        testUserEntity.setHandle(handleCrypto.deriveHandle(testUser.pub().serialize()));
        testUserEntity.setAdmin(false);
        userRepository.save(testUserEntity);
        log.info("> Generated Test User: {}", handleCrypto.deriveHandle(testUser.pub().serialize()));


        // Test User 2
        testUserTwo = asymmCrypto.generateKeypair();
        User testUserTwoEntity = new User();
        testUserTwoEntity.setPubSplitKey(testUserTwo.pub().serialize());
        testUserTwoEntity.setHandle(handleCrypto.deriveHandle(testUserTwo.pub().serialize()));
        testUserTwoEntity.setAdmin(false);
        userRepository.save(testUserTwoEntity);
        log.info("> Generated Test User 2: {}", handleCrypto.deriveHandle(testUserTwo.pub().serialize()));

        // Test Admin
        testAdmin = asymmCrypto.generateKeypair();
        User testAdminEntity = new User();
        testAdminEntity.setPubSplitKey(testAdmin.pub().serialize());
        testAdminEntity.setHandle(handleCrypto.deriveHandle(testAdmin.pub().serialize()));
        testAdminEntity.setAdmin(true);
        userRepository.save(testAdminEntity);
        log.info("> Generated Test Admin: {}", handleCrypto.deriveHandle(testAdmin.pub().serialize()));
    }
}
