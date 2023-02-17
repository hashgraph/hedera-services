/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmGetTokenDefaultKycStatusTest {

    public static final Bytes GET_TOKEN_DEFAULT_KYC_STATUS_INPUT =
            Bytes.fromHexString("0x335e04c10000000000000000000000000000000000000000000000000000000000000404");

    @Test
    void decodeGetTokenDefaultKycStatus() {
        final var decodedInput =
                EvmGetTokenDefaultKycStatus.decodeTokenDefaultKycStatus(GET_TOKEN_DEFAULT_KYC_STATUS_INPUT);

        assertTrue(decodedInput.token().length > 0);
    }
}
