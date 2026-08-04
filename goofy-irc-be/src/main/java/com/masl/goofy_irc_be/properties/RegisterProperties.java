package com.masl.goofy_irc_be.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.regex.Pattern;

// TODO: Add sensible default values or error early
@ConfigurationProperties(prefix = "goofy.register")
@Data
public class RegisterProperties {
    private Boolean registrationsAllowed;
    private String checkMethod;
    private String autoAllowDomains; // If a handle is from this domain, it does not need a register code

    public List<String> getAllowedDomains() {
        if (autoAllowDomains == null || autoAllowDomains.isEmpty()) {
            return List.of();
        }
        return List.of(autoAllowDomains.split(Pattern.quote(",")));
    }
}
