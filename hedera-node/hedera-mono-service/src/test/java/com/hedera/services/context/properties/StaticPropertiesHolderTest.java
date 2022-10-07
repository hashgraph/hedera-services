/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.config.MockHederaNumbers;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.test.utils.IdUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StaticPropertiesHolderTest {
    private final HederaNumbers defaultNumbers = new MockHederaNumbers();

    @Test
    void hasExpectedNumbers() {
        // setup:
        final var pretendNumbers = mock(HederaNumbers.class);
        final var expectedAccount = IdUtils.asAccount("0.0.3");
        final var expectedToken = IdUtils.asToken("0.0.3");
        final var expectedSchedule = IdUtils.asSchedule("0.0.3");

        given(pretendNumbers.shard()).willReturn(0L);
        given(pretendNumbers.realm()).willReturn(0L);

        // when:
        STATIC_PROPERTIES.configureNumbers(pretendNumbers, 100);

        // then:
        assertEquals(0L, STATIC_PROPERTIES.getShard());
        assertEquals(0L, STATIC_PROPERTIES.getRealm());
        assertEquals(expectedAccount, STATIC_PROPERTIES.scopedAccountWith(3L));
        assertEquals(expectedToken, STATIC_PROPERTIES.scopedTokenWith(3L));
        assertEquals(expectedSchedule, STATIC_PROPERTIES.scopedScheduleWith(3L));
        assertEquals("0.0.3", STATIC_PROPERTIES.scopedIdLiteralWith(3L));
        final var key = STATIC_PROPERTIES.scopedContractKeyWith(4L);
        assertInstanceOf(JContractIDKey.class, key);
        assertEquals(4L, key.getContractID().getContractNum());
        // and:
        assertFalse(STATIC_PROPERTIES.isThrottleExempt(0));
        assertFalse(STATIC_PROPERTIES.isThrottleExempt(101));
        assertTrue(STATIC_PROPERTIES.isThrottleExempt(1));
        assertTrue(STATIC_PROPERTIES.isThrottleExempt(100));
    }

    @AfterEach
    void cleanup() {
        STATIC_PROPERTIES.configureNumbers(defaultNumbers, 100);
    }
}
