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
package com.hedera.services.queries.crypto;

import static com.hedera.services.context.primitives.StateView.REMOVED_TOKEN;
import static com.hedera.services.utils.EntityIdUtils.asEvmAddress;
import static com.hedera.services.utils.EntityNumPair.fromAccountTokenRel;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static com.hedera.test.utils.IdUtils.tokenWith;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.services.config.NetworkInfo;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.submerkle.RawTokenRelationship;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoQuery;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetAccountInfoAnswerTest {
    private StateView view;
    @Mock private ScheduleStore scheduleStore;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;
    @Mock private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;
    @Mock private MerkleMap<EntityNum, MerkleToken> tokens;
    @Mock private OptionValidator optionValidator;
    @Mock private MerkleToken token;
    @Mock private MerkleToken deletedToken;
    @Mock private NetworkInfo networkInfo;
    @Mock private AliasManager aliasManager;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private RewardCalculator rewardCalculator;

    private final MutableStateChildren children = new MutableStateChildren();

    private final ByteString ledgerId = ByteString.copyFromUtf8("0xff");
    private final int maxTokensPerAccountInfo = 10;
    private final String node = "0.0.3";
    private final String memo = "When had I my own will?";
    private final String payer = "0.0.12345";
    private final AccountID payerId = IdUtils.asAccount(payer);
    private MerkleAccount payerAccount;
    private String target = payer;
    TokenID firstToken = tokenWith(555),
            secondToken = tokenWith(666),
            thirdToken = tokenWith(777),
            fourthToken = tokenWith(888),
            missingToken = tokenWith(999);
    long firstBalance = 123,
            secondBalance = 234,
            thirdBalance = 345,
            fourthBalance = 456,
            missingBalance = 567;

    private final long fee = 1_234L;
    private EntityNumPair firstRelKey = fromAccountTokenRel(payerId, firstToken);
    private Transaction paymentTxn;

    private GetAccountInfoAnswer subject;

    @BeforeEach
    void setup() throws Throwable {
        tokenRels = new MerkleMap<>();

        final var firstRel = new MerkleTokenRelStatus(firstBalance, true, true, true);
        firstRel.setKey(firstRelKey);
        final var secondRel = new MerkleTokenRelStatus(secondBalance, false, false, true);
        final var secondRelKey = fromAccountTokenRel(payerId, secondToken);
        secondRel.setKey(secondRelKey);
        final var thirdRel = new MerkleTokenRelStatus(thirdBalance, true, true, false);
        final var thirdRelKey = fromAccountTokenRel(payerId, thirdToken);
        thirdRel.setKey(thirdRelKey);
        final var fourthRel = new MerkleTokenRelStatus(fourthBalance, false, false, true);
        final var fourthRelKey = fromAccountTokenRel(payerId, fourthToken);
        fourthRel.setKey(fourthRelKey);
        final var missingRel = new MerkleTokenRelStatus(missingBalance, false, false, false);
        final var missingRelKey = fromAccountTokenRel(payerId, missingToken);
        missingRel.setKey(missingRelKey);

        firstRel.setNext(secondToken.getTokenNum());
        secondRel.setNext(thirdToken.getTokenNum());
        secondRel.setPrev(firstToken.getTokenNum());
        thirdRel.setPrev(secondToken.getTokenNum());
        thirdRel.setNext(fourthToken.getTokenNum());
        fourthRel.setPrev(thirdToken.getTokenNum());
        fourthRel.setNext(missingToken.getTokenNum());
        missingRel.setPrev(fourthToken.getTokenNum());

        tokenRels.put(firstRelKey, firstRel);
        tokenRels.put(secondRelKey, secondRel);
        tokenRels.put(thirdRelKey, thirdRel);
        tokenRels.put(fourthRelKey, fourthRel);
        tokenRels.put(missingRelKey, missingRel);

        var tokenAllowanceKey =
                FcTokenAllowanceId.from(EntityNum.fromLong(1000L), EntityNum.fromLong(2000L));
        TreeMap<EntityNum, Long> cryptoAllowances = new TreeMap();
        TreeMap<FcTokenAllowanceId, Long> fungibleTokenAllowances = new TreeMap();
        TreeSet<FcTokenAllowanceId> nftAllowances = new TreeSet<>();

        cryptoAllowances.put(EntityNum.fromLong(1L), 10L);
        fungibleTokenAllowances.put(tokenAllowanceKey, 20L);
        nftAllowances.add(tokenAllowanceKey);

        payerAccount =
                MerkleAccountFactory.newAccount()
                        .accountKeys(COMPLEX_KEY_ACCOUNT_KT)
                        .memo(memo)
                        .proxy(asAccount("1.2.3"))
                        .receiverSigRequired(true)
                        .balance(555L)
                        .autoRenewPeriod(1_000_000L)
                        .expirationTime(9_999_999L)
                        .cryptoAllowances(cryptoAllowances)
                        .fungibleTokenAllowances(fungibleTokenAllowances)
                        .explicitNftAllowances(nftAllowances)
                        .stakedId(-1L)
                        .stakePeriodStart(12345678L)
                        .declineReward(false)
                        .get();

        children.setAccounts(accounts);
        children.setTokenAssociations(tokenRels);
        children.setTokens(tokens);
        children.setStakingInfo(stakingInfo);

        view = new StateView(scheduleStore, children, networkInfo);

        subject =
                new GetAccountInfoAnswer(
                        optionValidator, aliasManager, dynamicProperties, rewardCalculator);
    }

    @Test
    void getsCostAnswerResponse() throws Throwable {
        // setup:
        Query query = validQuery(COST_ANSWER, fee, target);

        // when:
        Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasCryptoGetInfo());
        assertEquals(OK, response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(COST_ANSWER, response.getCryptoGetInfo().getHeader().getResponseType());
        assertEquals(fee, response.getCryptoGetInfo().getHeader().getCost());
    }

    @Test
    void getsInvalidResponse() throws Throwable {
        // setup:
        Query query = validQuery(COST_ANSWER, fee, target);

        // when:
        Response response = subject.responseGiven(query, view, ACCOUNT_DELETED, fee);

        // then:
        assertTrue(response.hasCryptoGetInfo());
        assertEquals(
                ACCOUNT_DELETED,
                response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(COST_ANSWER, response.getCryptoGetInfo().getHeader().getResponseType());
        assertEquals(fee, response.getCryptoGetInfo().getHeader().getCost());
    }

    @Test
    void identifiesFailInvalid() throws Throwable {
        given(dynamicProperties.maxTokensRelsPerInfoQuery()).willReturn(maxTokensPerAccountInfo);
        Query query = validQuery(ANSWER_ONLY, fee, target);
        // and:
        StateView view = mock(StateView.class);

        given(view.infoForAccount(any(), any(), anyInt(), any())).willReturn(Optional.empty());

        // when:
        Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasCryptoGetInfo());
        assertEquals(
                FAIL_INVALID,
                response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(ANSWER_ONLY, response.getCryptoGetInfo().getHeader().getResponseType());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getsTheAccountInfo() throws Throwable {
        given(dynamicProperties.maxTokensRelsPerInfoQuery()).willReturn(maxTokensPerAccountInfo);
        final MerkleMap<EntityNum, MerkleToken> tokens = mock(MerkleMap.class);
        children.setTokens(tokens);

        given(token.hasKycKey()).willReturn(true);
        given(token.hasFreezeKey()).willReturn(true);
        given(token.decimals())
                .willReturn(1)
                .willReturn(2)
                .willReturn(3)
                .willReturn(1)
                .willReturn(2)
                .willReturn(3);
        given(deletedToken.decimals()).willReturn(4);
        given(tokens.getOrDefault(EntityNum.fromTokenId(firstToken), REMOVED_TOKEN))
                .willReturn(token);
        given(tokens.getOrDefault(EntityNum.fromTokenId(secondToken), REMOVED_TOKEN))
                .willReturn(token);
        given(tokens.getOrDefault(EntityNum.fromTokenId(thirdToken), REMOVED_TOKEN))
                .willReturn(token);
        given(tokens.getOrDefault(EntityNum.fromTokenId(fourthToken), REMOVED_TOKEN))
                .willReturn(deletedToken);
        given(tokens.getOrDefault(EntityNum.fromTokenId(missingToken), REMOVED_TOKEN))
                .willReturn(REMOVED_TOKEN);
        payerAccount.setKey(EntityNum.fromAccountId(payerId));
        payerAccount.setHeadTokenId(firstToken.getTokenNum());

        given(token.symbol()).willReturn("HEYMA");
        given(deletedToken.symbol()).willReturn("THEWAY");
        given(accounts.get(EntityNum.fromAccountId(asAccount(target)))).willReturn(payerAccount);
        given(networkInfo.ledgerId()).willReturn(ledgerId);
        given(rewardCalculator.epochSecondAtStartOfPeriod(12345678L)).willReturn(12345678L);

        // setup:
        Query query = validQuery(ANSWER_ONLY, fee, target);

        // when:
        Response response = subject.responseGiven(query, view, OK, fee);

        // then:
        assertTrue(response.hasCryptoGetInfo());
        assertTrue(response.getCryptoGetInfo().hasHeader(), "Missing response header!");
        assertEquals(OK, response.getCryptoGetInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(ANSWER_ONLY, response.getCryptoGetInfo().getHeader().getResponseType());
        assertEquals(0, response.getCryptoGetInfo().getHeader().getCost());
        // and:
        CryptoGetInfoResponse.AccountInfo info = response.getCryptoGetInfo().getAccountInfo();
        assertEquals(asAccount(payer), info.getAccountID());
        String address = CommonUtils.hex(asEvmAddress(0, 0L, 12_345L));
        assertEquals(address, info.getContractAccountID());
        assertEquals(payerAccount.getBalance(), info.getBalance());
        assertEquals(payerAccount.getAutoRenewSecs(), info.getAutoRenewPeriod().getSeconds());
        assertEquals(payerAccount.getProxy(), EntityId.fromGrpcAccountId(info.getProxyAccountID()));
        assertEquals(JKey.mapJKey(payerAccount.getAccountKey()), info.getKey());
        assertEquals(payerAccount.isReceiverSigRequired(), info.getReceiverSigRequired());
        assertEquals(payerAccount.getExpiry(), info.getExpirationTime().getSeconds());
        assertEquals(memo, info.getMemo());

        assertEquals(0L, info.getStakingInfo().getStakedNodeId());
        assertFalse(info.getStakingInfo().hasStakedAccountId());
        assertFalse(info.getStakingInfo().getDeclineReward());
        assertEquals(12345678L, info.getStakingInfo().getStakePeriodStart().getSeconds());
        assertEquals(0L, info.getStakingInfo().getStakedToMe());

        // and:
        assertEquals(
                List.of(
                        new RawTokenRelationship(
                                        firstBalance,
                                        0,
                                        0,
                                        firstToken.getTokenNum(),
                                        true,
                                        true,
                                        true)
                                .asGrpcFor(token),
                        new RawTokenRelationship(
                                        secondBalance,
                                        0,
                                        0,
                                        secondToken.getTokenNum(),
                                        false,
                                        false,
                                        true)
                                .asGrpcFor(token),
                        new RawTokenRelationship(
                                        thirdBalance,
                                        0,
                                        0,
                                        thirdToken.getTokenNum(),
                                        true,
                                        true,
                                        false)
                                .asGrpcFor(token),
                        new RawTokenRelationship(
                                        fourthBalance,
                                        0,
                                        0,
                                        fourthToken.getTokenNum(),
                                        false,
                                        false,
                                        true)
                                .asGrpcFor(deletedToken),
                        new RawTokenRelationship(
                                        missingBalance,
                                        0,
                                        0,
                                        missingToken.getTokenNum(),
                                        false,
                                        false,
                                        false)
                                .asGrpcFor(REMOVED_TOKEN)),
                info.getTokenRelationshipsList());
    }

    @Test
    void usesValidator() throws Throwable {
        // setup:
        Query query = validQuery(COST_ANSWER, fee, target);

        given(optionValidator.queryableAccountStatus(EntityNum.fromAccountId(payerId), accounts))
                .willReturn(ACCOUNT_DELETED);

        // when:
        ResponseCodeEnum validity = subject.checkValidity(query, view);

        // then:
        assertEquals(ACCOUNT_DELETED, validity);
    }

    @Test
    void usesValidatorOnAccountWithAlias() throws Throwable {
        EntityNum entityNum = EntityNum.fromAccountId(payerId);
        Query query = validQueryWithAlias(COST_ANSWER, fee, "aaaa");

        given(aliasManager.lookupIdBy(any())).willReturn(entityNum);

        given(optionValidator.queryableAccountStatus(entityNum, accounts))
                .willReturn(INVALID_ACCOUNT_ID);

        ResponseCodeEnum validity = subject.checkValidity(query, view);
        assertEquals(INVALID_ACCOUNT_ID, validity);
    }

    @Test
    void getsExpectedPayment() throws Throwable {
        // given:
        Query query = validQuery(COST_ANSWER, fee, target);

        // expect:
        assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxnWrapper());
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
    void getsValidity() {
        // given:
        Response response =
                Response.newBuilder()
                        .setCryptoGetInfo(
                                CryptoGetInfoResponse.newBuilder()
                                        .setHeader(
                                                subject.answerOnlyHeader(
                                                        RESULT_SIZE_LIMIT_EXCEEDED)))
                        .build();

        // expect:
        assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
    }

    @Test
    void recognizesFunction() {
        // expect:
        assertEquals(CryptoGetInfo, subject.canonicalFunction());
    }

    private Query validQuery(ResponseType type, long payment, String idLit) throws Throwable {
        this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
        QueryHeader.Builder header =
                QueryHeader.newBuilder().setPayment(this.paymentTxn).setResponseType(type);
        CryptoGetInfoQuery.Builder op =
                CryptoGetInfoQuery.newBuilder().setHeader(header).setAccountID(asAccount(idLit));
        return Query.newBuilder().setCryptoGetInfo(op).build();
    }

    private Query validQueryWithAlias(ResponseType type, long payment, String alias)
            throws Throwable {
        this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
        QueryHeader.Builder header =
                QueryHeader.newBuilder().setPayment(this.paymentTxn).setResponseType(type);
        CryptoGetInfoQuery.Builder op =
                CryptoGetInfoQuery.newBuilder()
                        .setHeader(header)
                        .setAccountID(asAccountWithAlias(alias));
        return Query.newBuilder().setCryptoGetInfo(op).build();
    }
}
