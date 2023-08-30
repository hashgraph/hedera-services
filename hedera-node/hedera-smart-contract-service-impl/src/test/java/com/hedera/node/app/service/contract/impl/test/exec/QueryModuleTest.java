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

package com.hedera.node.app.service.contract.impl.test.exec;

import static com.hedera.node.app.service.contract.impl.exec.TransactionModule.provideActionSidecarContentTracer;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.hedera.node.app.service.contract.impl.exec.EvmActionTracer;
import com.hedera.node.app.service.contract.impl.exec.QueryModule;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryModuleTest {
    @Mock
    private QueryContext context;

    @Mock
    private HederaOperations hederaOperations;

    @Mock
    SystemContractOperations systemContractOperations;

    @Mock
    private EvmFrameStateFactory factory;

    @Mock
    private HederaEvmBlocks hederaEvmBlocks;

    @Mock
    private ReadableAccountStore readableAccountStore;

    @Mock
    private Configuration config;

    @Test
    void providesExpectedProxyWorldUpdater() {
        assertInstanceOf(
                ProxyWorldUpdater.class,
                QueryModule.provideProxyWorldUpdater(hederaOperations, systemContractOperations, factory));
    }

    @Test
    void createsEvmActionTracer() {
        assertInstanceOf(EvmActionTracer.class, provideActionSidecarContentTracer());
    }

    @Test
    void feesOnlyUpdaterIsProxyUpdater() {
        assertInstanceOf(
                ProxyWorldUpdater.class,
                QueryModule.provideFeesOnlyUpdater(hederaOperations, systemContractOperations, factory)
                        .get());
    }

    @Test
    void providesExpectedHederaEvmContext() {
        assertInstanceOf(
                HederaEvmContext.class, QueryModule.provideHederaEvmContext(hederaOperations, hederaEvmBlocks));
    }
}
