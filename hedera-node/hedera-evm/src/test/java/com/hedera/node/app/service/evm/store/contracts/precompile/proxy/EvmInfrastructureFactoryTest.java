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

package com.hedera.node.app.service.evm.store.contracts.precompile.proxy;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.hedera.node.app.service.evm.store.contracts.precompile.EvmInfrastructureFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;
import com.hedera.node.app.service.evm.store.tokens.TokenAccessor;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvmInfrastructureFactoryTest {

    @Mock
    private MessageFrame frame;

    @Mock
    private EvmEncodingFacade evmEncoder;

    @Mock
    private ViewGasCalculator gasCalculator;

    @Mock
    private TokenAccessor tokenAccessor;

    private EvmInfrastructureFactory subject;

    @BeforeEach
    void setUp() {
        subject = new EvmInfrastructureFactory(evmEncoder);
    }

    @Test
    void canCreateViewExecutor() {
        final var fakeInput = Bytes.of(1, 2, 3);
        final var viewExecutor = subject.newViewExecutor(fakeInput, frame, gasCalculator, tokenAccessor);
        assertInstanceOf(ViewExecutor.class, viewExecutor);
    }

    @Test
    void canCreateNewRedirectExecutor() {
        assertInstanceOf(
                RedirectViewExecutor.class,
                subject.newRedirectExecutor(Bytes.EMPTY, frame, gasCalculator, tokenAccessor));
    }
}
