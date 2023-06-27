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

package com.hedera.node.app.service.contract.impl.test.hevm;

import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmVersion.*;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownContextWith;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.wellKnownHapiCall;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmCode;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionProcessor;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.swirlds.config.api.Configuration;
import java.util.Map;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmTransactionProcessorTest {
    @Mock
    private HederaEvmCode code;

    @Mock
    private HederaEvmBlocks blocks;

    @Mock
    private HederaWorldUpdater worldUpdater;

    @Mock
    private OperationTracer tracer;

    @Mock
    private Configuration config;

    @Mock
    private TransactionProcessor v30processor;

    @Mock
    private TransactionProcessor v34processor;

    @Mock
    private TransactionProcessor v38processor;

    private HederaEvmTransactionProcessor subject;

    @BeforeEach
    void setUp() {
        subject = new HederaEvmTransactionProcessor(Map.of(
                VERSION_030, v30processor,
                VERSION_034, v34processor,
                VERSION_038, v38processor));
    }

    @Test
    void calls030AsExpected() {
        final var transaction = wellKnownHapiCall();
        final var context = wellKnownContextWith(code, blocks, false);

        subject.process(transaction, worldUpdater, context, VERSION_030, tracer, config);

        verify(v30processor).processTransaction(transaction, worldUpdater, context, tracer, config);
    }

    @Test
    void calls034AsExpected() {
        final var transaction = wellKnownHapiCall();
        final var context = wellKnownContextWith(code, blocks, false);

        subject.process(transaction, worldUpdater, context, VERSION_034, tracer, config);

        verify(v34processor).processTransaction(transaction, worldUpdater, context, tracer, config);
    }

    @Test
    void calls038AsExpected() {
        final var transaction = wellKnownHapiCall();
        final var context = wellKnownContextWith(code, blocks, false);

        subject.process(transaction, worldUpdater, context, VERSION_038, tracer, config);

        verify(v38processor).processTransaction(transaction, worldUpdater, context, tracer, config);
    }
}
