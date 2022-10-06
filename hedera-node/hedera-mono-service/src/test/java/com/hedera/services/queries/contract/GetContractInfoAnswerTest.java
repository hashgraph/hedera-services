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
package com.hedera.services.queries.contract;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.never;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.StakingInfo;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetContractInfoAnswerTest {
    private Transaction paymentTxn;
    private final int maxTokenPerContractInfo = 10;
    private final String node = "0.0.3";
    private final String payer = "0.0.12345";
    private final String target = "0.0.123";
    private final long fee = 1_234L;
    private final ByteString ledgerId = ByteString.copyFromUtf8("0xff");

    @Mock private OptionValidator optionValidator;
    @Mock private AliasManager aliasManager;
    @Mock private StateView view;
    @Mock private MerkleMap<EntityNum, MerkleAccount> contracts;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private RewardCalculator rewardCalculator;

    ContractGetInfoResponse.ContractInfo info;

    GetContractInfoAnswer subject;

    @BeforeEach
    void setup() {
        info =
                ContractGetInfoResponse.ContractInfo.newBuilder()
                        .setLedgerId(ledgerId)
                        .setContractID(asContract(target))
                        .setContractAccountID(
                                EntityIdUtils.asHexedEvmAddress(IdUtils.asAccount(target)))
                        .setMemo("Stay cold...")
                        .setAdminKey(COMPLEX_KEY_ACCOUNT_KT.asKey())
                        .setStakingInfo(
                                StakingInfo.newBuilder()
                                        .setStakedAccountId(asAccount("0.0.2"))
                                        .setStakePeriodStart(
                                                com.hederahashgraph.api.proto.java.Timestamp
                                                        .newBuilder()
                                                        .setSeconds(12345678L)
                                                        .build())
                                        .setDeclineReward(true)
                                        .build())
                        .build();

        subject =
                new GetContractInfoAnswer(
                        aliasManager, optionValidator, dynamicProperties, rewardCalculator);
    }

    @Test
    void getsTheInfo() throws Throwable {
        given(dynamicProperties.maxTokensRelsPerInfoQuery()).willReturn(maxTokenPerContractInfo);
        Query query = validQuery(ANSWER_ONLY, fee, target);

        given(
                        view.infoForContract(
                                asContract(target),
                                aliasManager,
                                maxTokenPerContractInfo,
                                rewardCalculator))
                .willReturn(Optional.of(info));

        // when:
        Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasContractGetInfo());
        assertTrue(response.getContractGetInfo().hasHeader(), "Missing response header!");
        assertEquals(
                OK, response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(ANSWER_ONLY, response.getContractGetInfo().getHeader().getResponseType());
        assertEquals(fee, response.getContractGetInfo().getHeader().getCost());

        assertEquals(0L, info.getStakingInfo().getStakedNodeId());
        assertTrue(info.getStakingInfo().hasStakedAccountId());
        assertTrue(info.getStakingInfo().getDeclineReward());
        assertEquals(12345678L, info.getStakingInfo().getStakePeriodStart().getSeconds());
        assertEquals(0L, info.getStakingInfo().getStakedToMe());

        // and:
        var actual = response.getContractGetInfo().getContractInfo();
        assertEquals(info, actual);
    }

    @Test
    void getsInfoFromCtxWhenAvailable() throws Throwable {
        // setup:
        Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, target);
        Map<String, Object> ctx = new HashMap<>();

        // given:
        ctx.put(GetContractInfoAnswer.CONTRACT_INFO_CTX_KEY, info);

        // when:
        Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

        // then:
        var opResponse = response.getContractGetInfo();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
        assertSame(info, opResponse.getContractInfo());
        // and:
        verify(view, never()).infoForContract(any(), any(), anyInt(), any());
    }

    @Test
    void recognizesMissingInfoWhenNoCtxGiven() throws Throwable {
        given(dynamicProperties.maxTokensRelsPerInfoQuery()).willReturn(maxTokenPerContractInfo);
        Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, target);

        given(
                        view.infoForContract(
                                asContract(target),
                                aliasManager,
                                maxTokenPerContractInfo,
                                rewardCalculator))
                .willReturn(Optional.empty());

        // when:
        Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

        // then:
        ContractGetInfoResponse opResponse = response.getContractGetInfo();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(INVALID_CONTRACT_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
    }

    @Test
    void recognizesMissingInfoWhenCtxGiven() throws Throwable {
        // setup:
        Query sensibleQuery = validQuery(ANSWER_ONLY, 5L, target);

        // when:
        Response response =
                subject.responseGiven(sensibleQuery, view, OK, 0L, Collections.emptyMap());

        // then:
        ContractGetInfoResponse opResponse = response.getContractGetInfo();
        assertTrue(opResponse.hasHeader(), "Missing response header!");
        assertEquals(INVALID_CONTRACT_ID, opResponse.getHeader().getNodeTransactionPrecheckCode());
        verify(view, never()).infoForContract(any(), any(), anyInt(), any());
    }

    @Test
    void getsCostAnswerResponse() throws Throwable {
        // setup:
        Query query = validQuery(COST_ANSWER, fee, target);

        // when:
        Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasContractGetInfo());
        assertEquals(
                OK, response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(COST_ANSWER, response.getContractGetInfo().getHeader().getResponseType());
        assertEquals(fee, response.getContractGetInfo().getHeader().getCost());
    }

    @Test
    void getsInvalidResponse() throws Throwable {
        // setup:
        Query query = validQuery(COST_ANSWER, fee, target);

        // when:
        Response response = subject.responseGiven(query, view, CONTRACT_DELETED, fee);

        // then:
        assertTrue(response.hasContractGetInfo());
        assertEquals(
                CONTRACT_DELETED,
                response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(COST_ANSWER, response.getContractGetInfo().getHeader().getResponseType());
        assertEquals(fee, response.getContractGetInfo().getHeader().getCost());
    }

    @Test
    void recognizesFunction() {
        // expect:
        assertEquals(HederaFunctionality.ContractGetInfo, subject.canonicalFunction());
    }

    @Test
    void requiresAnswerOnlyPayment() throws Throwable {
        // expect:
        assertFalse(subject.requiresNodePayment(validQuery(COST_ANSWER, 0, target)));
        assertTrue(subject.requiresNodePayment(validQuery(ANSWER_ONLY, 0, target)));
    }

    @Test
    void requiresAnswerOnlyCostAsExpected() throws Throwable {
        // expect:
        assertTrue(subject.needsAnswerOnlyCost(validQuery(COST_ANSWER, 0, target)));
        assertFalse(subject.needsAnswerOnlyCost(validQuery(ANSWER_ONLY, 0, target)));
    }

    @Test
    void getsValidity() {
        // given:
        Response response =
                Response.newBuilder()
                        .setContractGetInfo(
                                ContractGetInfoResponse.newBuilder()
                                        .setHeader(
                                                subject.answerOnlyHeader(
                                                        RESULT_SIZE_LIMIT_EXCEEDED)))
                        .build();

        // expect:
        assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
    }

    @Test
    void returnsInvalidContractIdFromValidator() throws Throwable {
        // setup:
        Query query = validQuery(COST_ANSWER, fee, target);

        given(optionValidator.queryableContractStatus(asContract(target), contracts))
                .willReturn(INVALID_CONTRACT_ID);
        given(view.contracts()).willReturn(contracts);

        // when:
        ResponseCodeEnum validity = subject.checkValidity(query, view);

        // then:
        assertEquals(INVALID_CONTRACT_ID, validity);
    }

    @Test
    void allowsQueryingDeletedContracts() throws Throwable {
        Query query = validQuery(COST_ANSWER, fee, target);

        given(optionValidator.queryableContractStatus(asContract(target), contracts))
                .willReturn(CONTRACT_DELETED);
        given(view.contracts()).willReturn(contracts);

        ResponseCodeEnum validity = subject.checkValidity(query, view);

        assertEquals(OK, validity);
    }

    @Test
    void getsExpectedPayment() throws Throwable {
        // given:
        Query query = validQuery(COST_ANSWER, fee, target);

        // expect:
        assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxnWrapper());
    }

    private Query validQuery(ResponseType type, long payment, String idLit) throws Throwable {
        this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
        QueryHeader.Builder header =
                QueryHeader.newBuilder().setPayment(this.paymentTxn).setResponseType(type);
        ContractGetInfoQuery.Builder op =
                ContractGetInfoQuery.newBuilder()
                        .setHeader(header)
                        .setContractID(asContract(idLit));
        return Query.newBuilder().setContractGetInfo(op).build();
    }
}
