package com.library.api.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The aspect is mostly a logging side effect, so these tests pin down the two behaviours that
 * are easy to break and matter for correctness: the return value must pass through untouched,
 * and a thrown exception must propagate unchanged rather than being swallowed.
 */
@DisplayName("LoggingAspect")
class LoggingAspectTest {

    private LoggingAspect aspect;

    // A stand-in whose method signature the aspect will reflect over.
    @SuppressWarnings("unused")
    private String sampleMethod(String borrowerId, String password) {
        return "ok";
    }

    @BeforeEach
    void setUp() {
        aspect = new LoggingAspect();
    }

    private ProceedingJoinPoint joinPointFor(Object[] args) throws NoSuchMethodException {
        Method method = LoggingAspectTest.class.getDeclaredMethod("sampleMethod", String.class, String.class);

        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getName()).thenReturn(method.getName());
        when(signature.getDeclaringType()).thenReturn(LoggingAspectTest.class);

        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getSignature()).thenReturn(signature);
        when(jp.getArgs()).thenReturn(args);
        return jp;
    }

    @Test
    @DisplayName("returns the wrapped method's value unchanged")
    void passesReturnValueThrough() throws Throwable {
        ProceedingJoinPoint jp = joinPointFor(new Object[]{"7", "hunter2"});
        when(jp.proceed()).thenReturn("the-result");

        Object result = aspect.trace(jp);

        assertThat(result).isEqualTo("the-result");
    }

    @Test
    @DisplayName("re-throws the original exception without wrapping it")
    void propagatesException() throws Throwable {
        ProceedingJoinPoint jp = joinPointFor(new Object[]{"7", "hunter2"});
        IllegalStateException boom = new IllegalStateException("boom");
        when(jp.proceed()).thenThrow(boom);

        assertThatThrownBy(() -> aspect.trace(jp)).isSameAs(boom);
    }

    @Test
    @DisplayName("handles a no-arg call without error")
    void handlesNoArgs() throws Throwable {
        Method method = LoggingAspectTest.class.getDeclaredMethod("sampleMethod", String.class, String.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getName()).thenReturn("noArgs");
        when(signature.getDeclaringType()).thenReturn(LoggingAspectTest.class);

        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getSignature()).thenReturn(signature);
        when(jp.getArgs()).thenReturn(new Object[]{});
        when(jp.proceed()).thenReturn(null);

        assertThat(aspect.trace(jp)).isNull();
    }

    @Test
    @DisplayName("renders a byte[] without crashing (regression: ClassCastException)")
    void rendersByteArray() {
        // The original bug: describe() cast every array to Object[], but a byte[] (e.g.
        // springdoc's /v3/api-docs response) is not an Object[]. With DEBUG on, rendering the
        // return value threw ClassCastException and turned a 200 into a 500. byte[] is now
        // summarised rather than dumped, so a large binary payload also can't flood the log.
        String rendered = aspect.renderArray(new byte[]{1, 2, 3, 4});

        assertThat(rendered).isEqualTo("byte[4]");
    }

    @Test
    @DisplayName("renders other primitive and object arrays element-wise")
    void rendersOtherArrays() {
        assertThat(aspect.renderArray(new int[]{1, 2, 3})).isEqualTo("[1, 2, 3]");
        assertThat(aspect.renderArray(new String[]{"a", "b"})).isEqualTo("[a, b]");
        assertThat(aspect.renderArray("not an array")).isNull();
    }

    @Test
    @DisplayName("a byte[] return value passes through the aspect intact")
    void byteArrayReturnValuePassesThrough() throws Throwable {
        ProceedingJoinPoint jp = joinPointFor(new Object[]{"7", "hunter2"});
        byte[] payload = "{\"openapi\":\"3.0\"}".getBytes();
        when(jp.proceed()).thenReturn(payload);

        assertThat(aspect.trace(jp)).isSameAs(payload);
    }
}
