package com.hedera.services.txns.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SummarizedExpiryMetaTest {
    @Test
    void ensuresMetaIsValidWhenExpected() {
        assertThrows(IllegalStateException.class, SummarizedExpiryMeta.INVALID_EXPIRY_SUMMARY::knownValidMeta);
    }

    @Test
    void returnsMetaIfValid() {
        final var expected = ExpiryMeta.withExplicitExpiry(1_234_567L);
        final var subject = SummarizedExpiryMeta.forValid(expected);
        final var actual = subject.knownValidMeta();
        assertSame(expected, actual);
    }
}