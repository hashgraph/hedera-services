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
package com.hedera.services.queries.consensus;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.services.config.NetworkInfo;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.topics.TopicFactory;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoQuery;
import com.hederahashgraph.api.proto.java.ConsensusGetTopicInfoResponse;
import com.hederahashgraph.api.proto.java.ConsensusTopicInfo;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetMerkleTopicInfoAnswerTest {
    long seqNo = 1_234L;
    MerkleMap topics;
    byte[] hash = "NOT A HASH".getBytes();
    StateView view;
    OptionValidator optionValidator;
    Key adminKey, submitKey;
    long fee = 1_234L;
    String id = "1.2.3";
    String node = "0.0.3";
    String payer = "0.0.12345";
    String target = "3.2.1";
    String memo = "This was Mr. Bleaney's room...";
    String idLit = "0.0.12345";
    EntityNum key = EntityNum.fromTopicId(asTopic(target));
    long expiry = 1_234_567L;
    long duration = 55L;
    private final ByteString ledgerId = ByteString.copyFromUtf8("0x03");
    MerkleTopic merkleTopic;
    private Transaction paymentTxn;

    GetTopicInfoAnswer subject;
    NodeLocalProperties nodeProps;
    NetworkInfo networkInfo;

    @BeforeEach
    void setup() throws Exception {
        adminKey = COMPLEX_KEY_ACCOUNT_KT.asKey();
        submitKey = MISC_ACCOUNT_KT.asKey();
        topics = mock(MerkleMap.class);
        merkleTopic =
                TopicFactory.newTopic()
                        .adminKey(adminKey)
                        .submitKey(submitKey)
                        .memo(memo)
                        .expiry(expiry)
                        .deleted(false)
                        .autoRenewDuration(duration)
                        .autoRenewId(asAccount(id))
                        .get();
        merkleTopic.setRunningHash(hash);
        merkleTopic.setSequenceNumber(seqNo);
        given(topics.get(key)).willReturn(merkleTopic);

        nodeProps = mock(NodeLocalProperties.class);
        final MutableStateChildren children = new MutableStateChildren();
        children.setTopics(topics);

        networkInfo = mock(NetworkInfo.class);
        given(networkInfo.ledgerId()).willReturn(ledgerId);

        view = new StateView(null, children, networkInfo);
        optionValidator = mock(OptionValidator.class);

        subject = new GetTopicInfoAnswer(optionValidator);
    }

    @Test
    void syntaxCheckRequiresId() {
        // given:
        ConsensusGetTopicInfoQuery op = ConsensusGetTopicInfoQuery.newBuilder().build();
        Query query = Query.newBuilder().setConsensusGetTopicInfo(op).build();

        // when:
        ResponseCodeEnum status = subject.checkValidity(query, view);

        // expect:
        assertEquals(INVALID_TOPIC_ID, status);
    }

    @Test
    void requiresOkMetaValidity() {
        // setup:
        TopicID id = asTopic(idLit);

        // given:
        ConsensusGetTopicInfoQuery op =
                ConsensusGetTopicInfoQuery.newBuilder().setTopicID(id).build();
        Query query = Query.newBuilder().setConsensusGetTopicInfo(op).build();

        // when:
        Response response = subject.responseGiven(query, view, PLATFORM_NOT_ACTIVE);
        ResponseCodeEnum status =
                response.getConsensusGetTopicInfo().getHeader().getNodeTransactionPrecheckCode();

        // expect:
        assertEquals(PLATFORM_NOT_ACTIVE, status);
        assertEquals(id, response.getConsensusGetTopicInfo().getTopicID());
    }

    @Test
    void syntaxCheckValidatesTidIfPresent() {
        // setup:
        TopicID tid = asTopic(idLit);

        // given:
        ConsensusGetTopicInfoQuery op =
                ConsensusGetTopicInfoQuery.newBuilder().setTopicID(tid).build();
        Query query = Query.newBuilder().setConsensusGetTopicInfo(op).build();
        // and:
        given(optionValidator.queryableTopicStatus(tid, topics)).willReturn(TOPIC_EXPIRED);

        // when:
        ResponseCodeEnum status = subject.checkValidity(query, view);

        // expect:
        assertEquals(TOPIC_EXPIRED, status);
    }

    @Test
    void getsCostAnswerResponse() throws Throwable {
        // setup:
        Query query = validQuery(COST_ANSWER, fee, target);

        // when:
        Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasConsensusGetTopicInfo());
        assertEquals(
                OK,
                response.getConsensusGetTopicInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(
                COST_ANSWER, response.getConsensusGetTopicInfo().getHeader().getResponseType());
        assertEquals(fee, response.getConsensusGetTopicInfo().getHeader().getCost());
    }

    @Test
    void getsValidity() {
        // given:
        Response response =
                Response.newBuilder()
                        .setConsensusGetTopicInfo(
                                ConsensusGetTopicInfoResponse.newBuilder()
                                        .setHeader(
                                                ResponseHeader.newBuilder()
                                                        .setNodeTransactionPrecheckCode(
                                                                TOPIC_EXPIRED)))
                        .build();

        // expect:
        assertEquals(TOPIC_EXPIRED, subject.extractValidityFrom(response));
    }

    @Test
    void recognizesFunction() {
        // expect:
        assertEquals(HederaFunctionality.ConsensusGetTopicInfo, subject.canonicalFunction());
    }

    @Test
    void requiresAnswerOnlyCostAsExpected() throws Throwable {
        // expect:
        assertTrue(subject.needsAnswerOnlyCost(validQuery(COST_ANSWER, 0, target)));
        assertFalse(subject.needsAnswerOnlyCost(validQuery(ANSWER_ONLY, 0, target)));
    }

    @Test
    void requiresAnswerOnlyPayment() throws Throwable {
        // expect:
        assertFalse(subject.requiresNodePayment(validQuery(COST_ANSWER, 0, target)));
        assertTrue(subject.requiresNodePayment(validQuery(ANSWER_ONLY, 0, target)));
    }

    @Test
    void getsExpectedPayment() throws Throwable {
        // given:
        Query query = validQuery(COST_ANSWER, fee, target);

        // expect:
        assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxnWrapper());
    }

    @Test
    void getsTheTopicInfo() throws Throwable {
        // setup:
        Query query = validQuery(ANSWER_ONLY, fee, target);

        // when:
        Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasConsensusGetTopicInfo());
        assertEquals(
                OK,
                response.getConsensusGetTopicInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(
                ANSWER_ONLY, response.getConsensusGetTopicInfo().getHeader().getResponseType());
        assertEquals(0, response.getConsensusGetTopicInfo().getHeader().getCost());
        assertEquals(asTopic(target), response.getConsensusGetTopicInfo().getTopicID());
        // and:
        ConsensusTopicInfo info = response.getConsensusGetTopicInfo().getTopicInfo();
        assertEquals(adminKey, info.getAdminKey());
        assertEquals(submitKey, info.getSubmitKey());
        assertEquals(
                merkleTopic.getExpirationTimestamp().getSeconds(),
                info.getExpirationTime().getSeconds());
        assertEquals(
                merkleTopic.getAutoRenewDurationSeconds(), info.getAutoRenewPeriod().getSeconds());
        assertEquals(ByteString.copyFrom(merkleTopic.getRunningHash()), info.getRunningHash());
        assertEquals(
                merkleTopic.getAutoRenewAccountId().num(),
                info.getAutoRenewAccount().getAccountNum());
        assertEquals(merkleTopic.getSequenceNumber(), info.getSequenceNumber());
        assertEquals(merkleTopic.getMemo(), info.getMemo());
        assertEquals(ledgerId, info.getLedgerId());
    }

    @Test
    void failsAsExpectedWhenFetchingMissingTopicInfo() throws Throwable {
        given(topics.get(key)).willReturn(null);
        Query query = validQuery(ANSWER_ONLY, fee, target);

        Response response = subject.responseGiven(query, view, OK, fee);

        assertTrue(response.hasConsensusGetTopicInfo());
        assertEquals(
                INVALID_TOPIC_ID,
                response.getConsensusGetTopicInfo().getHeader().getNodeTransactionPrecheckCode());
    }

    @Test
    void getsTopicInfoWithEmptyRunningHash() throws Throwable {
        // setup:
        Query query = validQuery(ANSWER_ONLY, fee, target);
        merkleTopic.setRunningHash(null);
        given(topics.get(asTopic(target))).willReturn(merkleTopic);

        // when:
        Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasConsensusGetTopicInfo());
        // and:
        ConsensusTopicInfo info = response.getConsensusGetTopicInfo().getTopicInfo();
        assertArrayEquals(new byte[48], info.getRunningHash().toByteArray());
    }

    private Query validQuery(ResponseType type, long payment, String idLit) throws Throwable {
        this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
        QueryHeader.Builder header =
                QueryHeader.newBuilder().setPayment(this.paymentTxn).setResponseType(type);
        ConsensusGetTopicInfoQuery.Builder op =
                ConsensusGetTopicInfoQuery.newBuilder()
                        .setHeader(header)
                        .setTopicID(asTopic(idLit));
        return Query.newBuilder().setConsensusGetTopicInfo(op).build();
    }
}
