/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.execution;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallLocalEvmTxProcessorTest {
    private static final int MAX_STACK_SIZE = 1024;

    @Mock private LivePricesSource livePricesSource;
    @Mock private HederaWorldState worldState;
    @Mock private CodeCache codeCache;
    @Mock private GlobalDynamicProperties globalDynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private Set<Operation> operations;
    @Mock private Transaction transaction;
    @Mock private HederaWorldState.Updater updater;
    @Mock private HederaStackedWorldStateUpdater stackedUpdater;
    @Mock private Map<String, PrecompiledContract> precompiledContractMap;
    @Mock private AliasManager aliasManager;
    @Mock private BlockMetaSource blockMetaSource;
    @Mock private HederaBlockValues hederaBlockValues;

    private final Account sender = new Account(new Id(0, 0, 1002));
    private final Account receiver = new Account(new Id(0, 0, 1006));
    private final Address receiverAddress = receiver.getId().asEvmAddress();
    private final Instant consensusTime = Instant.now();

    private CallLocalEvmTxProcessor callLocalEvmTxProcessor;

    @BeforeEach
    private void setup() {
        CommonProcessorSetup.setup(gasCalculator);

        callLocalEvmTxProcessor =
                new CallLocalEvmTxProcessor(
                        codeCache,
                        livePricesSource,
                        globalDynamicProperties,
                        gasCalculator,
                        operations,
                        precompiledContractMap,
                        aliasManager);

        callLocalEvmTxProcessor.setWorldState(worldState);
        callLocalEvmTxProcessor.setBlockMetaSource(blockMetaSource);
    }

    @Test
    void assertSuccessExecutе() {
        givenValidMock();
        given(blockMetaSource.computeBlockValues(anyLong())).willReturn(hederaBlockValues);
        final var receiverAddress = receiver.getId().asEvmAddress();
        given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
        given(globalDynamicProperties.chainIdBytes32()).willReturn(Bytes32.ZERO);
        var result =
                callLocalEvmTxProcessor.execute(
                        sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY);
        assertTrue(result.isSuccessful());
        assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
    }

    @Test
    void throwsWhenCodeCacheFailsLoading() {
        given(worldState.updater()).willReturn(updater);
        given(worldState.updater().updater()).willReturn(updater);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());

        var evmAccount = mock(EvmAccount.class);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);

        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(evmAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()).getMutable())
                .willReturn(mock(MutableAccount.class));
        given(worldState.updater()).willReturn(updater);

        var senderMutableAccount = mock(MutableAccount.class);

        given(updater.getSenderAccount(any())).willReturn(evmAccount);
        given(updater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
        given(updater.getOrCreate(any())).willReturn(evmAccount);
        given(updater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);
        given(globalDynamicProperties.chainIdBytes32()).willReturn(Bytes32.ZERO);

        assertFailsWith(
                () ->
                        callLocalEvmTxProcessor.execute(
                                sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY),
                INVALID_CONTRACT_ID);
    }

    @Test
    void assertIsContractCallFunctionality() {
        // expect:
        assertEquals(
                HederaFunctionality.ContractCallLocal, callLocalEvmTxProcessor.getFunctionType());
    }

    @Test
    void assertTransactionSenderAndValue() {
        // setup:
        doReturn(Optional.of(receiver.getId().asEvmAddress())).when(transaction).getTo();
        given(codeCache.getIfPresent(any())).willReturn(Code.EMPTY);
        given(transaction.getSender()).willReturn(sender.getId().asEvmAddress());
        given(transaction.getValue()).willReturn(Wei.of(1L));
        final MessageFrame.Builder commonInitialFrame =
                MessageFrame.builder()
                        .messageFrameStack(mock(Deque.class))
                        .maxStackSize(MAX_STACK_SIZE)
                        .worldUpdater(mock(WorldUpdater.class))
                        .initialGas(1_000_000L)
                        .originator(sender.getId().asEvmAddress())
                        .gasPrice(Wei.ZERO)
                        .sender(sender.getId().asEvmAddress())
                        .value(Wei.of(transaction.getValue().getAsBigInteger()))
                        .apparentValue(Wei.of(transaction.getValue().getAsBigInteger()))
                        .blockValues(mock(BlockValues.class))
                        .depth(0)
                        .completer(__ -> {})
                        .miningBeneficiary(Address.ZERO)
                        .blockHashLookup(h -> null);
        // when:
        MessageFrame buildMessageFrame =
                callLocalEvmTxProcessor.buildInitialFrame(
                        commonInitialFrame, (Address) transaction.getTo().get(), Bytes.EMPTY, 0L);

        // expect:
        assertEquals(transaction.getSender(), buildMessageFrame.getSenderAddress());
        assertEquals(transaction.getValue(), buildMessageFrame.getApparentValue());
    }

    private void givenValidMock() {
        given(worldState.updater()).willReturn(updater);
        given(worldState.updater().updater()).willReturn(stackedUpdater);
        given(globalDynamicProperties.fundingAccount())
                .willReturn(new Id(0, 0, 1010).asGrpcAccount());

        var evmAccount = mock(EvmAccount.class);

        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(0L);

        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()))
                .willReturn(evmAccount);
        given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()).getMutable())
                .willReturn(mock(MutableAccount.class));
        given(codeCache.getIfPresent(any())).willReturn(Code.EMPTY);

        var senderMutableAccount = mock(MutableAccount.class);
        given(senderMutableAccount.decrementBalance(any())).willReturn(Wei.of(1234L));
        given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));

        given(gasCalculator.getSelfDestructRefundAmount()).willReturn(0L);
        given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

        given(stackedUpdater.getSenderAccount(any())).willReturn(evmAccount);
        given(stackedUpdater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
        given(stackedUpdater.getOrCreate(any())).willReturn(evmAccount);
        given(stackedUpdater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);
    }
}
