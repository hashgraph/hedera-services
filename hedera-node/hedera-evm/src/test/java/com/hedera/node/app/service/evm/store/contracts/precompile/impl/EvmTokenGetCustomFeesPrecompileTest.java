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
class EvmTokenGetCustomFeesPrecompileTest {

    private static final Bytes GET_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT =
            Bytes.fromHexString(
                    "0xae7611a000000000000000000000000000000000000000000000000000000000000003ee");

    @Test
    void decodeTokenGetCustomFees() {
        final var decodedInput =
                EvmTokenGetCustomFeesPrecompile.decodeTokenGetCustomFees(
                        GET_FUNGIBLE_TOKEN_CUSTOM_FEES_INPUT);

        assertTrue(decodedInput.token().length > 0);
    }
}
