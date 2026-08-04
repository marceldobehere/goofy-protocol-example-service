package com.masl.goofy_irc_be.dto.ws;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WsError extends WsGenericEv {
    public WsError() {
        super(EventType.ERROR);
    }

    public WsError(String errMsg) {
        super(EventType.ERROR);
        this.errorMsg = errMsg;
    }

    @NotNull
    private String errorMsg;
}
