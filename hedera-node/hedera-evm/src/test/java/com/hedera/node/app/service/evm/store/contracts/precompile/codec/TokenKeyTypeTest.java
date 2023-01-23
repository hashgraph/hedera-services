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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class TokenKeyTypeTest {

    @Test
    void test() {
        assertEquals(
                TokenKeyType.ADMIN_KEY,
                new GetTokenKeyWrapper<>(Bytes.EMPTY.toArray(), 1).tokenKeyType());
        assertEquals(
                TokenKeyType.KYC_KEY,
                new GetTokenKeyWrapper<>(Bytes.EMPTY.toArray(), 2).tokenKeyType());
        assertEquals(
                TokenKeyType.FREEZE_KEY,
                new GetTokenKeyWrapper<>(Bytes.EMPTY.toArray(), 4).tokenKeyType());
        assertEquals(
                TokenKeyType.WIPE_KEY,
                new GetTokenKeyWrapper<>(Bytes.EMPTY.toArray(), 8).tokenKeyType());
        assertEquals(
                TokenKeyType.SUPPLY_KEY,
                new GetTokenKeyWrapper<>(Bytes.EMPTY.toArray(), 16).tokenKeyType());
        assertEquals(
                TokenKeyType.FEE_SCHEDULE_KEY,
                new GetTokenKeyWrapper<>(Bytes.EMPTY.toArray(), 32).tokenKeyType());
        assertEquals(
                TokenKeyType.PAUSE_KEY,
                new GetTokenKeyWrapper<>(Bytes.EMPTY.toArray(), 64).tokenKeyType());
        assertThrows(
                InvalidTransactionException.class,
                () -> new GetTokenKeyWrapper<>(Bytes.EMPTY.toArray(), 5).tokenKeyType());
    }
}
