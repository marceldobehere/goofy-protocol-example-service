//package com.masl.goofy_irc_be.integration;
//
//import com.masl.goofy_protocol_core.crypto.connected.HandleCrypto;
//import com.masl.goofy_irc_be.IsolatedTestConfig;
//import com.masl.goofy_irc_be.dto.request.GeneralReportDto;
//import com.masl.goofy_irc_be.service.GeneralReportService;
//import com.masl.goofy_irc_be.test_data.test_only.TestDataUser;
//import org.assertj.core.api.Assertions;
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
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@Tag("integration")
//@SpringBootTest
//@AutoConfigureMockMvc
//@ActiveProfiles({"test", "tests-shared"})
//@ContextConfiguration(initializers = IsolatedTestConfig.class)
//class GeneralTests {
//	private static final String TEST_API_STATUS = "/api/general/status";
//	private static final String TEST_API_INFO = "/api/general/info";
//	private static final String TEST_API_CONTACT = "/api/general/contact";
//	private static final String TEST_API_REPORT = "/api/general/report";
//
//	private final HandleCrypto handleCrypto = new HandleCrypto(new IsolatedHandleHelper());
//
//	private final MockMvc mvc;
//	private final TestDataUser testDataUser;
//	private final GeneralReportService reportService;
//
//	@Autowired
//	public GeneralTests(MockMvc mvc, TestDataUser testDataUser, GeneralReportService reportService) {
//		this.mvc = mvc;
//        this.testDataUser = testDataUser;
//        this.reportService = reportService;
//    }
//
//	@Test
//	void testStatus() throws Exception {
//		performUnsignedRequest(HttpMethod.GET, TEST_API_STATUS, mvc)
//				.andExpect(status().isOk());
//	}
//
//	@Test
//	void testInfo() throws Exception {
//		performUnsignedRequest(HttpMethod.GET, TEST_API_INFO, mvc)
//				.andExpect(status().isOk());
//	}
//
//	@Test
//	void testContact() throws Exception {
//		performUnsignedRequest(HttpMethod.GET, TEST_API_CONTACT, mvc)
//				.andExpect(status().isOk());
//	}
//
//	@Test
//	void testReportAsGuest() throws Exception {
//		String randomUUID = UUID.randomUUID().toString();
//		GeneralReportDto reportDto = new GeneralReportDto();
//		reportDto.setTitle("Test Report");
//		reportDto.setDescription("This is a test report.");
//		reportDto.setContact(randomUUID);
//
//		ObjectMapper objectMapper = new ObjectMapper();
//		String requestBody = objectMapper.writeValueAsString(reportDto);
//		performUnsignedRequestStr(HttpMethod.POST, TEST_API_REPORT, requestBody, mvc)
//				.andExpect(status().isOk());
//
//		var report = reportService.getAllUnresolvedReports()
//				.stream().filter(r -> r.getContact().equals(randomUUID))
//				.findFirst().orElse(null);
//		Assertions.assertThat(report).isNotNull();
//		Assertions.assertThat(report.getTitle()).isEqualTo(reportDto.getTitle());
//		Assertions.assertThat(report.getDescription()).isEqualTo(reportDto.getDescription());
//		Assertions.assertThat(report.getOptionalHandle()).isNull();
//	}
//
//	@Test
//	void testReportAsUser() throws Exception {
//		String randomUUID = UUID.randomUUID().toString();
//		GeneralReportDto reportDto = new GeneralReportDto();
//		reportDto.setTitle("Test Report");
//		reportDto.setDescription("This is a test report.");
//		reportDto.setContact(randomUUID);
//		String handle = handleCrypto.deriveHandle(testDataUser.testUser.pub().serialize());
//
//		ObjectMapper objectMapper = new ObjectMapper();
//		String requestBody = objectMapper.writeValueAsString(reportDto);
//		performSignedRequestStr(HttpMethod.POST, TEST_API_REPORT, requestBody, testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isOk());
//
//		var report = reportService.getAllUnresolvedReports()
//				.stream().filter(r -> r.getContact().equals(randomUUID))
//				.findFirst().orElse(null);
//		Assertions.assertThat(report).isNotNull();
//		Assertions.assertThat(report.getTitle()).isEqualTo(reportDto.getTitle());
//		Assertions.assertThat(report.getDescription()).isEqualTo(reportDto.getDescription());
//		Assertions.assertThat(report.getOptionalHandle()).isEqualTo(handle);
//	}
//
//	@Test
//	void testReportAsUserInvalidBody() throws Exception {
//		String requestBody = "{\"bla\": \"bla bla\"}";
//		performSignedRequestStr(HttpMethod.POST, TEST_API_REPORT, requestBody, testDataUser.testUser, mvc, handleCrypto)
//				.andExpect(status().isBadRequest());
//	}
//}
