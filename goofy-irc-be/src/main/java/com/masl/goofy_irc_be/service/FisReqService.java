package com.masl.goofy_irc_be.service;

import com.masl.goofy_irc_be.crypto.HandleHelper;
import com.masl.goofy_irc_be.crypto.IrcHandleCrypto;
import com.masl.goofy_irc_be.dto.fis.FisErrorDto;
import com.masl.goofy_irc_be.exception.server.FisRequestError;
import com.masl.goofy_irc_be.exception.server.FisRequestValidationError;
import com.masl.goofy_irc_be.test_data.test_dev_prod.TestDataKeypair;
import com.masl.goofy_protocol_core.crypto.connected.HandleCrypto;
import com.masl.goofy_protocol_core.crypto.connected.request.SignedRequest;
import jakarta.validation.Validator;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
public class FisReqService {
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    private final TestDataKeypair keypairData;
    private final HandleCrypto handleCrypto;
    private final Validator validator;

    public FisReqService(TestDataKeypair keypairData, IrcHandleCrypto handleCrypto, Validator validator) {
        this.keypairData = keypairData;
        this.handleCrypto = handleCrypto;
        this.validator = validator;
    }

    public byte[] performFisRequest(HttpMethod method, String fisDomain, String path, byte[] body, Map<String, String> headers) {
        if (headers == null)
            headers = Map.of();

        MultiValueMap<String, String> multiHeaders = new LinkedMultiValueMap<>();
        headers.forEach(multiHeaders::add);

        for (var protocol : HandleHelper.supportedFisProtocols) {
            try {
                // TODO: Disable local IP Addresses / Domains / localhost if not in dev mode
                return restClient
                        .method(method)
                        .uri(protocol + fisDomain + path)
                        .headers(allHeaders -> allHeaders.putAll(multiHeaders))
                        .contentType(isJSON(body) ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN)
                        .body(body)
                        .retrieve()
                        .body(byte[].class);
            } catch (Exception _) {
                // System.err.println("Failed to perform request to FIS: " + method.name() + " " + protocol + fisDomain + path + "   -> REASON: " + e.getMessage());
            }
        }
        return null;
    }

    public <T, K> T performSignedRequest(Class<T> returnType, HttpMethod method, String fisDomain, String path, K body, Map<String, String> extraHeaders) throws FisRequestError, FisRequestValidationError {
        return performSignedRequest(returnType, method, fisDomain, path, mapper.writeValueAsBytes(body), extraHeaders);
    }

    public <T, K> T performSignedRequest(Class<T> returnType, HttpMethod method, String fisDomain, String path, K body) throws FisRequestError, FisRequestValidationError {
        return performSignedRequest(returnType, method, fisDomain, path, mapper.writeValueAsBytes(body), Map.of());
    }

    public <T> T performSignedRequest(Class<T> returnType, HttpMethod method, String fisDomain, String path, byte[] body, Map<String, String> extraHeaders) throws FisRequestError, FisRequestValidationError {
        if (extraHeaders == null)
            extraHeaders = Map.of();

        try {
            // Create Signed Request
            SignedRequest req = SignedRequest.fromParts(keypairData.getServerKeypair(), method.name(), path, body, handleCrypto);

            // Get Headers as MultiValueMap
            Map<String, String> headers = req.toHeadersWithPubKey();
            MultiValueMap<String, String> multiHeaders = new LinkedMultiValueMap<>();
            headers.forEach(multiHeaders::add);
            extraHeaders.forEach(multiHeaders::add);

            // Perform Request
            byte[] responseBytes = performFisRequest(method, fisDomain, path, body, multiHeaders.toSingleValueMap());
            if (responseBytes == null && returnType != void.class)
                throw new FisRequestError("Failed to perform request to FIS: " + method.name() + " " + fisDomain + path);

            // Attempt to parse error
            try {
                FisErrorDto errDto = mapper.readValue(responseBytes, FisErrorDto.class);
                if (validator.validate(errDto, FisErrorDto.class).isEmpty())
                    errDto.throwFisErr();
            } catch (FisRequestError e) {
                throw e;
            } catch (Exception _) {
            }

            // Check if return type is something like String, or byte[] and return it directly
            if (returnType == byte[].class)
                return returnType.cast(responseBytes);
            if (returnType == String.class)
                return returnType.cast(new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8));
            if (returnType == void.class)
                return null;

            // Parse Result
            T result = mapper.readValue(responseBytes, returnType);

            // Validate
            var violations = validator.validate(result);
            if (!violations.isEmpty())
                throw new FisRequestValidationError(violations.toString());

            // Return
            return result;
        } catch (FisRequestError e) {
          throw e;
        } catch (Exception e) {
            throw new FisRequestValidationError(e.getMessage());
        }
    }

    private boolean isJSON(byte[] data) {
        if (data == null)
            return false;

        try {
            mapper.readTree(data);
            return true;
        } catch (JacksonException _) {
            return false;
        }
    }
}
