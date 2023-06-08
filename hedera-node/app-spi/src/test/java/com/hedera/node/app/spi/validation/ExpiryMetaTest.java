/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

import org.junit.jupiter.api.Test;

class ExpiryMetaTest {
    @Test
    void detectsExplicitExpiry() {
        final var withExpiry = new ExpiryMeta(1L, NA, NA);
        final var withoutExpiry = new ExpiryMeta(NA, NA, NA);
        assertTrue(withExpiry.hasExplicitExpiry());
        assertFalse(withoutExpiry.hasExplicitExpiry());
    }

    @Test
    void detectsRenewPeriod() {
        final var withRenewPeriod = new ExpiryMeta(NA, 1L, NA);
        final var withoutRenewPeriod = new ExpiryMeta(2L, NA, 1L);
        assertTrue(withRenewPeriod.hasAutoRenewPeriod());
        assertFalse(withoutRenewPeriod.hasAutoRenewPeriod());
    }

    @Test
    void detectsRenewNum() {
        final var withRenewNum = new ExpiryMeta(NA, 1L, 1L);
        final var withoutRenewNum = new ExpiryMeta(2L, NA, NA);
        assertTrue(withRenewNum.hasAutoRenewNum());
        assertFalse(withoutRenewNum.hasAutoRenewNum());
    }

    @Test
    void detectsFullAutoRenewSpec() {
        final var withFullSpec = new ExpiryMeta(NA, 1L, 1L);
        final var withoutFullSpec = new ExpiryMeta(2L, NA, 1L);
        assertTrue(withFullSpec.hasFullAutoRenewSpec());
        assertFalse(withoutFullSpec.hasFullAutoRenewSpec());
    }
}
