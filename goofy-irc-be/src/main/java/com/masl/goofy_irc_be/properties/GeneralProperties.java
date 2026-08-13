package com.masl.goofy_irc_be.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

// TODO: Add sensible default values or error early
@ConfigurationProperties(prefix = "goofy.general")
@Data
public class GeneralProperties {
    private String frontendUrl;
    private String url;
    private String name;
    private String description;
    private String contact;
    public final static String version = "0.7.1";

    public String getDomain() {
        URI uri = URI.create(url);
        String host = uri.getHost();

        // Return with/without Port
        int port = uri.getPort();
        return (port == -1) ? host : host + ":" + port;
    }

    public String getDomainHost() {
        URI uri = URI.create(url);
        return uri.getHost();
    }
}
