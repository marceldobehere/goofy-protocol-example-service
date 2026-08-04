package com.masl.goofy_irc_be.dto.ws;

import com.masl.goofy_irc_be.entity.FieldSize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WsReceiveMsg extends WsGenericEv {
    public WsReceiveMsg() {
        super(EventType.RECEIVE_MSG);
    }

    @NotNull
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Use only a-z, 0-9, and underscore (_)")
    private String roomName;

    @NotNull
    private String senderHandle;

    @NotNull
    @Size(max = FieldSize.LONG_TEXT_LEN + FieldSize.SIGNATURE_LEN)
    private String msgObj;

    @NotNull
    @Size(max = FieldSize.SIGNATURE_LEN)
    private String sig;
}
