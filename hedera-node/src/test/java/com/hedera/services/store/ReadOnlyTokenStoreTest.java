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

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReadOnlyTokenStoreTest {
    @Mock private AccountStore accountStore;
    @Mock private BackingStore<TokenID, MerkleToken> tokens;
    @Mock private BackingStore<NftId, UniqueTokenAdapter> uniqueTokens;
    @Mock private BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> tokenRels;

    private ReadOnlyTokenStore subject;

    @BeforeEach
    void setUp() {
        setupToken();
        setupTokenRel();

        subject = new ReadOnlyTokenStore(accountStore, tokens, uniqueTokens, tokenRels);
    }

    private void setupToken() {
        merkleToken =
                new MerkleToken(
                        expiry,
                        tokenSupply,
                        0,
                        symbol,
                        name,
                        freezeDefault,
                        true,
                        new EntityId(0, 0, treasuryAccountNum));
        merkleToken.setAutoRenewAccount(new EntityId(0, 0, autoRenewAccountNum));
        merkleToken.setSupplyKey(supplyKey);
        merkleToken.setKycKey(kycKey);
        merkleToken.setFreezeKey(freezeKey);
        merkleToken.setPauseKey(pauseKey);
        merkleToken.setPaused(false);

        token.setTreasury(treasuryAccount);
        token.setAutoRenewAccount(autoRenewAccount);
        token.setTotalSupply(tokenSupply);
        token.setKycKey(kycKey);
        token.setSupplyKey(supplyKey);
        token.setFreezeKey(freezeKey);
        token.setPauseKey(pauseKey);
        token.setFrozenByDefault(freezeDefault);
        token.setIsDeleted(false);
        token.setPaused(false);
        token.setExpiry(expiry);
    }

    private void setupTokenRel() {
        miscTokenMerkleRel =
                new MerkleTokenRelStatus(balance, frozen, kycGranted, automaticAssociation);
        miscTokenMerkleRel.setKey(miscTokenRelId);
        miscTokenMerkleRel.setPrev(0);
        miscTokenMerkleRel.setNext(0);
        miscTokenRel.initBalance(balance);
        miscTokenRel.setFrozen(frozen);
        miscTokenRel.setKycGranted(kycGranted);
        miscTokenRel.setAutomaticAssociation(automaticAssociation);
        miscTokenRel.markAsPersisted();
    }

    /* --- Token loading --- */
    @Test
    void reportsExpectedNftsMinted() {
        given(uniqueTokens.size()).willReturn(123L);
        // expect:
        assertEquals(123L, subject.currentMintedNfts());
    }

    @Test
    void canLoadPausedTokenUsingLoadPossiblyPausedToken() {
        given(accountStore.loadAccount(autoRenewId)).willReturn(autoRenewAccount);
        given(accountStore.loadAccount(treasuryId)).willReturn(treasuryAccount);
        givenToken(merkleTokenId, merkleToken);
        merkleToken.setPaused(true);
        token.setPaused(true);

        final var actualToken = subject.loadPossiblyPausedToken(tokenId);

        assertEquals(token.toString(), actualToken.toString());
        assertEquals(token.isPaused(), actualToken.isPaused());
        assertTrue(actualToken.isPaused());
    }

    @Test
    void failsLoadPossiblyPausedTokenMissingToken() {
        assertLoadPossiblyPausedTokenFailsWith(INVALID_TOKEN_ID);
    }

    @Test
    void failsLoadPossiblyPausedTokenDeletedToken() {
        givenToken(merkleTokenId, merkleToken);
        merkleToken.setDeleted(true);

        assertLoadPossiblyPausedTokenFailsWith(TOKEN_WAS_DELETED);
    }

    @Test
    void loadsExpectedToken() {
        given(accountStore.loadAccount(autoRenewId)).willReturn(autoRenewAccount);
        given(accountStore.loadAccount(treasuryId)).willReturn(treasuryAccount);
        givenToken(merkleTokenId, merkleToken);

        // when:
        final var actualToken = subject.loadToken(tokenId);

        // then:
        /* JKey does not override equals properly, have to compare string representations here */
        assertEquals(token.toString(), actualToken.toString());
    }

    @Test
    void failsLoadingTokenWithDetachedAutoRenewAccount() {
        given(accountStore.loadAccount(autoRenewId))
                .willThrow(new InvalidTransactionException(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
        givenToken(merkleTokenId, merkleToken);

        assertTokenLoadFailsWith(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    }

    @Test
    void failsLoadingMissingToken() {
        assertTokenLoadFailsWith(INVALID_TOKEN_ID);
    }

    @Test
    void canLoadAutoRemovedTokenIfAllowed() {
        final var autoRemovedToken = subject.loadPossiblyDeletedOrAutoRemovedToken(tokenId);

        assertEquals(tokenId, autoRemovedToken.getId());
        assertTrue(autoRemovedToken.isBelievedToHaveBeenAutoRemoved());
    }

    @Test
    void loadsActuallyDeletedTokenAsExpected() {
        givenToken(merkleTokenId, merkleToken);
        merkleToken.setDeleted(true);

        final var deletedToken = subject.loadPossiblyDeletedOrAutoRemovedToken(tokenId);

        assertEquals(token.getId(), deletedToken.getId());
    }

    @Test
    void failsLoadingDeletedToken() {
        givenToken(merkleTokenId, merkleToken);
        merkleToken.setDeleted(true);

        assertTokenLoadFailsWith(TOKEN_WAS_DELETED);
    }

    @Test
    void failsLoadingPausedTokenUsingLoadPossiblyDeletedOrAutoRemovedToken() {
        givenToken(merkleTokenId, merkleToken);
        merkleToken.setPaused(true);
        assertLoadPossiblyDeletedTokenFailsWith(TOKEN_IS_PAUSED);
    }

    @Test
    void failsLoadingPausedToken() {
        givenToken(merkleTokenId, merkleToken);
        merkleToken.setPaused(true);

        assertTokenLoadFailsWith(TOKEN_IS_PAUSED);
    }

    @Test
    void loadOrFailsCantLoadPausedToken() {
        givenToken(merkleTokenId, merkleToken);
        merkleToken.setPaused(true);
        assertFailsWith(() -> subject.loadTokenOrFailWith(tokenId, FAIL_INVALID), FAIL_INVALID);
    }

    @Test
    void loadsUniqueTokens() {
        final var aToken = new Token(miscId);
        final var uniqueTokenHolder =
                UniqueTokenAdapter.wrap(
                        new MerkleUniqueToken(
                                new EntityId(Id.DEFAULT),
                                new byte[0],
                                RichInstant.MISSING_INSTANT));
        uniqueTokenHolder.setSpender(new EntityId(Id.DEFAULT));
        final var serialNumbers = List.of(1L, 2L);
        given(uniqueTokens.getImmutableRef(any())).willReturn(uniqueTokenHolder);

        subject.loadUniqueTokens(aToken, serialNumbers);

        assertEquals(2, aToken.getLoadedUniqueTokens().size());

        given(uniqueTokens.getImmutableRef(any())).willReturn(null);
        assertThrows(
                InvalidTransactionException.class,
                () -> subject.loadUniqueTokens(aToken, serialNumbers));
    }

    @Test
    void loadsUniqueTokensVirtual() {
        final var aToken = new Token(miscId);
        final var uniqueTokenHolder =
                UniqueTokenAdapter.wrap(
                        new UniqueTokenValue(
                                Id.DEFAULT.num(),
                                Id.DEFAULT.num(),
                                new byte[0],
                                RichInstant.MISSING_INSTANT));
        uniqueTokenHolder.setSpender(new EntityId(Id.DEFAULT));
        final var serialNumbers = List.of(1L, 2L);
        given(uniqueTokens.getImmutableRef(any())).willReturn(uniqueTokenHolder);

        subject.loadUniqueTokens(aToken, serialNumbers);

        assertEquals(2, aToken.getLoadedUniqueTokens().size());

        given(uniqueTokens.getImmutableRef(any())).willReturn(null);
        assertThrows(
                InvalidTransactionException.class,
                () -> subject.loadUniqueTokens(aToken, serialNumbers));
    }

    @Test
    void loadOrFailsWorksAsExpected() {
        assertFailsWith(() -> subject.loadTokenOrFailWith(Id.DEFAULT, FAIL_INVALID), FAIL_INVALID);
        given(tokens.getImmutableRef(any())).willReturn(merkleToken);
        assertNotNull(subject.loadTokenOrFailWith(IdUtils.asModelId("0.0.3"), FAIL_INVALID));
    }

    @Test
    void loadPossiblyDeletedTokenRelationshipWorks() {
        givenRelationship(miscTokenRelId, miscTokenMerkleRel);

        final var actualTokenRel = subject.loadPossiblyMissingTokenRelationship(token, miscAccount);

        assertEquals(miscTokenRel, actualTokenRel);
    }

    @Test
    void loadsExpectedRelationship() {
        givenRelationship(miscTokenRelId, miscTokenMerkleRel);

        // when:
        final var actualTokenRel = subject.loadTokenRelationship(token, miscAccount);

        // then:
        assertEquals(miscTokenRel, actualTokenRel);
    }

    private void givenRelationship(
            final EntityNumPair anAssoc, MerkleTokenRelStatus aRelationship) {
        given(tokenRels.getImmutableRef(anAssoc.asAccountTokenRel())).willReturn(aRelationship);
    }

    private void givenToken(final EntityNum anId, final MerkleToken aToken) {
        given(tokens.getImmutableRef(anId.toGrpcTokenId())).willReturn(aToken);
    }

    private void assertTokenLoadFailsWith(final ResponseCodeEnum status) {
        final var ex =
                assertThrows(InvalidTransactionException.class, () -> subject.loadToken(tokenId));
        assertEquals(status, ex.getResponseCode());
    }

    private void assertLoadPossiblyPausedTokenFailsWith(final ResponseCodeEnum status) {
        final var ex =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> subject.loadPossiblyPausedToken(tokenId));
        assertEquals(status, ex.getResponseCode());
    }

    private void assertLoadPossiblyDeletedTokenFailsWith(final ResponseCodeEnum status) {
        final var ex =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> subject.loadPossiblyDeletedOrAutoRemovedToken(tokenId));
        assertEquals(status, ex.getResponseCode());
    }

    private final long expiry = 1_234_567L;
    private final long balance = 1_000L;
    private final long miscAccountNum = 1_234L;
    private final long treasuryAccountNum = 2_234L;
    private final long autoRenewAccountNum = 3_234L;
    private final Id miscId = new Id(0, 0, miscAccountNum);
    private final Id treasuryId = new Id(0, 0, treasuryAccountNum);
    private final Id autoRenewId = new Id(0, 0, autoRenewAccountNum);
    private final Account miscAccount = new Account(miscId);
    private final Account treasuryAccount = new Account(treasuryId);
    private final Account autoRenewAccount = new Account(autoRenewId);

    private final JKey kycKey = TxnHandlingScenario.TOKEN_KYC_KT.asJKeyUnchecked();
    private final JKey freezeKey = TxnHandlingScenario.TOKEN_FREEZE_KT.asJKeyUnchecked();
    private final JKey supplyKey = TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKeyUnchecked();
    private final JKey pauseKey = TxnHandlingScenario.TOKEN_PAUSE_KT.asJKeyUnchecked();
    private final long tokenNum = 4_234L;
    private final long tokenSupply = 777L;
    private final String name = "Testing123";
    private final String symbol = "T123";
    private final String memo = "memo";
    private final EntityNum merkleTokenId = EntityNum.fromLong(tokenNum);
    private final Id tokenId = new Id(0, 0, tokenNum);
    private final Token token = new Token(tokenId);

    private final boolean frozen = false;
    private final boolean kycGranted = true;
    private final boolean freezeDefault = true;
    private final EntityNumPair miscTokenRelId = EntityNumPair.fromLongs(miscAccountNum, tokenNum);
    private final boolean automaticAssociation = true;
    private final TokenRelationship miscTokenRel = new TokenRelationship(token, miscAccount);
    private MerkleToken merkleToken;
    private MerkleTokenRelStatus miscTokenMerkleRel;
}
