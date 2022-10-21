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

import static com.hedera.services.utils.EntityNumPair.fromAccountTokenRel;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.tokenBalanceWith;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetAccountBalanceAnswerTest {
    @Mock private AccountStorageAdapter accounts;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private OptionValidator optionValidator;
    @Mock private AliasManager aliasManager;

    private GetAccountBalanceAnswer subject;

    @BeforeEach
    void setup() {
        subject = new GetAccountBalanceAnswer(aliasManager, optionValidator, dynamicProperties);
    }

    @Test
    void requiresNothing() {
        // setup:
        CryptoGetAccountBalanceQuery costAnswerOp =
                CryptoGetAccountBalanceQuery.newBuilder()
                        .setHeader(
                                QueryHeader.newBuilder().setResponseType(ResponseType.COST_ANSWER))
                        .build();
        Query costAnswerQuery = Query.newBuilder().setCryptogetAccountBalance(costAnswerOp).build();
        CryptoGetAccountBalanceQuery answerOnlyOp =
                CryptoGetAccountBalanceQuery.newBuilder()
                        .setHeader(
                                QueryHeader.newBuilder().setResponseType(ResponseType.ANSWER_ONLY))
                        .build();
        Query answerOnlyQuery = Query.newBuilder().setCryptogetAccountBalance(answerOnlyOp).build();

        // expect:
        assertFalse(subject.requiresNodePayment(costAnswerQuery));
        assertFalse(subject.requiresNodePayment(answerOnlyQuery));
        assertFalse(subject.needsAnswerOnlyCost(answerOnlyQuery));
        assertFalse(subject.needsAnswerOnlyCost(costAnswerQuery));
    }

    @Test
    void hasNoPayment() {
        // expect:
        assertFalse(subject.extractPaymentFrom(mock(Query.class)).isPresent());
    }

    @Test
    void syntaxCheckRequiresId() {
        // given:
        CryptoGetAccountBalanceQuery op = CryptoGetAccountBalanceQuery.newBuilder().build();
        Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();

        // when:
        ResponseCodeEnum status = subject.checkValidity(query, wellKnownView());

        // expect:
        assertEquals(INVALID_ACCOUNT_ID, status);
    }

    @Test
    void syntaxCheckValidatesCidIfPresent() {
        String contractIdLit = "0.0.12346";
        ContractID cid = asContract(contractIdLit);
        given(optionValidator.queryableContractStatus(cid, accounts)).willReturn(CONTRACT_DELETED);

        final var query = contractQueryWith(cid);
        final var status = subject.checkValidity(query, wellKnownView());

        assertEquals(CONTRACT_DELETED, status);
    }

    @Test
    void syntaxCheckValidatesAliasedCidIfPresent() {
        final var resolvedId = EntityNum.fromLong(666);
        given(aliasManager.lookupIdBy(evmAddress)).willReturn(resolvedId);
        given(optionValidator.queryableContractStatus(resolvedId.toGrpcContractID(), accounts))
                .willReturn(CONTRACT_DELETED);

        final var query = contractQueryWith(aliasContractId);
        final var status = subject.checkValidity(query, wellKnownView());

        assertEquals(CONTRACT_DELETED, status);
    }

    @Test
    void getsValidity() {
        // given:
        Response response =
                Response.newBuilder()
                        .setCryptogetAccountBalance(
                                CryptoGetAccountBalanceResponse.newBuilder()
                                        .setHeader(
                                                subject.answerOnlyHeader(
                                                        RESULT_SIZE_LIMIT_EXCEEDED)))
                        .build();

        // expect:
        assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
    }

    @Test
    void requiresOkMetaValidity() {
        // setup:
        AccountID id = asAccount(accountIdLit);

        // given:
        CryptoGetAccountBalanceQuery op =
                CryptoGetAccountBalanceQuery.newBuilder().setAccountID(id).build();
        Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();

        // when:
        Response response = subject.responseGiven(query, wellKnownView(), PLATFORM_NOT_ACTIVE);
        ResponseCodeEnum status =
                response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode();

        // expect:
        assertEquals(PLATFORM_NOT_ACTIVE, status);
        assertEquals(id, response.getCryptogetAccountBalance().getAccountID());
    }

    @Test
    void syntaxCheckValidatesIdIfPresent() {
        // setup:
        AccountID id = asAccount(accountIdLit);

        // given:
        CryptoGetAccountBalanceQuery op =
                CryptoGetAccountBalanceQuery.newBuilder().setAccountID(id).build();
        Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();
        // and:
        given(optionValidator.queryableAccountStatus(id, accounts)).willReturn(ACCOUNT_DELETED);

        // when:
        ResponseCodeEnum status = subject.checkValidity(query, wellKnownView());

        // expect:
        assertEquals(ACCOUNT_DELETED, status);
    }

    @Test
    void resolvesContractAliasIfExtant() {
        final var aliasedContractId = ContractID.newBuilder().setEvmAddress(evmAddress).build();
        final var wellKnownId = EntityNum.fromLong(12345L);
        given(aliasManager.lookupIdBy(aliasedContractId.getEvmAddress())).willReturn(wellKnownId);
        given(accounts.get(wellKnownId)).willReturn(accountV);

        final var query = contractQueryWith(aliasedContractId);

        final var response = subject.responseGiven(query, wellKnownView(), OK);

        assertEquals(OK, statusFrom(response));
        assertEquals(balance, balanceFrom(response));
        assertEquals(
                wellKnownId.toGrpcAccountId(),
                response.getCryptogetAccountBalance().getAccountID());
    }

    @Test
    void resolvesAliasIfExtant() {
        final var aliasId =
                AccountID.newBuilder().setAlias(ByteString.copyFromUtf8("nope")).build();
        final var wellKnownId = EntityNum.fromLong(12345L);
        given(aliasManager.lookupIdBy(aliasId.getAlias())).willReturn(wellKnownId);
        accountV.setKey(EntityNum.fromAccountId(asAccount(accountIdLit)));
        given(accounts.get(wellKnownId)).willReturn(accountV);
        given(dynamicProperties.maxTokensRelsPerInfoQuery()).willReturn(maxTokenRels);

        CryptoGetAccountBalanceQuery op =
                CryptoGetAccountBalanceQuery.newBuilder().setAccountID(aliasId).build();
        Query query = Query.newBuilder().setCryptogetAccountBalance(op).build();

        Response response = subject.responseGiven(query, wellKnownView(), OK);
        ResponseCodeEnum status =
                response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode();
        long answer = response.getCryptogetAccountBalance().getBalance();

        assertTrue(response.getCryptogetAccountBalance().hasHeader(), "Missing response header!");
        assertEquals(
                List.of(
                        tokenBalanceWith(aToken, aBalance, 1),
                        tokenBalanceWith(bToken, bBalance, 2),
                        tokenBalanceWith(cToken, cBalance, 123),
                        tokenBalanceWith(dToken, dBalance, 123)),
                response.getCryptogetAccountBalance().getTokenBalancesList());
        assertEquals(OK, status);
        assertEquals(balance, answer);
        assertEquals(
                wellKnownId.toGrpcAccountId(),
                response.getCryptogetAccountBalance().getAccountID());
    }

    @Test
    void answersWithAccountBalance() {
        AccountID id = asAccount(accountIdLit);
        final var query = accountQueryWith(id);
        final var wellKnownId = EntityNum.fromLong(12345L);
        accountV.setKey(EntityNum.fromAccountId(asAccount(accountIdLit)));
        given(accounts.get(wellKnownId)).willReturn(accountV);
        given(dynamicProperties.maxTokensRelsPerInfoQuery()).willReturn(maxTokenRels);

        // when:
        Response response = subject.responseGiven(query, wellKnownView(), OK);
        ResponseCodeEnum status =
                response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode();
        long answer = response.getCryptogetAccountBalance().getBalance();

        // expect:
        assertTrue(response.getCryptogetAccountBalance().hasHeader(), "Missing response header!");
        assertEquals(
                List.of(
                        tokenBalanceWith(aToken, aBalance, 1),
                        tokenBalanceWith(bToken, bBalance, 2),
                        tokenBalanceWith(cToken, cBalance, 123),
                        tokenBalanceWith(dToken, dBalance, 123)),
                response.getCryptogetAccountBalance().getTokenBalancesList());
        assertEquals(OK, status);
        assertEquals(balance, answer);
        assertEquals(id, response.getCryptogetAccountBalance().getAccountID());
    }

    @Test
    void answersWithAccountBalanceWhenTheAccountIDIsContractID() {
        ContractID id = asContract(accountIdLit);
        Query query = contractQueryWith(id);

        accountV.setKey(EntityNum.fromAccountId(asAccount(accountIdLit)));
        final var view = wellKnownView();
        given(accounts.get(EntityNum.fromContractId(id))).willReturn(accountV);
        given(dynamicProperties.maxTokensRelsPerInfoQuery()).willReturn(maxTokenRels);

        // when:
        Response response = subject.responseGiven(query, view, OK);
        ResponseCodeEnum status =
                response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode();
        long answer = response.getCryptogetAccountBalance().getBalance();

        // expect:
        assertTrue(response.getCryptogetAccountBalance().hasHeader(), "Missing response header!");
        assertEquals(
                List.of(
                        tokenBalanceWith(aToken, aBalance, 1),
                        tokenBalanceWith(bToken, bBalance, 2),
                        tokenBalanceWith(cToken, cBalance, 123),
                        tokenBalanceWith(dToken, dBalance, 123)),
                response.getCryptogetAccountBalance().getTokenBalancesList());
        assertEquals(OK, status);
        assertEquals(balance, answer);
        assertEquals(asAccount(accountIdLit), response.getCryptogetAccountBalance().getAccountID());
    }

    private Query contractQueryWith(final ContractID id) {
        CryptoGetAccountBalanceQuery op =
                CryptoGetAccountBalanceQuery.newBuilder().setContractID(id).build();
        return Query.newBuilder().setCryptogetAccountBalance(op).build();
    }

    private Query accountQueryWith(final AccountID id) {
        CryptoGetAccountBalanceQuery op =
                CryptoGetAccountBalanceQuery.newBuilder().setAccountID(id).build();
        return Query.newBuilder().setCryptogetAccountBalance(op).build();
    }

    @Test
    void recognizesFunction() {
        // expect:
        assertEquals(CryptoGetAccountBalance, subject.canonicalFunction());
    }

    private ResponseCodeEnum statusFrom(final Response response) {
        return response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode();
    }

    private long balanceFrom(final Response response) {
        return response.getCryptogetAccountBalance().getBalance();
    }

    private StateView wellKnownView() {
        MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels = new MerkleMap<>();
        tokenRels.put(aKey, aRel);
        aRel.setNext(bToken.getTokenNum());
        tokenRels.put(bKey, bRel);
        bRel.setPrev(aToken.getTokenNum());
        bRel.setNext(cToken.getTokenNum());
        tokenRels.put(cKey, cRel);
        cRel.setPrev(bToken.getTokenNum());
        cRel.setNext(dToken.getTokenNum());
        tokenRels.put(dKey, dRel);
        dRel.setPrev(cToken.getTokenNum());

        MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
        tokens.put(EntityNum.fromTokenId(aToken), notDeleted);
        tokens.put(EntityNum.fromTokenId(bToken), alsoNotDeleted);
        tokens.put(EntityNum.fromTokenId(cToken), deleted);
        tokens.put(EntityNum.fromTokenId(dToken), andAlsoNotDeleted);

        ScheduleStore scheduleStore = mock(ScheduleStore.class);

        final MutableStateChildren children = new MutableStateChildren();
        children.setTokens(tokens);
        children.setAccounts(accounts);
        children.setTokenAssociations(tokenRels);
        return new StateView(scheduleStore, children, null);
    }

    private final JKey multiKey = new JEd25519Key("01234578901234578901234578912".getBytes());
    private final String accountIdLit = "0.0.12345";
    private final AccountID target = asAccount(accountIdLit);
    private final long balance = 1_234L;
    private final long aBalance = 345;
    private final long bBalance = 456;
    private final long cBalance = 567;
    private final long dBalance = 678;
    private final TokenID aToken = IdUtils.asToken("0.0.3");
    private final TokenID bToken = IdUtils.asToken("0.0.4");
    private final TokenID cToken = IdUtils.asToken("0.0.5");
    private final TokenID dToken = IdUtils.asToken("0.0.6");
    private final EntityNumPair aKey = fromAccountTokenRel(target, aToken);
    private final EntityNumPair bKey = fromAccountTokenRel(target, bToken);
    private final EntityNumPair cKey = fromAccountTokenRel(target, cToken);
    private final EntityNumPair dKey = fromAccountTokenRel(target, dToken);
    private final MerkleToken notDeleted = new MerkleToken();
    private final MerkleToken alsoNotDeleted = new MerkleToken();
    private final MerkleToken deleted = new MerkleToken();
    private final MerkleToken andAlsoNotDeleted = new MerkleToken();
    private final MerkleAccount accountV =
            MerkleAccountFactory.newAccount()
                    .balance(balance)
                    .tokens(aToken, bToken, cToken, dToken)
                    .get();
    private final MerkleTokenRelStatus aRel = new MerkleTokenRelStatus(aBalance, true, true, true);
    private final MerkleTokenRelStatus bRel =
            new MerkleTokenRelStatus(bBalance, false, false, false);
    private final MerkleTokenRelStatus cRel =
            new MerkleTokenRelStatus(cBalance, false, false, true);
    private final MerkleTokenRelStatus dRel =
            new MerkleTokenRelStatus(dBalance, false, false, true);

    {
        deleted.setDeleted(true);
        deleted.setDecimals(123);
        deleted.setSymbol("deletedToken");
        deleted.setFreezeKey(multiKey);
        deleted.setKycKey(multiKey);

        notDeleted.setDecimals(1);
        notDeleted.setSymbol("existingToken");
        notDeleted.setFreezeKey(multiKey);
        notDeleted.setKycKey(multiKey);

        alsoNotDeleted.setDecimals(2);
        alsoNotDeleted.setSymbol("existingToken");
        alsoNotDeleted.setFreezeKey(multiKey);
        alsoNotDeleted.setKycKey(multiKey);

        andAlsoNotDeleted.setDecimals(123);
        andAlsoNotDeleted.setSymbol("existingToken");
        andAlsoNotDeleted.setFreezeKey(multiKey);
        andAlsoNotDeleted.setKycKey(multiKey);

        accountV.setHeadTokenId(aToken.getTokenNum());
        accountV.setNumAssociations(4);
        accountV.setNumPositiveBalances(0);
    }

    private static final int maxTokenRels = 10;
    private static final ByteString evmAddress =
            ByteStringUtils.wrapUnsafely("aabbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes());
    private static final ContractID aliasContractId =
            ContractID.newBuilder().setEvmAddress(evmAddress).build();
}
