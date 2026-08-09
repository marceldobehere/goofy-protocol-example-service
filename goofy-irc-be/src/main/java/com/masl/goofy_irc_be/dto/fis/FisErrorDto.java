package com.masl.goofy_irc_be.dto.fis;

import com.masl.goofy_irc_be.exception.server.FisRequestError;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FisErrorDto {
    @NotNull
    public Integer httpCode;
    @NotNull
    public Integer errorCode;
    @NotNull
    public String message;
    public Map<String, Object> errorDetails;

    public void throwFisErr() throws FisRequestError {
        throw new FisRequestError(this.toString());
    }
}
