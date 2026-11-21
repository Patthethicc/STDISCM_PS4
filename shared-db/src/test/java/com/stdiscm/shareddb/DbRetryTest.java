package com.stdiscm.shareddb;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DbRetryTest {

    @Test
    void retriesOnceWhenTheConnectionBroke() {
        AtomicInteger calls = new AtomicInteger();
        String result = DbRetry.read(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new RuntimeException(new SQLException("terminating connection", "57P01"));
            }
            return "from replica";
        });
        assertEquals("from replica", result);
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryOtherErrors() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> DbRetry.read(() -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("bad input");
        }));
        assertEquals(1, calls.get());
    }

    @Test
    void givesUpAfterOneRetry() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(RuntimeException.class, () -> DbRetry.read(() -> {
            calls.incrementAndGet();
            throw new RuntimeException(new SQLException("refused", "08001"));
        }));
        assertEquals(2, calls.get());
    }
}
