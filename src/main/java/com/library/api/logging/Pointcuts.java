package com.library.api.logging;

import org.aspectj.lang.annotation.Pointcut;

/**
 * Central place for the pointcut expressions the aspect weaves against.
 *
 * <p>Keeping them here rather than inline on the advice means the "what do we trace"
 * decision lives in one file, and the expressions can be composed by name instead of
 * copy-pasted. Nothing in here has advice attached - it is purely a set of named targets.
 */
public class Pointcuts {

    /** Any public method on one of *our* @RestControllers. Scoped to the app package so
     *  third-party controllers (e.g. springdoc's /v3/api-docs) are never instrumented. */
    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) && within(com.library.api..*)")
    public void controllerLayer() {
    }

    /** Any public method on one of our @Services. */
    @Pointcut("within(@org.springframework.stereotype.Service *) && within(com.library.api..*)")
    public void serviceLayer() {
    }

    /** Controller or service - the two layers worth tracing. Repositories are left to Hibernate's own SQL logging. */
    @Pointcut("controllerLayer() || serviceLayer()")
    public void application() {
    }
}
