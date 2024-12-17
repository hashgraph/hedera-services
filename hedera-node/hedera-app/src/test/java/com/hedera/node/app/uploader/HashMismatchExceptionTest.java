package com.hedera.node.app.uploader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HashMismatchExceptionTest {

    @Test
    void testExceptionMessage() {
        String objectKey = "block123";
        String provider = "AWS";
        HashMismatchException exception = new HashMismatchException(objectKey, provider);
        String expectedMessage = "Hash mismatch for block block123 in provider AWS";
        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void testExceptionIsRuntimeException() {
        String objectKey = "block456";
        String provider = "GCS";
        HashMismatchException exception = new HashMismatchException(objectKey, provider);
        assertInstanceOf(RuntimeException.class, exception);
    }
}
