package com.masl.goofy_irc_be.exception.base.swagger;

import io.swagger.v3.oas.annotations.Operation;

import java.lang.annotation.*;

// This should be added to all endpoints to allow proper swagger documentation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Operation
public @interface IrcEndpoint {
    String summary() default "";
    String description() default "";
}
