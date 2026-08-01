//package com.masl.goofy_irc_be.integration;
//
//import com.masl.goofy_protocol_core.crypto.connected.HandleCrypto;
//import com.masl.goofy_protocol_core.crypto.isolated.asymm.GlobAsymmCrypto;
//import com.masl.goofy_irc_be.IsolatedTestConfig;
//import com.masl.goofy_irc_be.dto.request.RegistrationRequestDto;
//import com.masl.goofy_irc_be.entity.User;
//import com.masl.goofy_irc_be.repository.UserRepository;
//import com.masl.goofy_irc_be.service.RegistrationService;
//import org.junit.jupiter.api.Tag;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
//import org.springframework.http.HttpMethod;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.ContextConfiguration;
//import org.springframework.test.web.servlet.MockMvc;
//import tools.jackson.databind.ObjectMapper;
//
//import java.util.UUID;
//
//import static com.masl.goofy_irc_be.integration.signed_request.SignedRequestUtils.*;
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@Tag("integration")
//@SpringBootTest
//@AutoConfigureMockMvc
//@ActiveProfiles({"test", "tests-shared"})
//@ContextConfiguration(initializers = IsolatedTestConfig.class)
//class RegistrationTests {
//	private static final String TEST_API_REGISTER = "/api/register";
//	private static final String TEST_API_STATUS = "/api/register/status";
//	private static final String TEST_API_REQUEST = "/api/register/request";
//
//	private final GlobAsymmCrypto crypto = new GlobAsymmCrypto();
//	private final HandleCrypto handleCrypto = new HandleCrypto(new IsolatedHandleHelper());
//
//	private final MockMvc mvc;
//	private final UserRepository userRepository;
//	private final RegistrationService registrationService;
//
//	@Autowired
//	public RegistrationTests(MockMvc mvc, UserRepository userRepository, RegistrationService registrationService) {
//		this.mvc = mvc;
//        this.userRepository = userRepository;
//        this.registrationService = registrationService;
//    }
//
//	@Test
//	void testStatus() throws Exception {
//		performUnsignedRequest(HttpMethod.GET, TEST_API_STATUS, mvc)
//				.andExpect(status().isOk());
//	}
//
//	@Test
//	void testRequest() throws Exception {
//		var keypair = crypto.generateKeypair();
//		String randomUUID = UUID.randomUUID().toString();
//
//		RegistrationRequestDto requestDto = new RegistrationRequestDto();
//		requestDto.setMessage("Please approve my registration.");
//		requestDto.setContact(randomUUID);
//		requestDto.setOptEmail("test@mail.com");
//
//		ObjectMapper objectMapper = new ObjectMapper();
//		performSignedRequestStr(HttpMethod.POST, TEST_API_REQUEST, objectMapper.writeValueAsString(requestDto), keypair, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		var request = registrationService.getAllUnresolvedRequests()
//				.stream().filter(r -> r.getGeneralContact().equals(randomUUID))
//				.findFirst().orElse(null);
//		assertThat(request).isNotNull();
//		assertThat(request.getMessage()).isEqualTo(requestDto.getMessage());
//		assertThat(request.getOptEmail()).isEqualTo(requestDto.getOptEmail());
//	}
//
//	@Test
//	void testRegisterValidCode() throws Exception {
//		var keypair = crypto.generateKeypair();
//		var code = registrationService.createNewRegistrationCode(false);
//		assertThat(code).isNotNull();
//
//		performSignedRequestStr(HttpMethod.POST, TEST_API_REGISTER, code.getCode(), keypair, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		User user = userRepository.findById(handleCrypto.deriveHandle(keypair.pub().serialize())).orElse(null);
//		assertThat(user).isNotNull();
//		assertThat(user.isAdmin()).isEqualTo(false);
//	}
//
//	@Test
//	void testRegisterInvalidCode() throws Exception {
//		var keypair = crypto.generateKeypair();
//		var code = registrationService.createNewRegistrationCode(false);
//		assertThat(code).isNotNull();
//
//		performSignedRequestStr(HttpMethod.POST, TEST_API_REGISTER, "bruh lol", keypair, mvc, handleCrypto)
//				.andExpect(status().is4xxClientError());
//
//		User user = userRepository.findById(handleCrypto.deriveHandle(keypair.pub().serialize())).orElse(null);
//		assertThat(user).isNull();
//	}
//
//	// TODO: Test isRegistrationCodeValid
//}
