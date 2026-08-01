package com.masl.goofy_irc_be.exception.base.swagger;

import com.masl.goofy_irc_be.exception.base.BaseClassIrcException;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// This makes the Swagger automatically document all possibly thrown (checked) Exceptions and documents them
@Component
public class IrcOpenApiCustomizer implements OperationCustomizer {
    private static final Logger log = LoggerFactory.getLogger(IrcOpenApiCustomizer.class);

    public IrcOpenApiCustomizer() {
        log.debug("Initializing Swagger Magic");
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // Get the Method and Check Annotations
        Method m = handlerMethod.getMethod();
        IrcEndpoint ircEndpoint = AnnotatedElementUtils.findMergedAnnotation(m, IrcEndpoint.class);
        if (ircEndpoint == null)
            return operation;

        log.debug(" > MATCH handler method: {}", handlerMethod);

        // Check for @PreAuthorize
        String authVal = "ANY";
        PreAuthorize preAuth = AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class);
        if (preAuth != null) {
            authVal = preAuth.value()
                    .replaceAll(Pattern.quote("hasRole('"), "")
                    .replaceAll(Pattern.quote("')"), "")
                    .replaceAll(Pattern.quote("ROLE_"), "");
            log.debug("  > Auth: {}", authVal);
        }

        // Set Summary, Description and Auth Tag
        operation.setSummary(ircEndpoint.summary());
        operation.setDescription("(Auth Roles: " + authVal + ")<br>" + ircEndpoint.description());
        operation.addTagsItem("[Auth: " + authVal + "]");

        // Get Responses
        ApiResponses responses = operation.getResponses();
        // log.debug(" > Existing Responses: {}", responses);

        // Check Exceptions
        log.debug(" > Exception Types: {}", Arrays.toString(m.getExceptionTypes()));
        for (Class<?> exType : m.getExceptionTypes()) {
            if (!BaseClassIrcException.class.isAssignableFrom(exType))
                continue;
            log.debug("  > MATCH Exception Type: {}", exType);

            // Get the Error Detail Fields
            String[] detailFieldsArr = BaseClassIrcException.detailFieldsFor(exType);
            Map<String, Object> detailFields = Arrays.stream(detailFieldsArr).collect(Collectors.toMap(f -> f, f -> "..."));

            // Use The IrcErrorDto as the Schema base, since it doesn't get used we have to explicitly force it
            Schema schema = ModelConverters.getInstance().read(IrcErrorDto.class).get("IrcErrorDto");
            schema.setExample(new IrcErrorDto(
                    BaseClassIrcException.errorCodeFor(exType),
                    "Error message for " + exType.getSimpleName(),
                    detailFields));

            // Create and Add the API Response
            String httpStatus = BaseClassIrcException.httpStatusFor(exType) + " / " + BaseClassIrcException.errorCodeFor(exType);
            String descStr = BaseClassIrcException.descriptionFor(exType); descStr = descStr.isEmpty() ? "" : ":<br>" + descStr;
            ApiResponse response = new ApiResponse()
                    .description(exType.getSimpleName().replaceAll("([a-z0-9])([A-Z])", "$1 $2") + descStr)
                    .content(new Content().addMediaType(
                            "application/json",
                            new MediaType().schema(schema)
                    ));
            responses.addApiResponse(httpStatus, response);
        }

        return operation;
    }
}
