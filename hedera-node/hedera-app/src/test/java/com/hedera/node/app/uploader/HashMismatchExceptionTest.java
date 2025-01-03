/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.uploader;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

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
