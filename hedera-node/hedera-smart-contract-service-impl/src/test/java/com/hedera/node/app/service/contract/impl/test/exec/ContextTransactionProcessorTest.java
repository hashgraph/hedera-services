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

import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmVersion.VERSION_038;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.HEVM_CREATION;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.ContextTransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.infra.HevmTransactionFactory;
import com.hedera.node.app.service.contract.impl.state.BaseProxyWorldUpdater;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextTransactionProcessorTest {
    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.createConfig();

    @Mock
    private HandleContext context;

    @Mock
    private HederaEvmContext hederaEvmContext;

    @Mock
    private ActionSidecarContentTracer tracer;

    @Mock
    private HevmTransactionFactory hevmTransactionFactory;

    @Mock
    private TransactionProcessor processor;

    @Mock
    private BaseProxyWorldUpdater baseProxyWorldUpdater;

    @Mock
    private Supplier<HederaWorldUpdater> feesOnlyUpdater;

    private ContextTransactionProcessor subject;

    @BeforeEach
    void setUp() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var processors = Map.of(VERSION_038, processor);
        subject = new ContextTransactionProcessor(
                context,
                contractsConfig,
                CONFIGURATION,
                hederaEvmContext,
                tracer,
                baseProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processors);
    }

    @Test
    void callsComponentInfraAsExpected() {
        given(context.body()).willReturn(TransactionBody.DEFAULT);
        given(hevmTransactionFactory.fromHapiTransaction(TransactionBody.DEFAULT))
                .willReturn(HEVM_CREATION);
        given(processor.processTransaction(
                        HEVM_CREATION, baseProxyWorldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, CONFIGURATION))
                .willReturn(SUCCESS_RESULT);

        assertEquals(SUCCESS_RESULT.asProtoResultForBase(baseProxyWorldUpdater), subject.call());
    }
}
