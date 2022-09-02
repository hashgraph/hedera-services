/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.impl.BalanceOfPrecompile.decodeBalanceOf;
import static java.util.function.UnaryOperator.identity;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.store.contracts.precompile.impl.BalanceOfPrecompile;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalanceOfPrecompileTest {
    private static final Bytes BALANCE_INPUT =
            Bytes.fromHexString(
                    "0x70a08231000000000000000000000000000000000000000000000000000000000000059f");
    private MockedStatic<BalanceOfPrecompile> balanceOfPrecompile;

    @BeforeEach
    void setUp() {
        balanceOfPrecompile = Mockito.mockStatic(BalanceOfPrecompile.class);
    }

    @AfterEach
    void closeMocks() {
        balanceOfPrecompile.close();
    }

    @Test
    void decodeBalanceInput() {
        balanceOfPrecompile
                .when(() -> decodeBalanceOf(BALANCE_INPUT, identity()))
                .thenCallRealMethod();
        final var decodedInput = decodeBalanceOf(BALANCE_INPUT, identity());

        assertTrue(decodedInput.accountId().getAccountNum() > 0);
    }
}
