package com.masl.goofy_irc_be.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

// TODO: Add sensible default values or error early
@ConfigurationProperties(prefix = "goofy.room")
@Data
public class RoomProperties {
    private Integer maxRoomsPerUser;
    private Integer defaultMaxUserLimit;
}
