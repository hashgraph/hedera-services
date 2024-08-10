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

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.swirlds.metrics.api.Metrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutorComponentTest {
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
        final var executorModule =
                new ExecutorModule(fileService, contractService, configProvider, bootstrapConfigProvider);
        final var subject = DaggerExecutorComponent.builder()
                .metrics(metrics)
                .executorModule(executorModule)
                .build();

        assertNotNull(subject.infrastructureInitializer());
    }
}
