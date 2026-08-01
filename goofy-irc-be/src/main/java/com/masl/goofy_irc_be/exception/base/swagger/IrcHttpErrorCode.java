package com.masl.goofy_irc_be.exception.base.swagger;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// This should be added to all IrcExceptions to define the errorCode and or httpStatus
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface IrcHttpErrorCode {
    int httpStatus() default 0;
    int errorCode() default 0;
    String[] detailFields() default {};
    String description() default "";
}
