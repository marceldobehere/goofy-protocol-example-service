package com.masl.goofy_irc_be.integration;

import com.masl.goofy_protocol_core.crypto.connected.HandleCryptoHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class IsolatedHandleHelper implements HandleCryptoHelper {
    private static final String HANDLE_WORDS_PATH = "data/handle_words.json";
    private static final Logger log = LoggerFactory.getLogger(IsolatedHandleHelper.class);

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
        return Map.of();
    }

    @Override
    public boolean addPersistedKeyToHandleMapping(String pubSplitKey, String handle) {
        return false;
    }

    @Override
    public Map<String, String> loadUserKeyToHandleMap() {
        return Map.of();
    }

    @Override
    public String lookupPubSplitKeyForHandleExternally(String handle) {return null;}
}
