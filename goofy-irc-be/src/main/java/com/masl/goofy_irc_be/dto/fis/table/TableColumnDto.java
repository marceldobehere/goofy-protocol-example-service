package com.masl.goofy_irc_be.dto.fis.table;

import com.masl.goofy_irc_be.entity.FieldSize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TableColumnDto {
    @NotBlank
    @Size(max = FieldSize.SHORT_TEXT_LEN)
    @Pattern(regexp = "^[a-z0-9_]+$", message = "Use only a-z, 0-9, and underscore (_)")
    private String colName;

    @NotNull
    private Type type;

    private Integer typeSize;

    @NotNull
    private Set<Constraint> constraints;

    private Object defaultValue;


    public enum Type {
        FIXED_STRING_N, VAR_STRING_N, // (N, 1-MAX_FIELD_SIZE)
        BOOLEAN,
        TINYINT, SMALLINT, INT, BIGINT, // NOTE: These are signed!
        FLOAT, DOUBLE,
        DATE, TIME
    }

    public enum Constraint {
        NOT_NULL,
        UNIQUE,
        PRIMARY_KEY
    }
}
