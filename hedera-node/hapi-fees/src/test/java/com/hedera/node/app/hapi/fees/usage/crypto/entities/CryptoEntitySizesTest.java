/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.fees.usage.crypto.entities;

import static com.hedera.node.app.hapi.fees.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BOOL_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.LONG_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CryptoEntitySizesTest {
    private final CryptoEntitySizes subject = CRYPTO_ENTITY_SIZES;

    @Test
    void knowsExpectedFixedBytes() {
        // expect:
        assertEquals(3 * BOOL_SIZE + 5 * LONG_SIZE, subject.fixedBytesInAccountRepr());
    }

    @Test
    void knowsTotalBytes() {
        // when:
        final var actual = subject.bytesInTokenAssocRepr();

        // then:
        assertEquals(LONG_SIZE + 3 * BOOL_SIZE, actual);
    }
}
