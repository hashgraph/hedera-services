/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.standalone;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema.TXN_RECORD_QUEUE;
import static java.util.Collections.emptyIterator;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.handlers.ContractHandlers;
import com.hedera.node.app.service.contract.impl.handlers.EthereumTransactionHandler;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.state.recordcache.RecordCacheService;
import com.hedera.node.config.VersionedConfigImpl;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableQueueState;
import com.swirlds.state.spi.ReadableStates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutorComponentTest {
    @Mock
    private State state;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private EthereumTransactionHandler ethereumTransactionHandler;

    @Mock
    private ContractHandlers contractHandlers;

    @Mock
    private ReadableQueueState<TransactionRecordEntry> receiptQueue;

    @Mock
    private Metrics metrics;

    @Mock
    private FileServiceImpl fileService;

    @Mock
    private ContractServiceImpl contractService;

    @Mock
    private ConfigProviderImpl configProvider;

    @Mock
    private BootstrapConfigProviderImpl bootstrapConfigProvider;

    @Test
    void constructsObjectRoots() {
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(DEFAULT_CONFIG, 1L));
        given(state.getReadableStates(RecordCacheService.NAME)).willReturn(readableStates);
        given(readableStates.<TransactionRecordEntry>getQueue(TXN_RECORD_QUEUE)).willReturn(receiptQueue);
        given(receiptQueue.iterator()).willReturn(emptyIterator());
        given(contractService.handlers()).willReturn(contractHandlers);
        given(contractHandlers.ethereumTransactionHandler()).willReturn(ethereumTransactionHandler);

        final var executorModule =
                new ExecutorModule(fileService, contractService, configProvider, bootstrapConfigProvider);
        final var subject = DaggerExecutorComponent.builder()
                .metrics(metrics)
                .executorModule(executorModule)
                .build();

        assertDoesNotThrow(subject::executionInitializer);
        requireNonNull(subject.workingStateAccessor()).setState(state);
        assertDoesNotThrow(subject::standaloneDispatchFactory);
        assertDoesNotThrow(subject::dispatchProcessor);
    }
}
