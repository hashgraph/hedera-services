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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmVersion.VERSION_038;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.HEVM_CREATION;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.exec.ContextQueryProcessor;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmVersion;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.infra.HevmStaticTransactionFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextQueryProcessorTest {
    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.createConfig();

    @Mock
    private QueryContext context;

    @Mock
    private TransactionProcessor processor;

    @Mock
    private HederaEvmContext hederaEvmContext;

    @Mock
    private ActionSidecarContentTracer tracer;

    @Mock
    private ProxyWorldUpdater proxyWorldUpdater;

    @Mock
    private HevmStaticTransactionFactory hevmStaticTransactionFactory;

    @Mock
    private Supplier<HederaWorldUpdater> feesOnlyUpdater;

    @Mock
    private Map<HederaEvmVersion, TransactionProcessor> processors;

    @Test
    void callsComponentInfraAsExpectedForValidQuery() {
        final var contractsConfig = CONFIGURATION.getConfigData(ContractsConfig.class);
        final var processors = Map.of(VERSION_038, processor);

        final var subject = new ContextQueryProcessor(
                context,
                hederaEvmContext,
                tracer,
                proxyWorldUpdater,
                hevmStaticTransactionFactory,
                feesOnlyUpdater,
                processors);

        given(context.configuration()).willReturn(CONFIGURATION);
        given(context.query()).willReturn(Query.DEFAULT);
        given(hevmStaticTransactionFactory.fromHapiQuery(Query.DEFAULT)).willReturn(HEVM_CREATION);
        given(processor.processTransaction(
                        HEVM_CREATION, proxyWorldUpdater, feesOnlyUpdater, hederaEvmContext, tracer, CONFIGURATION))
                .willReturn(SUCCESS_RESULT);
        final var protoResult = SUCCESS_RESULT.asQueryResult();
        final var expectedResult = new CallOutcome(protoResult, SUCCESS);
        assertEquals(expectedResult, subject.call());
    }
}
