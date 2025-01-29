/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.validation;

import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.AccountID;
import org.junit.jupiter.api.Test;

class ExpiryMetaTest {
    @Test
    void detectsExplicitExpiry() {
        final var withExpiry = new ExpiryMeta(1L, NA, null);
        final var withoutExpiry = new ExpiryMeta(NA, NA, null);
        assertTrue(withExpiry.hasExplicitExpiry());
        assertFalse(withoutExpiry.hasExplicitExpiry());
    }

    @Test
    void detectsRenewPeriod() {
        final var withRenewPeriod = new ExpiryMeta(NA, 1L, null);
        final var withoutRenewPeriod =
                new ExpiryMeta(2L, NA, AccountID.newBuilder().accountNum(1L).build());
        assertTrue(withRenewPeriod.hasAutoRenewPeriod());
        assertFalse(withoutRenewPeriod.hasAutoRenewPeriod());
    }

    @Test
    void detectsRenewNum() {
        final var withRenewNum =
                new ExpiryMeta(NA, 1L, AccountID.newBuilder().accountNum(1L).build());
        final var withoutRenewNum = new ExpiryMeta(2L, NA, null);
        assertTrue(withRenewNum.hasAutoRenewAccountId());
        assertFalse(withoutRenewNum.hasAutoRenewAccountId());
    }

    @Test
    void detectsFullAutoRenewSpec() {
        final var withFullSpec =
                new ExpiryMeta(NA, 1L, AccountID.newBuilder().accountNum(1L).build());
        final var withoutFullSpec =
                new ExpiryMeta(2L, NA, AccountID.newBuilder().accountNum(1L).build());
        assertTrue(withFullSpec.hasFullAutoRenewSpec());
        assertFalse(withoutFullSpec.hasFullAutoRenewSpec());
    }
}
