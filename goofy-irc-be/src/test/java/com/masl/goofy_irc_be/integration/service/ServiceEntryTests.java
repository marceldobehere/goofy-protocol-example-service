//package com.masl.goofy_irc_be.integration.service;
//
//import com.masl.goofy_protocol_core.crypto.connected.HandleCrypto;
//import com.masl.goofy_protocol_core.crypto.connected.IsolatedHandleHelper;
//import com.masl.goofy_protocol_core.crypto.isolated.asymm.AsymmCrypto;
//import com.masl.goofy_protocol_core.crypto.isolated.asymm.GlobAsymmCrypto;
//import com.masl.goofy_protocol_core.crypto.isolated.symm.GlobSymmCrypto;
//import com.masl.goofy_irc_be.IsolatedTestConfig;
//import com.masl.goofy_irc_be.dto.both.IdentityStorageEntryDto;
//import com.masl.goofy_irc_be.dto.both.ServiceEntryDto;
//import com.masl.goofy_irc_be.dto.response.MyServiceEntryQuotasDto;
//import com.masl.goofy_irc_be.properties.BaseQuotaProperties;
//import com.masl.goofy_irc_be.repository.IdentityStorageEntryRepository;
//import com.masl.goofy_irc_be.repository.ServiceEntryRepository;
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
//import java.util.UUID;
//
//import static com.masl.goofy_irc_be.integration.signed_request.SignedRequestUtils.*;
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@Tag("integration")
//@SpringBootTest
//@AutoConfigureMockMvc
//@ActiveProfiles({"test", "tests-service-entry"})
//@ContextConfiguration(initializers = IsolatedTestConfig.class)
//class ServiceEntryTests {
//	private static final String BASE = "/api/service-entry";
//	private static final String IDENTITY_BASE = "/api/identity-storage";
//
//	private final HandleCrypto handleCrypto = new HandleCrypto(new IsolatedHandleHelper());
//	private final GlobAsymmCrypto asymmCrypto = new GlobAsymmCrypto();
//	private final GlobSymmCrypto symmCrypto = new GlobSymmCrypto();
//
//	private final MockMvc mvc;
//	private final IdentityStorageEntryRepository identityRepository;
//	private final ServiceEntryRepository serviceEntryRepository;
//	private final BaseQuotaProperties baseQuotaProperties;
//	private final ObjectMapper objectMapper;
//	private final TestDataUser testDataUser;
//
//	private static final String encryptionPassword = "test password";
//
//	@Autowired
//	public ServiceEntryTests(MockMvc mvc, IdentityStorageEntryRepository identityRepository, ServiceEntryRepository serviceEntryRepository, BaseQuotaProperties baseQuotaProperties, ObjectMapper objectMapper, TestDataUser testDataUser) {
//		this.mvc = mvc;
//        this.identityRepository = identityRepository;
//        this.serviceEntryRepository = serviceEntryRepository;
//        this.baseQuotaProperties = baseQuotaProperties;
//        this.objectMapper = objectMapper;
//        this.testDataUser = testDataUser;
//    }
//
//	@BeforeEach
//	@Transactional
//	void prepServiceEntry() {
//		// keep deterministic for each test
//		serviceEntryRepository.deleteAll();
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
//
//		String encData = symmCrypto.encryptStr(identity.priv().serialize(), encryptionPassword);
//		dto.setEncKeypairEntry(encData);
//		dto.setEncKeypairEntrySignature(asymmCrypto.signStr(encData, identity.priv().serialize()));
//		return dto;
//	}
//
//	private void createIdentityForAuthRole(AsymmCrypto.AsymmFullKeyPair identity, AsymmCrypto.AsymmFullKeyPair user) throws Exception {
//		IdentityStorageEntryDto entry = createStorageDto(identity);
//		performSignedRequestStr(HttpMethod.POST, IDENTITY_BASE, objectMapper.writeValueAsString(entry), user, mvc, handleCrypto)
//				.andExpect(status().isOk());
//	}
//
//	private ServiceEntryDto createServiceEntryDto(String name, String usedService) {
//		ServiceEntryDto dto = new ServiceEntryDto();
//		dto.setName(name);
//		dto.setUsedService(usedService);
//		dto.setUuid(null);
//		return dto;
//	}
//
//	@Test
//	void testInsertAuthCheck() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		createIdentityForAuthRole(identity, testDataUser.testUser);
//
//		ServiceEntryDto entry = createServiceEntryDto("se_name", "svcA");
//		performUnsignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), mvc)
//				.andExpect(status().is4xxClientError());
//	}
//
//	@Test
//	void testInsertEntry() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		createIdentityForAuthRole(identity, testDataUser.testUser);
//
//		ServiceEntryDto entry = createServiceEntryDto("se_name_1", "svcA");
//
//		performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), identity, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		// Fetch list to discover the generated uuid
//		performSignedRequest(HttpMethod.GET, BASE, identity, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andExpect(result -> {
//					String responseBody = result.getResponse().getContentAsString();
//					ServiceEntryDto[] returned = objectMapper.readValue(responseBody, ServiceEntryDto[].class);
//
//					assertThat(returned).hasSize(1);
//					assertThat(returned[0].getName()).isEqualTo(entry.getName());
//					assertThat(returned[0].getUsedService()).isEqualTo(entry.getUsedService());
//					assertThat(returned[0].getUuid()).isNotNull();
//					assertThat(returned[0].getUuid()).isNotEmpty();
//				});
//	}
//
//	@Test
//	void testGetInvalidEntry() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		createIdentityForAuthRole(identity, testDataUser.testUser);
//
//		performSignedRequest(HttpMethod.GET, BASE + "/" + UUID.randomUUID(), testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().is4xxClientError());
//	}
//
//	@Test
//	void testDeleteEntry() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		createIdentityForAuthRole(identity, testDataUser.testUser);
//
//		ServiceEntryDto entry = createServiceEntryDto("se_name_del", "svcA");
//
//		String uuid = performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), identity, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andReturn().getResponse().getContentAsString();
//
//		performSignedRequest(HttpMethod.GET, BASE + "/" + uuid, identity, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		performSignedRequest(HttpMethod.DELETE, BASE + "/" + uuid, identity, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		performSignedRequest(HttpMethod.GET, BASE + "/" + uuid, identity, mvc, handleCrypto)
//				.andExpect(status().is4xxClientError());
//	}
//
//	@Test
//	void testUpdateEntry() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		createIdentityForAuthRole(identity, testDataUser.testUser);
//
//		ServiceEntryDto entry = createServiceEntryDto("se_name_upd_1", "svcA");
//
//		String uuid = performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(entry), identity, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andReturn().getResponse().getContentAsString();
//
//		// Update
//		ServiceEntryDto updateDto = new ServiceEntryDto();
//		updateDto.setUuid(uuid);
//		updateDto.setName("se_name_upd_2");
//		updateDto.setUsedService("svcB");
//
//		performSignedRequestStr(HttpMethod.PUT, BASE + "/" + uuid, objectMapper.writeValueAsString(updateDto), identity, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		// Verify
//		performSignedRequest(HttpMethod.GET, BASE + "/" + uuid, identity, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andExpect(result -> {
//					ServiceEntryDto returned = objectMapper.readValue(result.getResponse().getContentAsString(), ServiceEntryDto.class);
//					assertThat(returned.getUuid()).isEqualTo(uuid);
//					assertThat(returned.getName()).isEqualTo(updateDto.getName());
//					assertThat(returned.getUsedService()).isEqualTo(updateDto.getUsedService());
//				});
//	}
//
//	@Test
//	void testGetEntries() throws Exception {
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		createIdentityForAuthRole(identity, testDataUser.testUser);
//
//		List<String> uuids = new ArrayList<>();
//
//		int quota = baseQuotaProperties.getIdentity().getMaxServiceEntries();
//		for (int i = 0; i < quota; i++) {
//			ServiceEntryDto dto = createServiceEntryDto("se_name_" + i, "svc_" + i);
//
//			String uuid = performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(dto), identity, mvc, handleCrypto)
//					.andExpect(status().isOk())
//					.andReturn().getResponse().getContentAsString();
//			uuids.add(uuid);
//		}
//
//		performSignedRequest(HttpMethod.GET, BASE, identity, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andExpect(result -> {
//					ServiceEntryDto[] returned = objectMapper.readValue(result.getResponse().getContentAsString(), ServiceEntryDto[].class);
//					assertThat(returned.length).isEqualTo(quota);
//
//					for (ServiceEntryDto se : returned) {
//						assertThat(se.getUuid()).isNotNull();
//						assertThat(se.getUuid()).isNotEmpty();
//						assertThat(uuids).contains(se.getUuid());
//					}
//				});
//	}
//
//	@Test
//	void testQuotaLimit() throws Exception {
//		int quota = baseQuotaProperties.getIdentity().getMaxServiceEntries();
//
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		createIdentityForAuthRole(identity, testDataUser.testUser);
//
//		for (int i = 0; i < quota; i++) {
//			ServiceEntryDto dto = createServiceEntryDto("se_quota_" + i, "svcA_" + i);
//
//			performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(dto), identity, mvc, handleCrypto)
//					.andExpect(status().isOk());
//		}
//
//		// one more should fail
//		ServiceEntryDto overflow = createServiceEntryDto("se_overflow", "svcOverflow");
//		performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(overflow), identity, mvc, handleCrypto)
//				.andExpect(status().is4xxClientError());
//	}
//
//	@Test
//	void testQuota() throws Exception {
//		int quota = baseQuotaProperties.getIdentity().getMaxServiceEntries();
//
//		AsymmCrypto.AsymmFullKeyPair identity = asymmCrypto.generateKeypair();
//		createIdentityForAuthRole(identity, testDataUser.testUser);
//
//		performSignedRequest(HttpMethod.GET, BASE + "/quotas", identity, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andExpect(result -> {
//					MyServiceEntryQuotasDto returnedQuota =
//							objectMapper.readValue(result.getResponse().getContentAsString(), MyServiceEntryQuotasDto.class);
//					assertThat(returnedQuota.getMaxServiceEntryCount()).isEqualTo(quota);
//					assertThat(returnedQuota.getCurrentServiceEntryCount()).isEqualTo(0);
//				});
//
//		for (int i = 0; i < quota; i++) {
//			ServiceEntryDto dto = createServiceEntryDto("se_quota2_" + i, "svcA_" + i);
//			performSignedRequestStr(HttpMethod.POST, BASE, objectMapper.writeValueAsString(dto), identity, mvc, handleCrypto)
//					.andExpect(status().isOk());
//		}
//
//		performSignedRequest(HttpMethod.GET, BASE + "/quotas", identity, mvc, handleCrypto)
//				.andExpect(status().isOk())
//				.andExpect(result -> {
//					MyServiceEntryQuotasDto returnedQuota =
//							objectMapper.readValue(result.getResponse().getContentAsString(), MyServiceEntryQuotasDto.class);
//					assertThat(returnedQuota.getMaxServiceEntryCount()).isEqualTo(quota);
//					assertThat(returnedQuota.getCurrentServiceEntryCount()).isEqualTo(quota);
//				});
//	}
//
//	// TODO: Test Restricted User cannot insert, delete, or update entries
//}
