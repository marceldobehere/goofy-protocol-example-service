package com.masl.goofy_irc_be.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "goofy.general")
@Data
public class GeneralProperties {
    private String frontendUrl;
    private String url;
    private String name;
    private String description;
    private String contact;
    public final static String version = "0.4.1";

    public String getDomain() {
        URI uri = URI.create(url);
        String host = uri.getHost();

        // Return with/without Port
        int port = uri.getPort();
        return (port == -1) ? host : host + ":" + port;
    }
}
