package com.masl.goofy_irc_be.dto.fis.table;

import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TWherePart {
    private Type type;

    // RAW VALUE
    private Object value;
    private TableColumnDto.Type valueType;

    // COLUMN
    private String colName;

    // CONDITION
    private TWherePart[] conditionParts;


    @Getter
    public enum Type {
        VAL(InputCount.NONE, ExpressionType.NONE, ExpressionType.ANY_VALUE),
        COL(InputCount.NONE, ExpressionType.NONE, ExpressionType.ANY_VALUE),

        M_ADD(InputCount.TWO, ExpressionType.ANY_NUMBER, ExpressionType.ANY_NUMBER),
        M_SUB(InputCount.TWO, ExpressionType.ANY_NUMBER, ExpressionType.ANY_NUMBER),
        M_MUL(InputCount.TWO, ExpressionType.ANY_NUMBER, ExpressionType.ANY_NUMBER),
        M_DIV(InputCount.TWO, ExpressionType.ANY_NUMBER, ExpressionType.ANY_NUMBER),
        M_MOD(InputCount.TWO, ExpressionType.ANY_NUMBER, ExpressionType.ANY_NUMBER),

        M_FLOOR(InputCount.ONE, ExpressionType.ANY_NUMBER, ExpressionType.ANY_NUMBER),
        M_CEIL(InputCount.ONE, ExpressionType.ANY_NUMBER, ExpressionType.ANY_NUMBER),
        M_ABS(InputCount.ONE, ExpressionType.ANY_NUMBER, ExpressionType.ANY_NUMBER),

        L_AND(InputCount.MANY, ExpressionType.ANY_BOOLEAN, ExpressionType.ANY_BOOLEAN),
        L_OR(InputCount.MANY, ExpressionType.ANY_BOOLEAN, ExpressionType.ANY_BOOLEAN),
        L_NOT(InputCount.ONE, ExpressionType.ANY_BOOLEAN, ExpressionType.ANY_BOOLEAN),

        C_EQ(InputCount.TWO, ExpressionType.ANY, ExpressionType.ANY_BOOLEAN),
        C_NEQ(InputCount.TWO, ExpressionType.ANY, ExpressionType.ANY_BOOLEAN),
        C_GT(InputCount.TWO, ExpressionType.ANY_NUMBER, ExpressionType.ANY_BOOLEAN),
        C_GE(InputCount.TWO, ExpressionType.ANY_NUMBER, ExpressionType.ANY_BOOLEAN),
        C_LT(InputCount.TWO, ExpressionType.ANY_NUMBER, ExpressionType.ANY_BOOLEAN),
        C_LE(InputCount.TWO, ExpressionType.ANY_NUMBER, ExpressionType.ANY_BOOLEAN),

        COALESCE(InputCount.TWO, ExpressionType.ANY, ExpressionType.ANY_VALUE),

        LIKE(InputCount.TWO, ExpressionType.ANY_VALUE, ExpressionType.ANY_VALUE);

        private final InputCount inputCount;
        private final ExpressionType inputTypes;
        private final ExpressionType outputType;

        Type(InputCount inputCount, ExpressionType inputTypes, ExpressionType outputType) {
            this.inputCount = inputCount;
            this.inputTypes = inputTypes;
            this.outputType = outputType;
        }

        public enum InputCount {
            NONE,
            ONE,
            TWO,
            MANY
        }

        public enum ExpressionType {
            NONE,
            ANY_VALUE,
            ANY_NUMBER, // Should also accept ANY_VALUE
            ANY_BOOLEAN, // Should also accept ANY_VALUE
            ANY
        }
    }

    public static TWherePart eq(TWherePart left, TWherePart right) {
        return builder().type(Type.C_EQ).conditionParts(new TWherePart[]{left, right}).build();
    }

    public static TWherePart val(Object value, TableColumnDto.Type valueType) {
        return builder().type(Type.VAL).value(value).valueType(valueType).build();
    }

    public static TWherePart valStr(String value) {
        return builder().type(Type.VAL).value(value).valueType(TableColumnDto.Type.VAR_STRING_N).build();
    }

    public static TWherePart valInt(int value) {
        return builder().type(Type.VAL).value(value).valueType(TableColumnDto.Type.INT).build();
    }

    public static TWherePart valLong(long value) {
        return builder().type(Type.VAL).value(value).valueType(TableColumnDto.Type.BIGINT).build();
    }

    public static TWherePart col(String colName) {
        return builder().type(Type.COL).colName(colName).build();
    }
}
