package com.masl.goofy_irc_be.crypto;

import com.masl.goofy_irc_be.dto.request.HandleLookupDto;
import com.masl.goofy_irc_be.properties.GeneralProperties;
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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
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
    private final GeneralProperties generalProperties;

    private final RestClient restClient = RestClient.create();
    public static final String[] supportedFisProtocols = new String[] { "https://", "http://" };

    public HandleHelper(CachedKeyHandleRepository cachedKeyHandleRepository, UserRepository userRepository, GeneralProperties generalProperties) {
        this.cachedKeyHandleRepository = cachedKeyHandleRepository;
        this.userRepository = userRepository;
        this.generalProperties = generalProperties;
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
        cachedKeyHandleRepository.save(new CachedKeyHandleEntry(pubSplitKey, handle, null, Instant.now()));
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

    public HandleLookupDto attemptLookup(String handle) {
        return attemptLookup(handle, 0);
    }

    // TODO: Test
    private HandleLookupDto attemptLookup(String handle, int cDepth) {
        if (cDepth > 10) {
            log.warn("Max lookup depth reached for handle {} at depth {}", handle, cDepth);
            return null;
        }

        String strippedHandle = GenericHandleCrypto.stripPotentialDomainFromHandle(handle);
        String optDomain = GenericHandleCrypto.getPotentialDomainFromHandle(handle);

        // Unknown
        if (optDomain == null)
            return null;

        log.debug("Attempting to look up handle {} at domain {}", strippedHandle, optDomain);
        for (var protocol : supportedFisProtocols) {
            try {
                // TODO: Disable local IP Addresses / Domains / localhost if not in dev mode
                HandleLookupDto lookupDto = restClient.get()
                        .uri(protocol + optDomain + "/fis-api/user/lookup/" + strippedHandle)
                        .retrieve()
                        .body(HandleLookupDto.class);

                if (lookupDto != null && strippedHandle.equals(lookupDto.getHandle()) &&  lookupDto.getPubKey() != null && !lookupDto.getPubKey().isBlank()) {
                    if (lookupDto.isRegisteredHere() && (lookupDto.getHandleDomain() == null || optDomain.equalsIgnoreCase(lookupDto.getHandleDomain()))) {
                        log.debug("Successfully looked up handle {} at domain {}: {}", strippedHandle, optDomain, lookupDto.getPubKey());
                        return lookupDto;
                    } else if (lookupDto.getHandleDomain() != null) {
                        log.debug("Handle is at home somewhere else, continuing lookup for handle {} at domain {}: {}", strippedHandle, optDomain, lookupDto.getHandleDomain());
                        return attemptLookup(strippedHandle + "@" + lookupDto.getHandleDomain(), cDepth + 1);
                    }
                } else {
                    log.warn("Handle {} at domain {} returned no public key", strippedHandle, optDomain);
                }
            } catch (RestClientException e) {
                log.info("Failed to look up handle {} at domain {}: {}", strippedHandle, optDomain, e.getMessage());
            } catch (Exception e) {
                log.warn("Unexpected error while looking up handle {} at domain {}: {}", strippedHandle, optDomain, e.getMessage());
            }
        }
        return null;
    }

    @Override
    public String lookupPubSplitKeyForHandleExternally(String handle) {
        String strippedHandle = GenericHandleCrypto.stripPotentialDomainFromHandle(handle);
        String optDomain = GenericHandleCrypto.getPotentialDomainFromHandle(handle);

        // Check internal Storage / DBs for potential Mappings
        User maybeUser = userRepository.findByHandle(strippedHandle);
        if (maybeUser != null)
            return maybeUser.getPubSplitKey();

        // Potentially yoink domain from Cache
        if (optDomain == null) {
            CachedKeyHandleEntry entry = cachedKeyHandleRepository.findByHandle(strippedHandle);
            if (entry != null && entry.getHandleDomain() != null) {
                optDomain = entry.getHandleDomain();
                log.debug("Found cached domain {} for handle {}", optDomain, strippedHandle);
            }
        }

        // Unknown
        if (optDomain == null)
            return null;

        // Avoid potential loop
        if (generalProperties.getDomain().contains(optDomain) || generalProperties.getUrl().contains(optDomain)) {
            log.debug("Skipping external lookup for handle {} at domain {} because it is our own domain", strippedHandle, optDomain);
            return null;
        }

        // Attempt Look up
        var res = attemptLookup(handle);
        return res == null ? null : res.getPubKey();
    }
}
