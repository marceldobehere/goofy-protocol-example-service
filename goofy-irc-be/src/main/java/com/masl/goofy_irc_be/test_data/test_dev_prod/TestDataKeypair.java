package com.masl.goofy_irc_be.test_data.test_dev_prod;

import com.masl.goofy_protocol_core.crypto.connected.HandleCrypto;
import com.masl.goofy_protocol_core.crypto.connected.HandleCryptoHelper;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.AsymmCrypto;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.AsymmCryptoType;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.GlobAsymmCrypto;
import com.masl.goofy_irc_be.entity.ServerKeypair;
import com.masl.goofy_irc_be.repository.ServerKeypairRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile({"test", "dev", "prod"})
@Order(2)
public class TestDataKeypair {
    private static final Logger log = LoggerFactory.getLogger(TestDataKeypair.class);
    private static final GlobAsymmCrypto asymmCrypto = new GlobAsymmCrypto();

    private final ServerKeypairRepository keypairRepository;
    private final HandleCrypto handleCrypto;
    private final String keypairType;

    @Getter
    private AsymmCrypto.AsymmFullKeyPair serverKeypair;
    @Getter
    private String serverHandle;

    public TestDataKeypair(ServerKeypairRepository keypairRepository, HandleCryptoHelper helper, @Value("${goofy.general.keypair.type}") String keypairType) {
        this.keypairRepository = keypairRepository;
        this.handleCrypto = new HandleCrypto(helper);
        this.keypairType = keypairType;
    }

    @PostConstruct
    public void init() {
        if (keypairRepository.count() > 0) {
            ServerKeypair keypair = keypairRepository.findAll().getFirst();
            serverKeypair = AsymmCrypto.AsymmFullKeyPair.fromParts(keypair.getPubSplitKey(), keypair.getPrivSplitKey());
            serverHandle = handleCrypto.deriveHandle(serverKeypair.pub().serialize());
            if (!asymmCrypto.checkPublicSplitKey(serverKeypair.pub().serialize()))
                throw new RuntimeException("Server Keypair public split key is invalid!");

            log.info("> Server identity: {}", serverHandle);
            return;
        }

        log.info("> No Server identity yet");
        serverKeypair = asymmCrypto.generateKeypair(AsymmCryptoType.valueOf(keypairType));
        serverHandle = handleCrypto.deriveHandle(serverKeypair.pub().serialize());
        ServerKeypair serverKeypairEntity = new ServerKeypair();
        serverKeypairEntity.setPubSplitKey(serverKeypair.pub().serialize());
        serverKeypairEntity.setPrivSplitKey(serverKeypair.priv().serialize());
        serverKeypairEntity.setHandle(serverHandle);
        keypairRepository.save(serverKeypairEntity);
        log.info("> Generated Server Keypair of type {} with handle: {}", keypairType, serverHandle);
    }
}
