package com.masl.goofy_irc_be.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "goofy.register")
@Data
public class RegisterProperties {
    private Boolean registrationsAllowed;
    private String checkMethod;
}
