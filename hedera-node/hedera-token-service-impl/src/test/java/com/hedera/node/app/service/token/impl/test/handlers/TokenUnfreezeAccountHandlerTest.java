package com.hedera.node.app.service.token.impl.test.handlers;

import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TokenUnfreezeAccountHandlerTest {

    private TokenUnfreezeAccountHandler subject;

    @BeforeEach
    void setUp() {
        subject = new TokenUnfreezeAccountHandler();
    }
    @Test
    void handleFunctionalityTest() {
        final var notImplemented = "Not implemented";
        try {
            subject.handle(null);
        } catch (final UnsupportedOperationException e) {
            assertEquals(e.getMessage(), notImplemented);
        }
    }
}
