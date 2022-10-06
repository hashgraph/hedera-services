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
package com.hedera.services.config;

import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_ADDRESS_BOOK_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_EXCHANGE_RATES_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_FEE_SCHEDULE_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_FREEZE_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_NODE_REWARD_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_SYSTEM_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_SYSTEM_DELETE_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_SYSTEM_UNDELETE_ADMIN;
import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_TREASURY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.context.properties.PropertySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountNumbersTest {
    PropertySource properties;
    AccountNumbers subject;

    @BeforeEach
    void setup() {
        properties = mock(PropertySource.class);
        given(properties.getLongProperty(ACCOUNTS_ADDRESS_BOOK_ADMIN)).willReturn(55L);
        given(properties.getLongProperty(ACCOUNTS_FEE_SCHEDULE_ADMIN)).willReturn(56L);
        given(properties.getLongProperty(ACCOUNTS_FREEZE_ADMIN)).willReturn(58L);
        given(properties.getLongProperty(ACCOUNTS_EXCHANGE_RATES_ADMIN)).willReturn(57L);
        given(properties.getLongProperty(ACCOUNTS_NODE_REWARD_ACCOUNT)).willReturn(801L);
        given(properties.getLongProperty(ACCOUNTS_STAKING_REWARD_ACCOUNT)).willReturn(800L);
        given(properties.getLongProperty(ACCOUNTS_SYSTEM_DELETE_ADMIN)).willReturn(59L);
        given(properties.getLongProperty(ACCOUNTS_SYSTEM_UNDELETE_ADMIN)).willReturn(60L);
        given(properties.getLongProperty(ACCOUNTS_SYSTEM_ADMIN)).willReturn(50L);
        given(properties.getLongProperty(ACCOUNTS_TREASURY)).willReturn(2L);

        subject = new AccountNumbers(properties);
    }

    @Test
    void hasExpectedNumbers() {
        // expect:
        assertEquals(2, subject.treasury());
        assertEquals(50, subject.systemAdmin());
        assertEquals(58, subject.freezeAdmin());
        assertEquals(55, subject.addressBookAdmin());
        assertEquals(56, subject.feeSchedulesAdmin());
        assertEquals(57, subject.exchangeRatesAdmin());
        assertEquals(59, subject.systemDeleteAdmin());
        assertEquals(60, subject.systemUndeleteAdmin());
        assertEquals(800, subject.stakingRewardAccount());
        assertEquals(801, subject.nodeRewardAccount());
    }

    @Test
    void recognizesAdmins() {
        // expect:
        assertTrue(subject.isSuperuser(2));
        assertTrue(subject.isSuperuser(50));
        assertFalse(subject.isSuperuser(3));
        assertFalse(subject.isSuperuser(55));
    }
}
