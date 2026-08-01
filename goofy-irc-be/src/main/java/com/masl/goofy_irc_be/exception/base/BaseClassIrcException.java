package com.masl.goofy_irc_be.exception.base;

import com.masl.goofy_irc_be.exception.base.swagger.IrcHttpErrorCode;

import java.util.Map;

public class BaseClassIrcException extends Exception {
    public final int httpCode;
    public final int errorCode;
    public final String message;
    public final Map<String, Object> errorDetails;

    public BaseClassIrcException(int httpCode, int errorCode, String message, Map<String, Object> errorDetails) {
        super(message);
        this.httpCode = httpCode;
        this.errorCode = errorCode;
        this.message = message;
        this.errorDetails = errorDetails;
    }

    public BaseClassIrcException(int httpCode, int errorCode, String message) {
        this(httpCode, errorCode, message, Map.of());
    }

    public BaseClassIrcException(String message, Map<String, Object> errorDetails) {
        super(message);
        this.httpCode = httpStatusFor(getClass());
        this.errorCode = errorCodeFor(getClass());
        this.message = message;
        this.errorDetails = errorDetails;
    }

    public BaseClassIrcException(String message) {
        this(message, Map.of());
    }


    // ---- Magic Helper Methods for Annotation ----

    public static int httpStatusFor(Class<?> _class) {
        // Check Base Class
        IrcHttpErrorCode ann = _class.getAnnotation(IrcHttpErrorCode.class);
        if (ann == null)
            throw new IllegalArgumentException("Class " + _class.getName() + " is not annotated with @IrcHttpErrorCode");
        if (ann.httpStatus() != 0)
            return ann.httpStatus();

        // Check Super Class
        ann = _class.getSuperclass().getAnnotation(IrcHttpErrorCode.class);
        if (ann == null)
            throw new IllegalArgumentException("Class " + _class.getName() + " is not annotated with @IrcHttpErrorCode");
        if (ann.httpStatus() != 0)
            return ann.httpStatus();

        throw new IllegalArgumentException("Class " + _class.getName() + " is not annotated with @IrcHttpErrorCode");
    }

    public static int errorCodeFor(Class<?> _class) {
        // Check Base Class
        IrcHttpErrorCode ann = _class.getAnnotation(IrcHttpErrorCode.class);
        if (ann == null)
            throw new IllegalArgumentException("Class " + _class.getName() + " is not annotated with @IrcHttpErrorCode");
        if (ann.errorCode() != 0)
            return ann.errorCode();

        // Check Super Class
        ann = _class.getSuperclass().getAnnotation(IrcHttpErrorCode.class);
        if (ann == null)
            throw new IllegalArgumentException("Class " + _class.getName() + " is not annotated with @IrcHttpErrorCode");
        if (ann.errorCode() != 0)
            return ann.errorCode();

        throw new IllegalArgumentException("Class " + _class.getName() + " is not annotated with @IrcHttpErrorCode");
    }

    public static String[] detailFieldsFor(Class<?> _class) {
        // Check Base Class
        IrcHttpErrorCode ann = _class.getAnnotation(IrcHttpErrorCode.class);
        if (ann == null)
            throw new IllegalArgumentException("Class " + _class.getName() + " is not annotated with @IrcHttpErrorCode");
        return ann.detailFields();
    }

    public static String descriptionFor(Class<?> _class) {
        // Check Base Class
        IrcHttpErrorCode ann = _class.getAnnotation(IrcHttpErrorCode.class);
        if (ann == null)
            throw new IllegalArgumentException("Class " + _class.getName() + " is not annotated with @IrcHttpErrorCode");
        return ann.description();
    }
}
