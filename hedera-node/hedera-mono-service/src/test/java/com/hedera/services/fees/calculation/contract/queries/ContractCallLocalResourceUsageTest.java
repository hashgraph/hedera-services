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
package com.hedera.services.fees.calculation.contract.queries;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.contracts.execution.BlockMetaSource;
import com.hedera.services.contracts.execution.CallLocalEvmTxProcessor;
import com.hedera.services.contracts.execution.StaticBlockMetaProvider;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.queries.contract.ContractCallLocalAnswer;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({LogCaptureExtension.class, MockitoExtension.class})
class ContractCallLocalResourceUsageTest {
    private static final int gas = 1_234;
    private static final ByteString params = ByteString.copyFrom("Hungry, and...".getBytes());

    private static final Id callerID = new Id(0, 0, 123);
    private static final Id contractID = new Id(0, 0, 456);
    private static final ContractID target = contractID.asGrpcContract();

    private static final Query satisfiableCostAnswer = localCallQuery(target, COST_ANSWER);
    private static final Query satisfiableAnswerOnly = localCallQuery(target, ANSWER_ONLY);
    private static final GlobalDynamicProperties properties = new MockGlobalDynamicProps();

    @Mock private AccountStore accountStore;
    @Mock private StateView view;
    @Mock private SmartContractFeeBuilder usageEstimator;
    @Mock private CallLocalEvmTxProcessor evmTxProcessor;
    @Mock private EntityIdSource ids;
    @Mock private OptionValidator validator;
    @Mock private NodeLocalProperties nodeLocalProperties;
    @Mock private AliasManager aliasManager;
    @Mock private BlockMetaSource blockMetaSource;
    @Mock private StaticBlockMetaProvider blockMetaProvider;

    @LoggingTarget private LogCaptor logCaptor;

    @LoggingSubject private ContractCallLocalResourceUsage subject;

    @BeforeEach
    void setup() {
        subject =
                new ContractCallLocalResourceUsage(
                        usageEstimator,
                        properties,
                        nodeLocalProperties,
                        accountStore,
                        evmTxProcessor,
                        ids,
                        validator,
                        aliasManager,
                        blockMetaProvider);
    }

    @Test
    void recognizesApplicableQuery() {
        final var applicable = localCallQuery(target, COST_ANSWER);
        final var inapplicable = Query.getDefaultInstance();

        assertTrue(subject.applicableTo(applicable));
        assertFalse(subject.applicableTo(inapplicable));
    }

    @Test
    void setsResultInQueryCxtIfPresent() {
        final var queryCtx = new HashMap<String, Object>();
        final var transactionProcessingResult =
                TransactionProcessingResult.successful(
                        new ArrayList<>(),
                        0,
                        0,
                        1,
                        Bytes.EMPTY,
                        callerID.asEvmAddress(),
                        Collections.emptyMap(),
                        Collections.emptyList());
        final var response = okResponse(transactionProcessingResult);
        final var estimateResponse = subject.dummyResponse(target);
        final var expected = expectedUsage();

        given(accountStore.loadAccount(any())).willReturn(new Account(Id.fromGrpcContract(target)));
        given(accountStore.loadContract(any()))
                .willReturn(new Account(Id.fromGrpcContract(target)));
        given(evmTxProcessor.execute(any(), any(), anyLong(), anyLong(), any()))
                .willReturn(transactionProcessingResult);
        given(
                        usageEstimator.getContractCallLocalFeeMatrices(
                                params.size(), response.getFunctionResult(), ANSWER_ONLY))
                .willReturn(nonGasUsage);
        given(
                        usageEstimator.getContractCallLocalFeeMatrices(
                                params.size(), estimateResponse.getFunctionResult(), ANSWER_ONLY))
                .willReturn(nonGasUsage);
        given(blockMetaProvider.getSource()).willReturn(Optional.of(blockMetaSource));

        final var actualUsage1 = subject.usageGiven(satisfiableAnswerOnly, view);
        final var actualUsage2 = subject.usageGivenType(satisfiableAnswerOnly, view, ANSWER_ONLY);
        final var actualUsage3 = subject.usageGiven(satisfiableAnswerOnly, view, queryCtx);

        assertEquals(response, queryCtx.get(ContractCallLocalAnswer.CONTRACT_CALL_LOCAL_CTX_KEY));
        assertEquals(expected, actualUsage1);
        assertEquals(expected, actualUsage2);
        assertEquals(expected, actualUsage3);
    }

    @Test
    void treatsAnswerOnlyEstimateAsExpected() {
        final var response = subject.dummyResponse(target);
        final var expected = expectedUsage();
        given(
                        usageEstimator.getContractCallLocalFeeMatrices(
                                params.size(), response.getFunctionResult(), ANSWER_ONLY))
                .willReturn(nonGasUsage);

        final var actualUsage = subject.usageGivenType(satisfiableCostAnswer, view, ANSWER_ONLY);

        assertEquals(expected, actualUsage);
        verifyNoInteractions(evmTxProcessor);
    }

    @Test
    void translatesExecutionException() {
        final var queryCtx = new HashMap<String, Object>();

        assertThrows(
                IllegalStateException.class,
                () -> subject.usageGiven(satisfiableAnswerOnly, view, queryCtx));
        assertFalse(queryCtx.containsKey(ContractCallLocalAnswer.CONTRACT_CALL_LOCAL_CTX_KEY));
        assertThat(
                logCaptor.warnLogs(),
                contains(startsWith("Usage estimation unexpectedly failed for")));
    }

    @Test
    void dummyResponseAsExpected() {
        final var dummy = subject.dummyResponse(target);

        assertEquals(OK, dummy.getHeader().getNodeTransactionPrecheckCode());
        assertEquals(target, dummy.getFunctionResult().getContractID());
        assertEquals(
                properties.localCallEstRetBytes(),
                dummy.getFunctionResult().getContractCallResult().size());
    }

    private static Query localCallQuery(final ContractID id, final ResponseType type) {
        final var op =
                ContractCallLocalQuery.newBuilder()
                        .setContractID(id)
                        .setGas(gas)
                        .setFunctionParameters(params)
                        .setHeader(QueryHeader.newBuilder().setResponseType(type).build());
        return Query.newBuilder().setContractCallLocal(op).build();
    }

    private ContractCallLocalResponse okResponse(TransactionProcessingResult result) {
        return response(result);
    }

    private ContractCallLocalResponse response(final TransactionProcessingResult result) {
        return ContractCallLocalResponse.newBuilder()
                .setHeader(
                        ResponseHeader.newBuilder()
                                .setNodeTransactionPrecheckCode(ResponseCodeEnum.OK))
                .setFunctionResult(result.toGrpc())
                .build();
    }

    private static final FeeData nonGasUsage =
            FeeData.newBuilder()
                    .setNodedata(
                            FeeComponents.newBuilder()
                                    .setMin(1)
                                    .setMax(1_000_000)
                                    .setConstant(1)
                                    .setBpt(1)
                                    .setVpt(1)
                                    .setRbh(1)
                                    .setSbh(1)
                                    .setGas(0)
                                    .setTv(1)
                                    .setBpr(1)
                                    .setSbpr(1))
                    .build();

    private static final FeeData expectedUsage() {
        return nonGasUsage.toBuilder()
                .setNodedata(nonGasUsage.toBuilder().getNodedataBuilder().setGas(gas).build())
                .build();
    }
}
