/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.usage.contract;

import static com.hedera.services.usage.contract.entities.ContractEntitySizes.CONTRACT_ENTITY_SIZES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.services.usage.crypto.ExtantCryptoContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtantContractContextTest {
    private static final int kvPairs = 64;
    private static final int bytecodeSize = 4096;
    private static final long nonBaseBytes = 666;

    @Mock private ExtantCryptoContext currentCryptoContext;

    private ExtantContractContext subject;

    @BeforeEach
    void setUp() {
        subject = new ExtantContractContext(kvPairs, currentCryptoContext);
    }

    @Test
    void getsExpectedRb() {
        given(currentCryptoContext.currentNonBaseRb()).willReturn(nonBaseBytes);

        final var expectedRb = CONTRACT_ENTITY_SIZES.fixedBytesInContractRepr() + nonBaseBytes;

        assertEquals(expectedRb, subject.currentRb());
    }

    @Test
    void getsExpectedKvPairs() {
        assertEquals(kvPairs, subject.currentNumKvPairs());
    }
}
