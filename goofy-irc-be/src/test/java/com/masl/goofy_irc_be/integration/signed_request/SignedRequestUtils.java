package com.masl.goofy_irc_be.integration.signed_request;

import com.masl.goofy_protocol_core.crypto.connected.GenericHandleCrypto;
import com.masl.goofy_protocol_core.crypto.connected.request.SignedRequest;
import com.masl.goofy_protocol_core.crypto.isolated.asymm.AsymmCrypto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SignedRequestUtils {
    public static ResultActions performSignedRequest(HttpMethod method, String path, AsymmCrypto.AsymmFullKeyPair keypair, MockMvc mvc, GenericHandleCrypto handleCrypto) {
        return performSignedRequest(method, path, null, keypair, mvc, handleCrypto);
    }

    public static ResultActions performSignedRequestStr(HttpMethod method, String path, String body, AsymmCrypto.AsymmFullKeyPair keypair, MockMvc mvc, GenericHandleCrypto handleCrypto) {
        return performSignedRequest(method, path, body.getBytes(StandardCharsets.UTF_8), keypair, mvc, handleCrypto);
    }

    private static boolean isJSON(byte[] data) {
        if (data == null)
            return false;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.readTree(data);
            return true;
        } catch (JacksonException _) {
            return false;
        }
    }

    public static ResultActions performSignedRequest(HttpMethod method, String path, byte[] body, AsymmCrypto.AsymmFullKeyPair keypair, MockMvc mvc, GenericHandleCrypto handleCrypto) {
        try {
            // Create Signed Request
            SignedRequest req = SignedRequest.fromParts(keypair, method.name(), path, body, handleCrypto);

            // Get Headers as MultiValueMap
            Map<String, String> headers = req.toHeadersWithPubKey();
            MultiValueMap<String, String> multiHeaders = new LinkedMultiValueMap<>();
            headers.forEach(multiHeaders::add);

            // Check if byte[] is JSON
            boolean isJSON = isJSON(body);

            // Perform Request
            return mvc.perform(MockMvcRequestBuilders.request(method, path)
                    .headers(new HttpHeaders(multiHeaders))
                    .contentType(isJSON ? "application/json" : "text/plain")
                    .content(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ResultActions performUnsignedRequest(HttpMethod method, String path, MockMvc mvc) {
        return performUnsignedRequest(method, path, null, mvc);
    }

    public static ResultActions performUnsignedRequestStr(HttpMethod method, String path, String body, MockMvc mvc) {
        return performUnsignedRequest(method, path, body.getBytes(StandardCharsets.UTF_8), mvc);
    }

    public static ResultActions performUnsignedRequest(HttpMethod method, String path, byte[] body, MockMvc mvc) {
        try {
            // Check if byte[] is JSON
            boolean isJSON = isJSON(body);

            // Perform Request
            return mvc.perform(MockMvcRequestBuilders.request(method, path)
                    .contentType(isJSON ? "application/json" : "text/plain")
                    .content(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
