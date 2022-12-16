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
package com.hedera.node.app.service.mono.token.impl;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.ReadableState;
import com.hedera.node.app.spi.state.ReadableStates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoServiceImplTest {
    @Mock private ReadableState<Long, MerkleAccount> aliases;
    @Mock private ReadableState<Long, MerkleAccount> accounts;
    @Mock ReadableStates states;
    @Mock PreHandleContext ctx;

    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";
    private CryptoServiceImpl subject;

    @Test
    void createsNewInstance() {
        subject = new CryptoServiceImpl();

        given(states.<Long, MerkleAccount>get(ACCOUNTS)).willReturn(accounts);
        given(states.<Long, MerkleAccount>get(ALIASES)).willReturn(aliases);

        final var serviceImpl = subject.createPreTransactionHandler(states, ctx);
        final var serviceImpl1 = subject.createPreTransactionHandler(states, ctx);
        assertNotEquals(serviceImpl1, serviceImpl);
    }
}
