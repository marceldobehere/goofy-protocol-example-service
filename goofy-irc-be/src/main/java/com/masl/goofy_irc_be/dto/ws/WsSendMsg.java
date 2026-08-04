package com.masl.goofy_irc_be.dto.ws;

import com.masl.goofy_irc_be.entity.FieldSize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WsSendMsg extends WsGenericEv {
    public WsSendMsg() {
        super(EventType.SEND_MSG);
    }

    @NotNull
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Use only a-z, 0-9, and underscore (_)")
    private String roomName;

    @NotNull
    @Size(max = FieldSize.LONG_TEXT_LEN + FieldSize.SIGNATURE_LEN)
    private String msgObj;
}
