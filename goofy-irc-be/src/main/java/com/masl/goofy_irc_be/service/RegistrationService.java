package com.masl.goofy_irc_be.service;

import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.dto.request.RegistrationRequestDto;
import com.masl.goofy_irc_be.entity.*;
import com.masl.goofy_irc_be.exception.client.GenericNotFound;
import com.masl.goofy_irc_be.exception.client.HandleAlreadyRegistered;
import com.masl.goofy_irc_be.exception.client.InvalidRegisterCode;
import com.masl.goofy_irc_be.exception.client.RegistrationCodeAlreadyUsed;
import com.masl.goofy_irc_be.properties.RegisterProperties;
import com.masl.goofy_irc_be.repository.CachedKeyHandleRepository;
import com.masl.goofy_irc_be.repository.RegistrationCodeRepository;
import com.masl.goofy_irc_be.repository.RegistrationRequestRepository;
import com.masl.goofy_irc_be.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RegistrationService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final RegisterProperties registerProperties;
    private final RegistrationCodeRepository registrationCodeRepository;
    private final RegistrationRequestRepository registrationRequestRepository;
    private final CachedKeyHandleRepository cachedKeyHandleRepository;
    private final UserRepository userRepository;

    public RegistrationService(RegisterProperties registerProperties, RegistrationCodeRepository registrationCodeRepository, RegistrationRequestRepository registrationRequestRepository, CachedKeyHandleRepository cachedKeyHandleRepository, UserRepository userRepository) {
        this.registerProperties = registerProperties;
        this.registrationCodeRepository = registrationCodeRepository;
        this.registrationRequestRepository = registrationRequestRepository;
        this.cachedKeyHandleRepository = cachedKeyHandleRepository;
        this.userRepository = userRepository;
    }

    public RegistrationCode createNewRegistrationCode(boolean isAdmin) {
        return createNewRegistrationCode(null, isAdmin);
    }

    public RegistrationCode createNewRegistrationCode(User createdBy, boolean isAdmin) {
        RegistrationCode code = new RegistrationCode();
        code.setCode(UUID.randomUUID().toString());
        code.setAdmin(isAdmin);
        code.setCreatedBy(createdBy);
        code.setCreatedAt(Instant.now());
        log.info("Created new registration code: {} (Admin: {})", code.getCode(), isAdmin);
        return registrationCodeRepository.save(code);
    }

    public void deleteRegistrationCode(String code) {
        log.info("Deleting registration code: {}", code);
        registrationCodeRepository.deleteByCode(code);
    }

    public boolean anyCodesExist() {
        return registrationCodeRepository.count() > 0;
    }

    public boolean anyUsedCodesExist() {
        return !registrationCodeRepository.findAllByUsedAtIsNotNull().isEmpty();
    }

    public List<RegistrationCode> getAllUsedCodes() {
        return registrationCodeRepository.findAllByUsedAtIsNotNull();
    }

    public List<RegistrationCode> getAllUnusedCodes() {
        return registrationCodeRepository.findAllByUsedAtIsNull();
    }

    public boolean isCodeValid(String code) {
        return registrationCodeRepository.findByCodeAndUsedAtIsNull(code) != null;
    }

    public RegistrationCode getValidCode(String code) {
        return registrationCodeRepository.findByCodeAndUsedAtIsNull(code);
    }

    private void useCode(String code, User user) throws RegistrationCodeAlreadyUsed {
        RegistrationCode regCode = registrationCodeRepository.findById(code).orElseThrow(() -> new IllegalArgumentException("Invalid registration code"));
        if (regCode.getUsedAt() != null)
            throw new RegistrationCodeAlreadyUsed(code);

        regCode.setUsedBy(user);
        regCode.setUsedAt(Instant.now());
        registrationCodeRepository.save(regCode);
    }

    synchronized public void attemptRegistration(String code, GoofyAuthUser auth) throws InvalidRegisterCode, HandleAlreadyRegistered, RegistrationCodeAlreadyUsed {
        boolean regCodeRequired = true;
        CachedKeyHandleEntry entry = cachedKeyHandleRepository.findByHandle(auth.getHandle());
        if (entry != null && entry.getHandleDomain() != null)
            regCodeRequired = !registerProperties.getAllowedDomains().contains(entry.getHandleDomain());

        // Get Code if needed
        RegistrationCode regCode = getValidCode(code);
        if (regCodeRequired && regCode == null)
            throw new InvalidRegisterCode(code);

        // Check if Handle is already registered
        if (userRepository.findById(auth.getHandle()).isPresent())
            throw new HandleAlreadyRegistered(auth.getHandle());

        // Create User
        User user = new User();
        user.setHandle(auth.getHandle());
        user.setPubSplitKey(auth.getSignedRequest().pubSplitKey());
        user.setAdmin(regCode != null && regCode.getAdmin());
        userRepository.save(user);

        // Use Code if needed
        if (regCode != null)
            useCode(code, user);
        log.info("User {} registered successfully with code {}", user.getHandle(), code);
    }

    public void submitRegistrationRequest(RegistrationRequestDto requestDto, String handle) {
        log.info("Received Registration Request: {}", requestDto);
        RegistrationRequest request = new RegistrationRequest();
        request.setMessage(requestDto.getMessage());
        request.setGeneralContact(requestDto.getContact());
        request.setOptEmail(requestDto.getOptEmail());
        request.setCreatedAt(Instant.now());
        request.setCreatedByHandle(handle);
        registrationRequestRepository.save(request);
    }

    public List<RegistrationRequest> getAllRequests() {
        return registrationRequestRepository.findAll();
    }

    public List<RegistrationRequest> getAllUnresolvedRequests() {
        return registrationRequestRepository.findAllByResolvedAtIsNull();
    }

    public RegistrationRequest getRequestById(Long id) throws GenericNotFound {
        return registrationRequestRepository.findById(id).orElseThrow(() -> new GenericNotFound(id));
    }

    public void deleteRegistrationRequest(Long id) {
        registrationRequestRepository.deleteById(id);
    }

    public void setResolvedStatus(Long id, boolean resolved) throws GenericNotFound {
        RegistrationRequest req = registrationRequestRepository.findById(id)
                .orElseThrow(() -> new GenericNotFound(id));

        req.setResolvedAt(resolved ? Instant.now() : null);
        registrationRequestRepository.save(req);
    }
}
