/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.validation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SummarizedExpiryMetaTest {
    @Test
    void ensuresMetaIsValidWhenExpected() {
        assertThrows(
                IllegalStateException.class,
                SummarizedExpiryMeta.INVALID_EXPIRY_SUMMARY::knownValidMeta);
    }

    @Test
    void returnsMetaIfValid() {
        final var expected = ExpiryMeta.withExplicitExpiry(1_234_567L);
        final var subject = SummarizedExpiryMeta.forValid(expected);
        final var actual = subject.knownValidMeta();
        assertSame(expected, actual);
    }
}
