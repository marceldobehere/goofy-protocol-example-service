package com.masl.goofy_irc_be.crypto;

import com.masl.goofy_protocol_core.crypto.connected.GenericHandleCrypto;
import com.masl.goofy_protocol_core.crypto.connected.HandleCryptoHelper;
import com.masl.goofy_irc_be.entity.CachedKeyHandleEntry;
import com.masl.goofy_irc_be.entity.User;
import com.masl.goofy_irc_be.repository.CachedKeyHandleRepository;
import com.masl.goofy_irc_be.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class HandleHelper implements HandleCryptoHelper {
    private static final String HANDLE_WORDS_PATH = "data/handle_words.json";
    private static final Logger log = LoggerFactory.getLogger(HandleHelper.class);

    private final CachedKeyHandleRepository cachedKeyHandleRepository;
    private final UserRepository userRepository;

    public HandleHelper(CachedKeyHandleRepository cachedKeyHandleRepository, UserRepository userRepository) {
        this.cachedKeyHandleRepository = cachedKeyHandleRepository;
        this.userRepository = userRepository;
    }

    // Load Word List (Currently ~15000 Entries)
    // Stored in resources/data/handle_words.json
    @Override
    synchronized public List<String> loadWordList() {
        try {
            ClassPathResource resource = new ClassPathResource(HANDLE_WORDS_PATH);
            ObjectMapper mapper = new ObjectMapper();
            String[] words = mapper.readValue(resource.getInputStream(), String[].class);
            List<String> wordList = new ArrayList<>(Arrays.asList(words));
            log.debug("Loaded {} words for handle generation", wordList.size());
            return wordList;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load handle words from " + HANDLE_WORDS_PATH, e);
        }
    }

    @Override
    public Map<String, String> loadPersistedKeyToHandleMapCache() {
        // TODO: Add pruning of old entries if the table has exceeded a certain amount of entries

        return cachedKeyHandleRepository.findAll().stream()
                .collect(Collectors.toMap(
                        CachedKeyHandleEntry::getPubSplitKey,
                        CachedKeyHandleEntry::getHandle,
                        (_, b) -> b));
    }

    @Override
    public boolean addPersistedKeyToHandleMapping(String pubSplitKey, String handle) {
        cachedKeyHandleRepository.save(new CachedKeyHandleEntry(pubSplitKey, handle, Instant.now()));
        return true;
    }

    @Override
    public Map<String, String> loadUserKeyToHandleMap() {
        Map<String, String> resMap = new HashMap<>();

        resMap.putAll(userRepository.findAll().stream()
                .collect(Collectors.toMap(
                        User::getPubSplitKey,
                        User::getHandle,
                        (_, b) -> b)));

        return resMap;
    }

    @Override
    public String lookupPubSplitKeyForHandleExternally(String handle) {
        String strippedHandle = GenericHandleCrypto.stripPotentialDomainFromHandle(handle);
        String optDomain = GenericHandleCrypto.getPotentialDomainFromHandle(handle);

        // Check internal Storage / DBs for potential Mappings
        // TODO: Also Check Identity Storage
        // TODO: Implement

        // Potential Look up
        if (optDomain != null) {
            // TODO: Look up, by asking the domain FIS
            return null;
        } else {
            // TODO: Potentially ask already known FIS Servers or throw Exception
            return null;
        }
    }
}
