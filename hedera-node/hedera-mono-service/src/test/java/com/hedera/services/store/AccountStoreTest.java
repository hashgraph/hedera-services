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
package com.hedera.services.store;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountStoreTest {
    @Mock private OptionValidator validator;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private BackingStore<AccountID, MerkleAccount> accounts;
    @Mock private TypedTokenStore tokenStore;

    private AccountStore subject;

    @BeforeEach
    void setUp() {
        setupAccounts();

        subject = new AccountStore(validator, accounts);
    }

    @Test
    void objectContractWorks() {
        assertNotNull(subject.getValidator());
    }

    /* --- Account loading --- */
    @Test
    void loadsContractAsExpected() {
        miscMerkleAccount.setSmartContract(true);
        setupWithUnexpiredAccount(miscMerkleId, miscMerkleAccount);
        Account account = subject.loadContract(miscId);

        assertEquals(Id.fromGrpcAccount(miscMerkleId.toGrpcAccountId()), account.getId());
    }

    @Test
    void loadsTreasuryTitles() {
        miscMerkleAccount.setNumTreasuryTitles(34);
        setupWithUnexpiredAccount(miscMerkleId, miscMerkleAccount);
        Account account = subject.loadAccount(miscId);

        assertEquals(34, account.getNumTreasuryTitles());
    }

    @Test
    void failsLoadingContractWithInvalidId() {
        miscMerkleAccount.setSmartContract(false);
        TxnUtils.assertFailsWith(() -> subject.loadContract(miscId), INVALID_CONTRACT_ID);
    }

    @Test
    void failsLoadingMissingAccount() {
        assertMiscAccountLoadFailsWith(INVALID_ACCOUNT_ID);
    }

    @Test
    void failsLoadingDeleted() {
        setupWithAccount(miscMerkleId, miscMerkleAccount);
        miscMerkleAccount.setDeleted(true);

        assertMiscAccountLoadFailsWith(ACCOUNT_DELETED);
    }

    @Test
    void failsLoadingDetached() throws NegativeAccountBalanceException {
        setupWithAccount(miscMerkleId, miscMerkleAccount);
        miscMerkleAccount.setBalance(0L);
        given(validator.expiryStatusGiven(0L, 1234567L, false))
                .willReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        assertMiscAccountLoadFailsWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    }

    @Test
    void canAlwaysLoadWithNonzeroBalance() {
        setupWithUnexpiredAccount(miscMerkleId, miscMerkleAccount);
        miscMerkleAccount.setHeadTokenId(firstAssocTokenNum);
        miscMerkleAccount.setNumAssociations(associatedTokensCount);
        miscMerkleAccount.setNumPositiveBalances(numPositiveBalances);
        miscAccount.setCryptoAllowances(Collections.emptyMap());
        miscAccount.setFungibleTokenAllowances(Collections.emptyMap());
        miscAccount.setApproveForAllNfts(Collections.emptySet());

        // when:
        final var actualAccount = subject.loadAccount(miscId);

        // then:
        assertEquals(miscAccount, actualAccount);

        final var actualAccount2 = subject.loadAccountOrFailWith(miscId, FAIL_INVALID);
        assertEquals(miscAccount, actualAccount2);
    }

    @Test
    void commitIncludesTreasuryTitlesCount() {
        setupWithAccount(miscMerkleId, miscMerkleAccount);
        setupWithMutableAccount(miscMerkleId, miscMerkleAccount);

        final var model = subject.loadAccount(miscId);
        model.setNumTreasuryTitles(34);
        subject.commitAccount(model);

        assertEquals(34, miscMerkleAccount.getNumTreasuryTitles());
    }

    @Test
    void persistenceUpdatesTokens() {
        setupWithUnexpiredAccount(miscMerkleId, miscMerkleAccount);
        setupWithMutableAccount(miscMerkleId, miscMerkleAccount);
        miscMerkleAccount.setKey(miscMerkleId);
        miscMerkleAccount.setNumAssociations(associatedTokensCount);
        miscMerkleAccount.setNumPositiveBalances(numPositiveBalances);
        // and:
        final var aThirdToken = new Token(new Id(0, 0, 888));
        // and:
        final var expectedReplacement =
                MerkleAccountFactory.newAccount()
                        .balance(balance)
                        .expirationTime(expiry)
                        .maxAutomaticAssociations(maxAutoAssociations)
                        .alreadyUsedAutomaticAssociations(alreadyUsedAutoAssociations)
                        .proxy(proxy.asGrpcAccount())
                        .get();
        expectedReplacement.setKey(miscMerkleId);
        expectedReplacement.setNumPositiveBalances(numPositiveBalances);
        expectedReplacement.setNumAssociations(associatedTokensCount + 1);

        // given:
        final var model = subject.loadAccount(miscId);

        // when:
        model.associateWith(List.of(aThirdToken), tokenStore, false, false, dynamicProperties);
        // and:
        subject.commitAccount(model);

        assertEquals(expectedReplacement, miscMerkleAccount);
    }

    @Test
    void failsWithGivenCause() throws NegativeAccountBalanceException {
        setupWithAccount(miscMerkleId, miscMerkleAccount);
        miscMerkleAccount.setDeleted(true);

        var ex =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> subject.loadAccountOrFailWith(miscId, FAIL_INVALID));
        assertEquals(FAIL_INVALID, ex.getResponseCode());

        miscMerkleAccount.setDeleted(false);
        miscMerkleAccount.setBalance(0L);
        given(validator.expiryStatusGiven(0L, expiry, false))
                .willReturn(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        var ex2 =
                assertThrows(
                        InvalidTransactionException.class,
                        () ->
                                subject.loadAccountOrFailWith(
                                        miscId, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
        assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, ex2.getResponseCode());
    }

    @Test
    void persistanceUpdatesAutoAssociations() {
        setupWithAccount(miscMerkleId, miscMerkleAccount);
        setupWithMutableAccount(miscMerkleId, miscMerkleAccount);
        miscMerkleAccount.setHeadTokenId(firstAssocTokenNum);
        miscMerkleAccount.setNumAssociations(associatedTokensCount);
        miscMerkleAccount.setNumPositiveBalances(numPositiveBalances);
        var newMax = maxAutoAssociations + 5;
        var newUsedCount = alreadyUsedAutoAssociations - 10;
        // and:
        final var expectedReplacement =
                MerkleAccountFactory.newAccount()
                        .balance(balance)
                        .expirationTime(expiry)
                        .maxAutomaticAssociations(newMax)
                        .alreadyUsedAutomaticAssociations(newUsedCount)
                        .proxy(proxy.asGrpcAccount())
                        .get();
        expectedReplacement.setNumPositiveBalances(numPositiveBalances);
        expectedReplacement.setNumAssociations(associatedTokensCount);
        expectedReplacement.setHeadTokenId(firstAssocTokenNum);

        // given:
        final var model = subject.loadAccount(miscId);

        // when:
        model.setMaxAutomaticAssociations(newMax);
        // decrease the already Used automatic associations by 10
        for (int i = 0; i < 11; i++) {
            model.decrementUsedAutomaticAssociations();
        }
        model.incrementUsedAutomaticAssociations();

        // and:
        subject.commitAccount(model);

        // then:
        assertEquals(expectedReplacement, miscMerkleAccount);
    }

    private void setupWithAccount(EntityNum anId, MerkleAccount anAccount) {
        given(accounts.getImmutableRef(anId.toGrpcAccountId())).willReturn(anAccount);
    }

    private void setupWithUnexpiredAccount(EntityNum anId, MerkleAccount anAccount) {
        given(accounts.getImmutableRef(anId.toGrpcAccountId())).willReturn(anAccount);
        given(validator.expiryStatusGiven(anyLong(), anyLong(), anyBoolean())).willReturn(OK);
    }

    private void setupWithMutableAccount(EntityNum anId, MerkleAccount anAccount) {
        given(accounts.getRef(anId.toGrpcAccountId())).willReturn(anAccount);
        given(validator.expiryStatusGiven(anyLong(), anyLong(), anyBoolean())).willReturn(OK);
    }

    private void assertMiscAccountLoadFailsWith(ResponseCodeEnum status) {
        var ex = assertThrows(InvalidTransactionException.class, () -> subject.loadAccount(miscId));
        assertEquals(status, ex.getResponseCode());
    }

    private void setupAccounts() {
        miscMerkleAccount =
                MerkleAccountFactory.newAccount()
                        .balance(balance)
                        .expirationTime(expiry)
                        .maxAutomaticAssociations(maxAutoAssociations)
                        .alreadyUsedAutomaticAssociations(alreadyUsedAutoAssociations)
                        .proxy(proxy.asGrpcAccount())
                        .get();

        miscAccount.setExpiry(expiry);
        miscAccount.initBalance(balance);
        miscAccount.setNumAssociations(associatedTokensCount);
        miscAccount.setNumPositiveBalances(numPositiveBalances);
        miscAccount.setMaxAutomaticAssociations(maxAutoAssociations);
        miscAccount.setAlreadyUsedAutomaticAssociations(alreadyUsedAutoAssociations);
        miscAccount.setProxy(proxy);

        firstRel.setKey(firstRelKey);
        secondRel.setKey(secondRelKey);
        secondRel.setPrev(firstAssocTokenNum);
        firstRel.setNext(secondAssocTokenNum);

        autoRenewAccount.setExpiry(expiry);
        autoRenewAccount.initBalance(balance);
    }

    private final long expiry = 1_234_567L;
    private final long balance = 1_000L;
    private final long miscAccountNum = 1_234L;
    private final long autoRenewAccountNum = 3_234L;
    private final long firstAssocTokenNum = 666L;
    private final long secondAssocTokenNum = 777L;
    private final long miscProxyAccount = 9_876L;
    private final int alreadyUsedAutoAssociations = 12;
    private final int maxAutoAssociations = 123;
    private final int associatedTokensCount = 2;
    private final int numPositiveBalances = 1;
    private final Id miscId = new Id(0, 0, miscAccountNum);
    private final Id autoRenewId = new Id(0, 0, autoRenewAccountNum);
    private final Id proxy = new Id(0, 0, miscProxyAccount);
    private final EntityNum miscMerkleId = EntityNum.fromLong(miscAccountNum);
    private final Account miscAccount = new Account(miscId);
    private final Account autoRenewAccount = new Account(autoRenewId);
    final EntityNumPair firstRelKey = EntityNumPair.fromLongs(miscAccountNum, firstAssocTokenNum);
    final MerkleTokenRelStatus firstRel = new MerkleTokenRelStatus(0, false, true, false);
    final EntityNumPair secondRelKey = EntityNumPair.fromLongs(miscAccountNum, secondAssocTokenNum);
    final MerkleTokenRelStatus secondRel = new MerkleTokenRelStatus(0, false, true, false);

    private MerkleAccount miscMerkleAccount;
}
