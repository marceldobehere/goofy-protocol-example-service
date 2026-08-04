package com.masl.goofy_irc_be.service;

import com.masl.goofy_irc_be.integration.IsolatedHandleHelper;
import com.masl.goofy_protocol_core.crypto.connected.HandleCrypto;
import com.masl.goofy_protocol_core.crypto.connected.request.SignedRequest;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.GlobAsymmCrypto;
import com.masl.goofy_irc_be.auth.GoofyAuthUser;
import com.masl.goofy_irc_be.dto.request.RegistrationRequestDto;
import com.masl.goofy_irc_be.entity.User;
import com.masl.goofy_irc_be.exception.client.HandleAlreadyRegistered;
import com.masl.goofy_irc_be.exception.client.InvalidRegisterCode;
import com.masl.goofy_irc_be.IsolatedTestConfig;
import com.masl.goofy_irc_be.repository.UserRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("service")
@SpringBootTest
@ActiveProfiles({"test", "tests-shared"})
@ContextConfiguration(initializers = IsolatedTestConfig.class)
public class RegistrationTests {
    private static final String INVALID_CODE = "bruh 123";

    private final RegistrationService registrationService;
    private final UserRepository userRepository;

    private final GlobAsymmCrypto asymmCrypto = new GlobAsymmCrypto();
    private final HandleCrypto handleCrypto = new HandleCrypto(new IsolatedHandleHelper());

    @Autowired
    public RegistrationTests(RegistrationService registrationService, UserRepository userRepository) {
        this.registrationService = registrationService;
        this.userRepository = userRepository;
    }

    GoofyAuthUser createTestAuthUser() {
        var keypair = asymmCrypto.generateKeypair();
        String handle = handleCrypto.deriveHandle(keypair.pub().serialize());
        assertThat(keypair).isNotNull();
        SignedRequest req = Mockito.mock(SignedRequest.class);
        Mockito.when(req.handle()).thenReturn(handle);
        Mockito.when(req.pubSplitKey()).thenReturn(keypair.pub().serialize());

        return new GoofyAuthUser(handle, false, false, req);
    }

    @ParameterizedTest(name = "testRegistrationWithValidCode(isAdmin={0})")
    @ValueSource(booleans = {false, true})
    void testRegistrationWithValidCode(boolean isAdmin) throws Exception {
        var auth = createTestAuthUser();
        var code = registrationService.createNewRegistrationCode(isAdmin);
        assertThat(code).isNotNull();

        registrationService.attemptRegistration(code.getCode(), auth);

        User user = userRepository.findById(auth.getHandle()).orElse(null);
        assertThat(user).isNotNull();
        assertThat(user.isAdmin()).isEqualTo(isAdmin);
    }

    @Test
    void testRegistrationWithInvalidCode() {
        var auth = createTestAuthUser();
        assertThrows(InvalidRegisterCode.class, () -> registrationService.attemptRegistration(INVALID_CODE, auth));
    }

    @Test
    void testRegistrationWithValidCodeReused() throws Exception {
        var auth = createTestAuthUser();
        var code = registrationService.createNewRegistrationCode(false);

        registrationService.attemptRegistration(code.getCode(), auth);

        assertThrows(InvalidRegisterCode.class, () -> registrationService.attemptRegistration(code.getCode(), auth));
    }

    @Test
    void testRegistrationWithSameIdentity() throws Exception {
        var auth = createTestAuthUser();

        var code1 = registrationService.createNewRegistrationCode(false);
        registrationService.attemptRegistration(code1.getCode(), auth);

        User user1 = userRepository.findById(auth.getHandle()).orElse(null);
        assertThat(user1).isNotNull();
        assertThat(user1.isAdmin()).isEqualTo(false);

        var code2 = registrationService.createNewRegistrationCode(false);
        assertThrows(HandleAlreadyRegistered.class, () -> registrationService.attemptRegistration(code2.getCode(), auth));

        List<User> users = userRepository.findAll().stream().filter(u -> u.getHandle().equals(auth.getHandle())).toList();
        assertThat(users.size()).isEqualTo(1);
    }

    @Test
    void testSubmitRegistrationRequestNoMail() {
        String randomUUID = UUID.randomUUID().toString();

        RegistrationRequestDto requestDto = new RegistrationRequestDto();
        requestDto.setMessage("Please approve my registration.");
        requestDto.setContact(randomUUID);
        requestDto.setOptEmail(null);

        registrationService.submitRegistrationRequest(requestDto, null);
        var request = registrationService.getAllUnresolvedRequests()
                .stream().filter(r -> r.getGeneralContact().equals(randomUUID))
                .findFirst().orElse(null);
        assertThat(request).isNotNull();
        assertThat(request.getMessage()).isEqualTo(requestDto.getMessage());
        assertThat(request.getOptEmail()).isNull();
    }

    @ParameterizedTest(name = "testSubmitRegistrationRequest(setMail={0})")
    @ValueSource(booleans = {false, true})
    void testSubmitRegistrationRequest(boolean setMail) {
        String randomUUID = UUID.randomUUID().toString();

        RegistrationRequestDto requestDto = new RegistrationRequestDto();
        requestDto.setMessage("Please approve my registration.");
        requestDto.setContact(randomUUID);
        requestDto.setOptEmail(setMail ? "test@mail.com" : null);

        registrationService.submitRegistrationRequest(requestDto, null);
        var request = registrationService.getAllUnresolvedRequests()
                .stream().filter(r -> r.getGeneralContact().equals(randomUUID))
                .findFirst().orElse(null);
        assertThat(request).isNotNull();
        assertThat(request.getMessage()).isEqualTo(requestDto.getMessage());
        assertThat(request.getOptEmail()).isEqualTo(requestDto.getOptEmail());
    }
}
