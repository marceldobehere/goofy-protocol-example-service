//package com.masl.goofy_irc_be.integration.storage;
//
//import com.masl.goofy_irc_be.integration.IsolatedHandleHelper;
//import com.masl.goofy_protocol_core.crypto.connected.HandleCrypto;
//import com.masl.goofy_protocol_core.crypto.isolated.asymm.AsymmCrypto;
//import com.masl.goofy_protocol_core.crypto.isolated.asymm.GlobAsymmCrypto;
//import com.masl.goofy_protocol_core.crypto.isolated.symm.GlobSymmCrypto;
//import com.masl.goofy_irc_be.IsolatedTestConfig;
//import com.masl.goofy_irc_be.dto.both.IdentityStorageEntryDto;
//import com.masl.goofy_irc_be.dto.response.MyIdentityEntryQuotasDto;
//import com.masl.goofy_irc_be.properties.BaseQuotaProperties;
//import com.masl.goofy_irc_be.repository.IdentityStorageEntryRepository;
//import com.masl.goofy_irc_be.test_data.test_only.TestDataUser;
//import jakarta.transaction.Transactional;
//import org.junit.jupiter.api.BeforeEach;
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
//import java.util.ArrayList;
//import java.util.List;
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
//class IdentityStorageTests {
//	private static final String BASE = "/api/identity-storage";
//
//	private static final String encryptionPassword = "test password";
//
//
//	private final HandleCrypto handleCrypto = new HandleCrypto(new IsolatedHandleHelper());
//	private final GlobAsymmCrypto asymmCrypto = new GlobAsymmCrypto();
//	private final GlobSymmCrypto symmCrypto = new GlobSymmCrypto();
//
//	private final MockMvc mvc;
//	private final IdentityStorageEntryRepository identityRepository;
//	private final BaseQuotaProperties baseQuotaProperties;
//	private final ObjectMapper objectMapper;
//	private final TestDataUser testDataUser;
//
//	@Autowired
//	public IdentityStorageTests(MockMvc mvc, IdentityStorageEntryRepository identityRepository, BaseQuotaProperties baseQuotaProperties, ObjectMapper objectMapper, TestDataUser testDataUser) {
//		this.mvc = mvc;
//        this.identityRepository = identityRepository;
//        this.baseQuotaProperties = baseQuotaProperties;
//        this.objectMapper = objectMapper;
//        this.testDataUser = testDataUser;
//    }
//
//	@BeforeEach
//	@Transactional
//	void prepIdentityStorage() {
//		identityRepository.deleteAll();
//	}
//
//	private IdentityStorageEntryDto createStorageDto(AsymmCrypto.AsymmFullKeyPair identity) {
//		String identityHandle = handleCrypto.deriveHandle(identity.pub().serialize());
//
//		IdentityStorageEntryDto dto = new IdentityStorageEntryDto();
//		dto.setName("name_" + identityHandle);
//		dto.setHandle(identityHandle);
//		dto.setPubSplitKey(identity.pub().serialize());
//		String encData = symmCrypto.encryptStr(identity.priv().serialize(), encryptionPassword);
//		dto.setEncKeypairEntry(encData);
//		dto.setEncKeypairEntrySignature(asymmCrypto.signStr(encData, identity.priv().serialize()));
//		return dto;
//	}
//
//	@Test
//	void testInsertAuthCheck() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		IdentityStorageEntryDto entry = createStorageDto(identity);
//		performUnsignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), mvc)
//				.andExpect(status().is4xxClientError());
//	}
//
//	@Test
//	void testInsertEntry() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		IdentityStorageEntryDto entry = createStorageDto(identity);
//		String identityHandle = handleCrypto.deriveHandle(identity.pub().serialize());
//
//		performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		performSignedRequest(HttpMethod.GET, BASE + "/" + identityHandle, testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andExpect(result -> {
//					String responseBody = result.getResponse().getContentAsString();
//					IdentityStorageEntryDto returnedEntry = objectMapper.readValue(responseBody, IdentityStorageEntryDto.class);
//					assertThat(returnedEntry.getHandle()).isEqualTo(identityHandle);
//					assertThat(returnedEntry.getName()).isEqualTo(entry.getName());
//					assertThat(returnedEntry.getPubSplitKey()).isEqualTo(entry.getPubSplitKey());
//					assertThat(returnedEntry.getEncKeypairEntry()).isEqualTo(entry.getEncKeypairEntry());
//					assertThat(returnedEntry.getEncKeypairEntrySignature()).isEqualTo(entry.getEncKeypairEntrySignature());
//				});
//	}
//
//	@Test
//	void testGetInvalidEntry() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		String identityHandle = handleCrypto.deriveHandle(identity.pub().serialize());
//
//		performSignedRequest(HttpMethod.GET, BASE + "/" + identityHandle, testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().is4xxClientError());
//	}
//
//	@Test
//	void testDeleteEntry() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		IdentityStorageEntryDto entry = createStorageDto(identity);
//		String identityHandle = handleCrypto.deriveHandle(identity.pub().serialize());
//
//		performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		performSignedRequest(HttpMethod.GET, BASE + "/" + identityHandle, testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		performSignedRequest(HttpMethod.DELETE, BASE + "/" + identityHandle, testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		performSignedRequest(HttpMethod.GET, BASE + "/" + identityHandle, testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().is4xxClientError());
//	}
//
//	@Test
//	void testUpdateEntry() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		IdentityStorageEntryDto entry = createStorageDto(identity);
//		String identityHandle = handleCrypto.deriveHandle(identity.pub().serialize());
//
//		performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		performSignedRequest(HttpMethod.GET, BASE + "/" + identityHandle, testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andExpect(result -> {
//					String responseBody = result.getResponse().getContentAsString();
//					IdentityStorageEntryDto returnedEntry = objectMapper.readValue(responseBody, IdentityStorageEntryDto.class);
//					assertThat(returnedEntry.getHandle()).isEqualTo(identityHandle);
//					assertThat(returnedEntry.getName()).isEqualTo(entry.getName());
//					assertThat(returnedEntry.getPubSplitKey()).isEqualTo(entry.getPubSplitKey());
//					assertThat(returnedEntry.getEncKeypairEntry()).isEqualTo(entry.getEncKeypairEntry());
//					assertThat(returnedEntry.getEncKeypairEntrySignature()).isEqualTo(entry.getEncKeypairEntrySignature());
//				});
//
//		String encData = symmCrypto.encryptStr("GNOM", encryptionPassword);
//		entry.setEncKeypairEntry(encData);
//		entry.setEncKeypairEntrySignature(asymmCrypto.signStr(encData, identity.priv().serialize()));
//
//		performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		performSignedRequest(HttpMethod.GET, BASE + "/" + identityHandle, testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andExpect(result -> {
//					String responseBody = result.getResponse().getContentAsString();
//					IdentityStorageEntryDto returnedEntry = objectMapper.readValue(responseBody, IdentityStorageEntryDto.class);
//					assertThat(returnedEntry.getHandle()).isEqualTo(identityHandle);
//					assertThat(returnedEntry.getName()).isEqualTo(entry.getName());
//					assertThat(returnedEntry.getPubSplitKey()).isEqualTo(entry.getPubSplitKey());
//					assertThat(returnedEntry.getEncKeypairEntry()).isEqualTo(entry.getEncKeypairEntry());
//					assertThat(returnedEntry.getEncKeypairEntrySignature()).isEqualTo(entry.getEncKeypairEntrySignature());
//				});
//	}
//
//	@Test
//	void testQuotaLimit() throws Exception {
//		int quota = baseQuotaProperties.getIdentity().getMaxEntries();
//
//		for (int i = 0; i < quota; i++) {
//			AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//			IdentityStorageEntryDto entry = createStorageDto(identity);
//			String identityHandle = handleCrypto.deriveHandle(identity.pub().serialize());
//
//			performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), testDataUser.testUser, mvc, handleCrypto)
//					.andExpect(status().isOk());
//
//			performSignedRequest(HttpMethod.GET, BASE + "/" + identityHandle, testDataUser.testUser, mvc, handleCrypto)
//					.andExpect(status().isOk());
//		}
//
//		{
//			AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//			IdentityStorageEntryDto entry = createStorageDto(identity);
//
//			performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), testDataUser.testUser, mvc, handleCrypto)
//					.andExpect(status().is4xxClientError());
//		}
//	}
//
//	@Test
//	void testGetEntries() throws Exception {
//		int quota = baseQuotaProperties.getIdentity().getMaxEntries();
//
//		List<String> handles = new ArrayList<>();
//
//		for (int i = 0; i < quota; i++) {
//			AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//			IdentityStorageEntryDto entry = createStorageDto(identity);
//			String identityHandle = handleCrypto.deriveHandle(identity.pub().serialize());
//
//			performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), testDataUser.testUser, mvc, handleCrypto)
//					.andExpect(status().isOk());
//
//			performSignedRequest(HttpMethod.GET, BASE + "/" + identityHandle, testDataUser.testUser, mvc, handleCrypto)
//					.andExpect(status().isOk());
//
//			handles.add(identityHandle);
//		}
//
//		performSignedRequest(HttpMethod.GET, BASE, testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andExpect(result -> {
//					String responseBody = result.getResponse().getContentAsString();
//					IdentityStorageEntryDto[] returnedEntries = objectMapper.readValue(responseBody, IdentityStorageEntryDto[].class);
//					assertThat(returnedEntries.length).isEqualTo(handles.size());
//					for (IdentityStorageEntryDto returnedEntry : returnedEntries)
//						assertThat(handles).contains(returnedEntry.getHandle());
//				});
//	}
//
//	@Test
//	void testQuota() throws Exception {
//		int quota = baseQuotaProperties.getIdentity().getMaxEntries();
//
//		{
//			performSignedRequest(HttpMethod.GET, BASE + "/quotas", testDataUser.testUser, mvc, handleCrypto)
//					.andExpect(status().isOk())
//					.andExpect(result -> {
//						String responseBody = result.getResponse().getContentAsString();
//						MyIdentityEntryQuotasDto returnedQuota = objectMapper.readValue(responseBody, MyIdentityEntryQuotasDto.class);
//						assertThat(returnedQuota.getMaxEntryCount()).isEqualTo(quota);
//						assertThat(returnedQuota.getCurrentEntryCount()).isEqualTo(0);
//					});
//		}
//
//		for (int i = 0; i < quota; i++) {
//			AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//			IdentityStorageEntryDto entry = createStorageDto(identity);
//			String identityHandle = handleCrypto.deriveHandle(identity.pub().serialize());
//
//			performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), testDataUser.testUser, mvc, handleCrypto)
//					.andExpect(status().isOk());
//
//			performSignedRequest(HttpMethod.GET, BASE + "/" + identityHandle, testDataUser.testUser, mvc, handleCrypto)
//					.andExpect(status().isOk());
//		}
//
//		{
//			performSignedRequest(HttpMethod.GET, BASE + "/quotas", testDataUser.testUser, mvc, handleCrypto)
//					.andExpect(status().isOk())
//					.andExpect(result -> {
//						String responseBody = result.getResponse().getContentAsString();
//						MyIdentityEntryQuotasDto returnedQuota = objectMapper.readValue(responseBody, MyIdentityEntryQuotasDto.class);
//						assertThat(returnedQuota.getMaxEntryCount()).isEqualTo(quota);
//						assertThat(returnedQuota.getCurrentEntryCount()).isEqualTo(quota);
//					});
//		}
//	}
//
//	// TODO: Test Restricted User cannot insert, delete, or update entries
//
//	// TODO: Tests for Public Data
//}
