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
package com.hedera.node.app.service.evm.store.tokens;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * This test is temporary just to cover the 2 new Enums introduced with the TokenAccessor interface
 * and will be removed once the ViewExecutor and RedirectViewExecutor classes get exported
 */
class TemporaryEnumTest {

    @Test
    void dummyEnumTest() {
        assertEquals(TokenType.FUNGIBLE_COMMON, TokenType.valueOf("FUNGIBLE_COMMON"));
        assertEquals(TokenType.NON_FUNGIBLE_UNIQUE, TokenType.valueOf("NON_FUNGIBLE_UNIQUE"));
        assertEquals(TokenKey.ADMIN_KEY, TokenKey.valueOf("ADMIN_KEY"));
        assertEquals(TokenKey.KYC_KEY, TokenKey.valueOf("KYC_KEY"));
        assertEquals(TokenKey.WIPE_KEY, TokenKey.valueOf("WIPE_KEY"));
        assertEquals(TokenKey.SUPPLY_KEY, TokenKey.valueOf("SUPPLY_KEY"));
        assertEquals(TokenKey.FEE_SCHEDULE_KEY, TokenKey.valueOf("FEE_SCHEDULE_KEY"));
        assertEquals(TokenKey.PAUSE_KEY, TokenKey.valueOf("PAUSE_KEY"));
    }
}
