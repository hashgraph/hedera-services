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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LOCAL_CALL_MODIFICATION_EXCEPTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.services.contracts.operation.HederaExceptionalHaltReason;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.utility.CommonUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CallLocalExecutorTest {
    int gas = 1_234;
    ByteString params = ByteString.copyFrom("Hungry, and...".getBytes());
    Id callerID = new Id(0, 0, 123);
    Id contractID = new Id(0, 0, 456);
    Id senderID = new Id(0, 0, 789);

    ContractCallLocalQuery query;

    @Mock private AccountStore accountStore;
    @Mock private CallLocalEvmTxProcessor evmTxProcessor;
    @Mock private AliasManager aliasManager;
    @Mock private EntityAccess entityAccess;

    @BeforeEach
    private void setup() {
        query = localCallQuery(contractID.asGrpcContract(), ANSWER_ONLY);
    }

    @Test
    void processingSuccessfulWithAlias() {
        // setup:
        final var targetAlias = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e25");
        final var target =
                ContractID.newBuilder().setEvmAddress(ByteString.copyFrom(targetAlias)).build();
        query = localCallQuery(target, ANSWER_ONLY);
        given(aliasManager.lookupIdBy(target.getEvmAddress()))
                .willReturn(EntityNum.fromLong(contractID.num()));

        final var transactionProcessingResult =
                TransactionProcessingResult.successful(
                        new ArrayList<>(),
                        0,
                        0,
                        1,
                        Bytes.EMPTY,
                        callerID.asEvmAddress(),
                        new TreeMap<>(),
                        new ArrayList<>());
        final var expected = response(OK, transactionProcessingResult);

        given(accountStore.loadAccount(any())).willReturn(new Account(callerID));
        given(accountStore.loadContract(contractID)).willReturn(new Account(contractID));
        given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any()))
                .willReturn(transactionProcessingResult);

        // when:
        final var result =
                CallLocalExecutor.execute(
                        accountStore, evmTxProcessor, query, aliasManager, entityAccess);

        // then:
        assertEquals(expected, result);
    }

    @Test
    void processingSuccessfulWithAccountAlias() {
        // setup:
        final var senderAlias = CommonUtils.unhex("6aea3773ea468a814d954e6dec795bfee7d76e25");
        final var sender =
                AccountID.newBuilder().setAlias(ByteString.copyFrom(senderAlias)).build();
        query = localCallQuery(contractID.asGrpcContract(), sender, ANSWER_ONLY);
        given(aliasManager.lookupIdBy(sender.getAlias()))
                .willReturn(EntityNum.fromLong(senderID.num()));

        final var transactionProcessingResult =
                TransactionProcessingResult.successful(
                        new ArrayList<>(),
                        0,
                        0,
                        1,
                        Bytes.EMPTY,
                        callerID.asEvmAddress(),
                        new TreeMap<>(),
                        new ArrayList<>());
        final var expected = response(OK, transactionProcessingResult);

        given(accountStore.loadAccount(any())).willReturn(new Account(callerID));
        given(accountStore.loadContract(contractID)).willReturn(new Account(contractID));
        given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any()))
                .willReturn(transactionProcessingResult);

        // when:
        final var result =
                CallLocalExecutor.execute(
                        accountStore, evmTxProcessor, query, aliasManager, entityAccess);

        // then:
        assertEquals(expected, result);
    }

    @Test
    void processingSuccessful() {
        // setup:
        final var transactionProcessingResult =
                TransactionProcessingResult.successful(
                        new ArrayList<>(),
                        0,
                        0,
                        1,
                        Bytes.EMPTY,
                        callerID.asEvmAddress(),
                        Collections.emptyMap(),
                        new ArrayList<>());
        final var expected = response(OK, transactionProcessingResult);

        given(accountStore.loadAccount(any())).willReturn(new Account(callerID));
        given(accountStore.loadContract(any())).willReturn(new Account(contractID));
        given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any()))
                .willReturn(transactionProcessingResult);

        // when:
        final var result =
                CallLocalExecutor.execute(
                        accountStore, evmTxProcessor, query, aliasManager, entityAccess);

        // then:
        assertEquals(expected, result);
    }

    @Test
    void processingSuccessfulCallingToken() {
        // setup:
        final var transactionProcessingResult =
                TransactionProcessingResult.successful(
                        new ArrayList<>(),
                        0,
                        0,
                        1,
                        Bytes.EMPTY,
                        callerID.asEvmAddress(),
                        Collections.emptyMap(),
                        new ArrayList<>());
        final var expected = response(OK, transactionProcessingResult);

        given(entityAccess.isTokenAccount(any())).willReturn(true);
        given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any()))
                .willReturn(transactionProcessingResult);

        // when:
        final var result =
                CallLocalExecutor.execute(
                        accountStore, evmTxProcessor, query, aliasManager, entityAccess);

        // then:
        assertEquals(expected, result);
    }

    @Test
    void processingReturnsModificationHaltReason() {
        // setup:
        final var transactionProcessingResult =
                TransactionProcessingResult.failed(
                        0,
                        0,
                        1,
                        Optional.empty(),
                        Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE),
                        Collections.emptyMap(),
                        Collections.emptyList());
        final var expected =
                response(LOCAL_CALL_MODIFICATION_EXCEPTION, transactionProcessingResult);

        given(accountStore.loadAccount(any())).willReturn(new Account(callerID));
        given(accountStore.loadContract(any())).willReturn(new Account(contractID));
        given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any()))
                .willReturn(transactionProcessingResult);

        // when:
        final var result =
                CallLocalExecutor.execute(
                        accountStore, evmTxProcessor, query, aliasManager, entityAccess);

        // then:
        assertEquals(expected, result);
    }

    @Test
    void processingReturnsInvalidSolidityAddressHaltReason() {
        // setup:
        final var transactionProcessingResult =
                TransactionProcessingResult.failed(
                        0,
                        0,
                        1,
                        Optional.empty(),
                        Optional.of(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS),
                        Collections.emptyMap(),
                        Collections.emptyList());
        final var expected = response(INVALID_SOLIDITY_ADDRESS, transactionProcessingResult);

        given(accountStore.loadAccount(any())).willReturn(new Account(callerID));
        given(accountStore.loadContract(any())).willReturn(new Account(contractID));
        given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any()))
                .willReturn(transactionProcessingResult);

        // when:
        final var result =
                CallLocalExecutor.execute(
                        accountStore, evmTxProcessor, query, aliasManager, entityAccess);

        // then:
        assertEquals(expected, result);
    }

    @Test
    void processingReturnsRevertReason() {
        // setup:
        final var transactionProcessingResult =
                TransactionProcessingResult.failed(
                        0,
                        0,
                        1,
                        Optional.of(Bytes.of("out of gas".getBytes())),
                        Optional.empty(),
                        Collections.emptyMap(),
                        Collections.emptyList());
        final var expected = response(CONTRACT_REVERT_EXECUTED, transactionProcessingResult);

        given(accountStore.loadAccount(any())).willReturn(new Account(callerID));
        given(accountStore.loadContract(any())).willReturn(new Account(contractID));
        given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any()))
                .willReturn(transactionProcessingResult);

        // when:
        final var result =
                CallLocalExecutor.execute(
                        accountStore, evmTxProcessor, query, aliasManager, entityAccess);

        // then:
        assertEquals(expected, result);
    }

    @Test
    void catchesInvalidTransactionException() {
        // setup:
        given(accountStore.loadAccount(any()))
                .willThrow(new InvalidTransactionException(INVALID_ACCOUNT_ID));

        // when:
        final var result =
                CallLocalExecutor.execute(
                        accountStore, evmTxProcessor, query, aliasManager, entityAccess);

        assertEquals(failedResponse(INVALID_ACCOUNT_ID), result);
        // and:
        verifyNoInteractions(evmTxProcessor);
    }

    private ContractCallLocalResponse response(
            ResponseCodeEnum status, TransactionProcessingResult result) {
        return ContractCallLocalResponse.newBuilder()
                .setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(status))
                .setFunctionResult(result.toGrpc())
                .build();
    }

    private ContractCallLocalResponse failedResponse(ResponseCodeEnum status) {
        return ContractCallLocalResponse.newBuilder()
                .setHeader(
                        RequestBuilder.getResponseHeader(status, 0l, ANSWER_ONLY, ByteString.EMPTY))
                .build();
    }

    private ContractCallLocalQuery localCallQuery(ContractID id, ResponseType type) {
        return ContractCallLocalQuery.newBuilder()
                .setContractID(id)
                .setGas(gas)
                .setFunctionParameters(params)
                .setHeader(QueryHeader.newBuilder().setResponseType(type).build())
                .build();
    }

    private ContractCallLocalQuery localCallQuery(
            ContractID id, AccountID sender, ResponseType type) {
        return ContractCallLocalQuery.newBuilder()
                .setContractID(id)
                .setGas(gas)
                .setFunctionParameters(params)
                .setHeader(QueryHeader.newBuilder().setResponseType(type).build())
                .setSenderId(sender)
                .build();
    }
}
