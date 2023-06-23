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

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionProcessorTest {
    @Mock
    private CustomMessageCallProcessor messageCallProcessor;

    @Mock
    private ContractCreationProcessor contractCreationProcessor;

    @Mock
    private HederaEvmBlocks blocks;

    @Mock
    private HederaWorldUpdater worldUpdater;

    @Mock
    private OperationTracer tracer;

    @Mock
    private Configuration config;

    @Mock
    private HederaEvmAccount senderAccount;

    @Mock
    private HederaEvmAccount calledAccount;
    @Mock
    private CustomGasCharging gasCharging;

    private TransactionProcessor subject;

    @BeforeEach
    void setUp() {
        subject = new TransactionProcessor(gasCharging, messageCallProcessor, contractCreationProcessor);
    }

    @Test
    void abortsOnMissingSender() {
        assertAbortsWith(INVALID_ACCOUNT_ID);
    }

    @Test
    void lazyCreationAttemptWithNoValueFailsFast() {
        givenSenderAccount();
        given(messageCallProcessor.isImplicitCreationEnabled(config)).willReturn(true);
        assertAbortsWith(wellKnownRelayedHapiCall(0), INVALID_CONTRACT_ID);
    }

    private void assertAbortsWith(@NonNull final ResponseCodeEnum reason) {
        assertAbortsWith(wellKnownHapiCall(), reason);
    }

    private void assertAbortsWith(
            @NonNull final HederaEvmTransaction transaction, @NonNull final ResponseCodeEnum reason) {
        final var result =
                subject.processTransaction(transaction, worldUpdater, wellKnownContextWith(blocks), tracer, config);
        assertEquals(reason, result.abortReason());
    }

    private void givenSenderAccount() {
        given(worldUpdater.getHederaAccount(SENDER_ID)).willReturn(senderAccount);
    }

    private void givenSenderAccount(final long balance) {
        given(worldUpdater.getHederaAccount(SENDER_ID)).willReturn(senderAccount);
        given(senderAccount.getBalance()).willReturn(Wei.of(balance));
    }

    private void givenReceiverAccount() {
        given(worldUpdater.getHederaAccount(CALLED_CONTRACT_ID)).willReturn(calledAccount);
    }

    private void givenEvmReceiverAccount() {
        given(worldUpdater.getHederaAccount(CALLED_CONTRACT_EVM_ADDRESS)).willReturn(calledAccount);
    }
}
