package com.masl.goofy_irc_be.dto.both;

import com.masl.goofy_irc_be.entity.FieldSize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDto {
    @NotNull
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Use only a-z, 0-9, and underscore (_)")
    private String name;

    @NotNull
    @Size(max = FieldSize.NORMAL_TEXT_LEN)
    private String description;

    private Integer userLimit;

    @NotNull
    private Boolean allowGuests;

    @NotNull
    private Boolean allowJoining;

    private String roomPasswordHash;

    private Integer memberCount;
    private List<String> members;
    private List<MemberStatusDto> memberStatus;

    private List<String> bannedUsers;

    private String createdByHandle;

    private Instant createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberStatusDto {
        private Boolean isOnline;
        private String typingInRoom;
    }
}
