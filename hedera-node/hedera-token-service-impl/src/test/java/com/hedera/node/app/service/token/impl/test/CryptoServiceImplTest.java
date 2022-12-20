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
package com.hedera.node.app.service.token.impl.test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.impl.CryptoServiceImpl;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.States;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoServiceImplTest {
    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock States states;
    @Mock PreHandleContext ctx;

    private static final String ACCOUNTS = "ACCOUNTS";
    private static final String ALIASES = "ALIASES";
    private CryptoServiceImpl subject;

    @Test
    void createsNewInstance() {
        subject = new CryptoServiceImpl();

        BDDMockito.given(states.get(ACCOUNTS)).willReturn(accounts);
        BDDMockito.given(states.get(ALIASES)).willReturn(aliases);

        final var serviceImpl = subject.createPreTransactionHandler(states, ctx);
        final var serviceImpl1 = subject.createPreTransactionHandler(states, ctx);
        assertNotEquals(serviceImpl1, serviceImpl);
    }

    @Test
    void testSpi() {
        // when
        final CryptoService service = CryptoService.getInstance();

        // then
        Assertions.assertNotNull(service, "We must always receive an instance");
        Assertions.assertEquals(
                CryptoServiceImpl.class,
                service.getClass(),
                "We must always receive an instance of type CryptoServiceImpl");
    }
}
