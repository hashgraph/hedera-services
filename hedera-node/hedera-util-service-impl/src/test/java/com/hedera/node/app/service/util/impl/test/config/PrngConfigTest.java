package com.hedera.node.app.service.util.impl.test.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.util.impl.config.PrngConfig;
import org.junit.jupiter.api.Test;

public class PrngConfigTest {

    @Test
    void emptyConstructor() {
        assertFalse(new PrngConfig(false).isPrngEnabled());
        assertTrue(new PrngConfig(true).isPrngEnabled());
    }
}
