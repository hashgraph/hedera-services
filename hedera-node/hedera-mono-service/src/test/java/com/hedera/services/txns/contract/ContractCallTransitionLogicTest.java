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
package com.hedera.services.txns.contract;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.CallEvmTxProcessor;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.utility.CommonUtils;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContractCallTransitionLogicTest {
    private final ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();
    private long gas = 1_234;
    private long sent = 1_234L;
    private static final long maxGas = 666_666L;
    private static final BigInteger biOfferedGasPrice = BigInteger.valueOf(111L);

    @Mock private TransactionContext txnCtx;
    @Mock private PlatformTxnAccessor accessor;
    @Mock private AccountStore accountStore;
    @Mock private HederaWorldState worldState;
    @Mock private TransactionRecordService recordService;
    @Mock private CallEvmTxProcessor evmTxProcessor;
    @Mock private GlobalDynamicProperties properties;
    @Mock private CodeCache codeCache;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private AliasManager aliasManager;
    @Mock private EntityAccess entityAccess;

    private TransactionBody contractCallTxn;
    private final Account senderAccount = new Account(new Id(0, 0, 1002));
    private final Account relayerAccount = new Account(new Id(0, 0, 1003));
    private final Account contractAccount = new Account(new Id(0, 0, 1006));
    ContractCallTransitionLogic subject;

    @BeforeEach
    void setup() {
        subject =
                new ContractCallTransitionLogic(
                        txnCtx,
                        accountStore,
                        worldState,
                        recordService,
                        evmTxProcessor,
                        properties,
                        codeCache,
                        sigImpactHistorian,
                        aliasManager,
                        entityAccess);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(contractCallTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void verifyExternaliseContractResultCall() {
        // setup:
        givenValidTxnCtx();
        // and:
        given(accessor.getTxn()).willReturn(contractCallTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        // and:
        given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
        given(
                        accountStore.loadContract(
                                new Id(
                                        target.getShardNum(),
                                        target.getRealmNum(),
                                        target.getContractNum())))
                .willReturn(contractAccount);
        // and:
        var results =
                TransactionProcessingResult.successful(
                        null,
                        1234L,
                        0L,
                        124L,
                        Bytes.EMPTY,
                        contractAccount.getId().asEvmAddress(),
                        Map.of(),
                        List.of());
        given(
                        evmTxProcessor.execute(
                                senderAccount,
                                contractAccount.getId().asEvmAddress(),
                                gas,
                                sent,
                                Bytes.EMPTY,
                                txnCtx.consensusTime()))
                .willReturn(results);
        given(worldState.getCreatedContractIds()).willReturn(List.of(target));
        // when:
        subject.doStateTransition();

        // then:
        verify(recordService).externaliseEvmCallTransaction(any());
        verify(worldState).getCreatedContractIds();
        verify(txnCtx).setTargetedContract(target);
    }

    @Test
    void verifyExternaliseContractResultCallEth() {
        // setup:
        givenValidTxnCtx();
        // and:
        given(accessor.getTxn()).willReturn(contractCallTxn);
        // and:
        given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
        given(
                        accountStore.loadContract(
                                new Id(
                                        target.getShardNum(),
                                        target.getRealmNum(),
                                        target.getContractNum())))
                .willReturn(contractAccount);
        given(accountStore.loadAccount(relayerAccount.getId())).willReturn(relayerAccount);
        // and:
        var results =
                TransactionProcessingResult.successful(
                        null,
                        1234L,
                        0L,
                        124L,
                        Bytes.EMPTY,
                        contractAccount.getId().asEvmAddress(),
                        Map.of(),
                        List.of());
        given(
                        evmTxProcessor.executeEth(
                                senderAccount,
                                contractAccount.getId().asEvmAddress(),
                                gas,
                                sent,
                                Bytes.EMPTY,
                                txnCtx.consensusTime(),
                                biOfferedGasPrice,
                                relayerAccount,
                                maxGas))
                .willReturn(results);
        given(worldState.getCreatedContractIds()).willReturn(List.of(target));
        // when:
        subject.doStateTransitionOperation(
                accessor.getTxn(),
                senderAccount.getId(),
                relayerAccount.getId(),
                maxGas,
                biOfferedGasPrice);

        // then:
        verify(recordService).externaliseEvmCallTransaction(any());
        verify(worldState).getCreatedContractIds();
        verify(txnCtx).setTargetedContract(target);
    }

    @Test
    void verifyAccountStoreNotQueriedForTokenAddress() {
        // setup:
        givenValidTxnCtx();
        // and:
        given(accessor.getTxn()).willReturn(contractCallTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        // and:
        given(entityAccess.isTokenAccount(any())).willReturn(true);
        given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);

        // and:
        var results =
                TransactionProcessingResult.successful(
                        null,
                        1234L,
                        0L,
                        124L,
                        Bytes.EMPTY,
                        contractAccount.getId().asEvmAddress(),
                        Map.of(),
                        List.of());
        given(
                        evmTxProcessor.execute(
                                senderAccount,
                                new Account(
                                                new Id(
                                                        target.getShardNum(),
                                                        target.getRealmNum(),
                                                        target.getContractNum()))
                                        .canonicalAddress(),
                                gas,
                                sent,
                                Bytes.EMPTY,
                                txnCtx.consensusTime()))
                .willReturn(results);
        given(worldState.getCreatedContractIds()).willReturn(List.of(target));
        // when:
        subject.doStateTransition();

        // then:
        verifyNoMoreInteractions(accountStore);

        verify(recordService).externaliseEvmCallTransaction(any());
        verify(worldState).getCreatedContractIds();
        verify(txnCtx).setTargetedContract(target);
    }

    @Test
    void verifyProcessorCallingWithCorrectCallData() {
        // setup:
        ByteString functionParams = ByteString.copyFromUtf8("0x00120");
        var op =
                TransactionBody.newBuilder()
                        .setContractCall(
                                ContractCallTransactionBody.newBuilder()
                                        .setGas(gas)
                                        .setAmount(sent)
                                        .setFunctionParameters(functionParams)
                                        .setContractID(target));
        contractCallTxn = op.build();
        // and:
        given(accessor.getTxn()).willReturn(contractCallTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);
        // and:
        given(accountStore.loadAccount(senderAccount.getId())).willReturn(senderAccount);
        given(
                        accountStore.loadContract(
                                new Id(
                                        target.getShardNum(),
                                        target.getRealmNum(),
                                        target.getContractNum())))
                .willReturn(contractAccount);
        // and:
        var results =
                TransactionProcessingResult.successful(
                        null,
                        1234L,
                        0L,
                        124L,
                        Bytes.EMPTY,
                        contractAccount.getId().asEvmAddress(),
                        Map.of(),
                        List.of());
        given(
                        evmTxProcessor.execute(
                                senderAccount,
                                contractAccount.getId().asEvmAddress(),
                                gas,
                                sent,
                                Bytes.fromHexString(CommonUtils.hex(functionParams.toByteArray())),
                                txnCtx.consensusTime()))
                .willReturn(results);
        given(worldState.getCreatedContractIds()).willReturn(List.of(target));
        // when:
        subject.doStateTransition();

        // then:
        verify(evmTxProcessor)
                .execute(
                        senderAccount,
                        contractAccount.getId().asEvmAddress(),
                        gas,
                        sent,
                        Bytes.fromHexString(CommonUtils.hex(functionParams.toByteArray())),
                        txnCtx.consensusTime());
        verify(sigImpactHistorian).markEntityChanged(target.getContractNum());
    }

    @Test
    void successfulPreFetch() {
        final var targetAlias = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e25");
        final var target =
                ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(targetAlias)).build();
        final var targetNum = EntityNum.fromLong(1234);
        final var txnBody = Mockito.mock(TransactionBody.class);
        final var ccTxnBody = Mockito.mock(ContractCallTransactionBody.class);

        given(accessor.getTxn()).willReturn(txnBody);
        given(txnBody.getContractCall()).willReturn(ccTxnBody);
        given(ccTxnBody.getContractID()).willReturn(target);
        given(aliasManager.lookupIdBy(target.getEvmAddress())).willReturn(targetNum);

        subject.preFetch(accessor);

        verify(codeCache).getIfPresent(targetNum.toEvmAddress());
    }

    @Test
    void codeCacheThrowingExceptionDuringGetDoesntPropagate() {
        TransactionBody txnBody = Mockito.mock(TransactionBody.class);
        ContractCallTransactionBody ccTxnBody = Mockito.mock(ContractCallTransactionBody.class);

        given(accessor.getTxn()).willReturn(txnBody);
        given(txnBody.getContractCall()).willReturn(ccTxnBody);
        given(ccTxnBody.getContractID()).willReturn(IdUtils.asContract("0.0.1324"));
        given(codeCache.getIfPresent(any(Address.class))).willThrow(new RuntimeException("oh no"));

        // when:
        assertDoesNotThrow(() -> subject.preFetch(accessor));
    }

    @Test
    void acceptsOkSyntax() {
        givenValidTxnCtx();
        given(properties.maxGasPerSec()).willReturn(gas + 1);
        // expect:
        assertEquals(OK, subject.semanticCheck().apply(contractCallTxn));
    }

    @Test
    void providingGasOverLimitReturnsCorrectPrecheck() {
        givenValidTxnCtx();
        given(properties.maxGasPerSec()).willReturn(gas - 1);
        // expect:
        assertEquals(MAX_GAS_LIMIT_EXCEEDED, subject.semanticCheck().apply(contractCallTxn));
    }

    @Test
    void rejectsNegativeSend() {
        // setup:
        sent = -1;

        givenValidTxnCtx();
        // expect:
        assertEquals(CONTRACT_NEGATIVE_VALUE, subject.semanticCheck().apply(contractCallTxn));
    }

    @Test
    void rejectsNegativeGas() {
        // setup:
        gas = -1;

        givenValidTxnCtx();

        // expect:
        assertEquals(CONTRACT_NEGATIVE_GAS, subject.semanticCheck().apply(contractCallTxn));
    }

    private void givenValidTxnCtx() {
        var op =
                TransactionBody.newBuilder()
                        .setContractCall(
                                ContractCallTransactionBody.newBuilder()
                                        .setGas(gas)
                                        .setAmount(sent)
                                        .setContractID(target));
        contractCallTxn = op.build();
    }

    private AccountID ourAccount() {
        return senderAccount.getId().asGrpcAccount();
    }
}
