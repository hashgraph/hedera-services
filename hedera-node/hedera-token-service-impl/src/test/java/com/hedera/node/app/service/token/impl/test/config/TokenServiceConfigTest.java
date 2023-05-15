package com.hedera.node.app.service.token.impl.test.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.service.token.impl.config.TokenServiceConfig;
import org.junit.jupiter.api.Test;

public class TokenServiceConfigTest {
    @Test
    void testGetter() {
        final var subject = new TokenServiceConfig(100);
        assertEquals(100, subject.maxCustomFeesAllowed());
    }
}
