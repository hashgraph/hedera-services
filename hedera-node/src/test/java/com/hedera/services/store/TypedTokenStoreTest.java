package com.hedera.services.store;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqueTokenViewsManager;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TypedTokenStoreTest {
	@Mock
	private SideEffectsTracker sideEffectsTracker;
	@Mock
	private AccountStore accountStore;
	@Mock
	private UniqueTokenViewsManager uniqueTokenViewsManager;
	@Mock
	private BackingStore<TokenID, MerkleToken> tokens;
	@Mock
	private BackingStore<NftId, MerkleUniqueToken> uniqueTokens;
	@Mock
	private BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> tokenRels;
	@Mock
	private TokenStore legacyStore;

	@Mock
	private TokenStore tokenStore;

	private TypedTokenStore subject;

	@BeforeEach
	void setUp() {
		setupToken();
		setupTokenRel();

		subject = new TypedTokenStore(
				accountStore,
				tokens,
				uniqueTokens,
				tokenRels,
				uniqueTokenViewsManager,
				tokenStore::addKnownTreasury,
				legacyStore::removeKnownTreasuryForToken,
				sideEffectsTracker);
	}

	/* --- Token relationship loading --- */
	@Test
	void failsLoadingMissingRelationship() {
		assertMiscRelLoadFailsWith(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
	}

	@Test
	void loadPossiblyDeletedTokenRelationshipWorks() {
		givenRelationship(miscTokenRelId, miscTokenMerkleRel);

		final var actualTokenRel = subject.loadPossiblyDeletedTokenRelationship(token, miscAccount);

		assertEquals(miscTokenRel, actualTokenRel);
	}

	@Test
	void loadPossiblyDeletedTokenRelationshipReturnsNullAsExpected() {
		assertNull(subject.loadPossiblyDeletedTokenRelationship(token, miscAccount));
	}

	@Test
	void loadsExpectedRelationship() {
		givenRelationship(miscTokenRelId, miscTokenMerkleRel);

		// when:
		final var actualTokenRel = subject.loadTokenRelationship(token, miscAccount);

		// then:
		assertEquals(miscTokenRel, actualTokenRel);
	}

	/* --- Token relationship saving --- */
	@Test
	void persistsExtantTokenRelAsExpected() {
		// setup:
		final var expectedReplacementTokenRel = new MerkleTokenRelStatus(balance * 2, !frozen, !kycGranted, automaticAssociation);

		givenRelationship(miscTokenRelId, miscTokenMerkleRel);
		givenModifiableRelationship(miscTokenRelId, miscTokenMerkleRel);

		// when:
		final var modelTokenRel = subject.loadTokenRelationship(token, miscAccount);
		// and:
		modelTokenRel.setBalance(balance * 2);
		modelTokenRel.setFrozen(!frozen);
		modelTokenRel.setKycGranted(!kycGranted);
		// and:
		subject.commitTokenRelationships(List.of(modelTokenRel));

		// then:
		assertEquals(expectedReplacementTokenRel, miscTokenMerkleRel);
		// and:
		verify(sideEffectsTracker).trackTokenBalanceChanges(List.of(modelTokenRel));
	}

	@Test
	void removesDestroyedRel() {
		// setup:
		final var destroyedRel = new TokenRelationship(token, miscAccount);
		destroyedRel.markAsPersisted();
		destroyedRel.markAsDestroyed();

		// when:
		subject.commitTokenRelationships(List.of(destroyedRel));

		// then:
		verify(tokenRels).remove(Pair.of(
                      STATIC_PROPERTIES.scopedAccountWith(miscAccountNum),
		      STATIC_PROPERTIES.scopedTokenWith(tokenNum)));
		verify(sideEffectsTracker).trackTokenBalanceChanges(List.of(destroyedRel));
	}

	@Test
	void persistTrackers() {
		final var ot = new OwnershipTracker();
		subject.commitTrackers(ot);
		verify(sideEffectsTracker).trackTokenOwnershipChanges(ot);
	}

	@Test
	void persistsNewTokenRelAsExpected() {
		// setup:
		final var expectedNewTokenRel = new MerkleTokenRelStatus(balance * 2, false, true, false);

		// given:
		final var newTokenRel = new TokenRelationship(token, miscAccount);

		// when:
		newTokenRel.setKycGranted(true);
		newTokenRel.setBalance(balance * 2);
		// and:
		subject.commitTokenRelationships(List.of(newTokenRel));

		// then:
		verify(tokenRels).put(Pair.of(STATIC_PROPERTIES.scopedAccountWith(miscAccountNum),
				STATIC_PROPERTIES.scopedTokenWith(tokenNum)), expectedNewTokenRel);
		// and:
		verify(sideEffectsTracker).trackTokenBalanceChanges(List.of(newTokenRel));
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
		final var merkleUniqueToken = mock(MerkleUniqueToken.class);
		final var serialNumbers = List.of(1L, 2L);
		given(merkleUniqueToken.getOwner()).willReturn(new EntityId(Id.DEFAULT));
		given(uniqueTokens.getImmutableRef(any())).willReturn(merkleUniqueToken);

		subject.loadUniqueTokens(aToken, serialNumbers);

		assertEquals(2, aToken.getLoadedUniqueTokens().size());

		given(uniqueTokens.getImmutableRef(any())).willReturn(null);
		assertThrows(InvalidTransactionException.class, () -> subject.loadUniqueTokens(aToken, serialNumbers));
	}

	@Test
	void persistsDeletedTokenAsExpected() {
		setupToken();
		givenModifiableToken(merkleTokenId, merkleToken);

		token.setIsDeleted(true);
		token.setAutoRenewAccount(null);

		subject.commitToken(token);

		assertTrue(merkleToken.isDeleted());
		verify(legacyStore).removeKnownTreasuryForToken(any(), any());
	}

	/* --- Token saving --- */
	@Test
	void savesTokenAsExpected() {
		// setup:
		final var mintedSerialNo = 33L;
		final var wipedSerialNo = 33L;
		final var mintedSerialNo2 = 44;
		final var burnedSerialNo = 44;
		final var nftMeta = "abcdefgh".getBytes();
		final var treasuryId = new EntityId(0, 0, treasuryAccountNum);
		final var tokenEntityId = new EntityId(0, 0, tokenNum);
		final var creationTime = new RichInstant(1_234_567L, 8);
		final var modelTreasuryId = new Id(0, 0, treasuryAccountNum);
		final var mintedToken = new UniqueToken(tokenId, mintedSerialNo, creationTime, Id.DEFAULT, nftMeta);
		final var wipedToken = new UniqueToken(tokenId, wipedSerialNo, creationTime, modelTreasuryId, nftMeta);
		final var mintedToken2 = new UniqueToken(tokenId, mintedSerialNo2, creationTime, Id.DEFAULT, nftMeta);
		final var burnedToken = new UniqueToken(tokenId, burnedSerialNo, creationTime, modelTreasuryId, nftMeta);
		// and:
		final var expectedReplacementToken = new MerkleToken(
				expiry, tokenSupply * 2, 0,
				symbol, name,
				freezeDefault, true,
				new EntityId(0, 0, autoRenewAccountNum));
		expectedReplacementToken.setAutoRenewAccount(treasuryId);
		expectedReplacementToken.setSupplyKey(supplyKey);
		expectedReplacementToken.setFreezeKey(freezeKey);
		expectedReplacementToken.setKycKey(kycKey);
		expectedReplacementToken.setPauseKey(pauseKey);
		expectedReplacementToken.setAccountsFrozenByDefault(!freezeDefault);
		expectedReplacementToken.setMemo(memo);
		expectedReplacementToken.setAutoRenewPeriod(autoRenewPeriod);

		// and:
		final var expectedReplacementToken2 = new MerkleToken(
				expiry, tokenSupply * 4, 0,
				symbol, name,
				freezeDefault, true,
				new EntityId(0, 0, treasuryAccountNum));
		expectedReplacementToken2.setAutoRenewAccount(treasuryId);
		expectedReplacementToken2.setSupplyKey(supplyKey);
		expectedReplacementToken2.setFreezeKey(freezeKey);
		expectedReplacementToken2.setKycKey(kycKey);
		expectedReplacementToken2.setPauseKey(pauseKey);
		expectedReplacementToken2.setAccountsFrozenByDefault(!freezeDefault);
		expectedReplacementToken2.setMemo(memo);
		expectedReplacementToken2.setAutoRenewPeriod(autoRenewPeriod);
		// and:
		final var expectedNewUniqTokenId = NftId.withDefaultShardRealm(tokenEntityId.num(), mintedSerialNo);
		final var expectedNewUniqTokenId2 = NftId.withDefaultShardRealm(tokenEntityId.num(), mintedSerialNo2);
		final var expectedNewUniqToken = new MerkleUniqueToken(MISSING_ENTITY_ID, nftMeta, creationTime);
		final var expectedPastUniqTokenId = NftId.withDefaultShardRealm(tokenEntityId.num(), wipedSerialNo);
		final var expectedPastUniqTokenId2 = NftId.withDefaultShardRealm(tokenEntityId.num(), burnedSerialNo);

		givenModifiableToken(merkleTokenId, merkleToken);
		givenToken(merkleTokenId, merkleToken);

		// when:
		var modelToken = subject.loadToken(tokenId);
		// and:
		modelToken.setTotalSupply(tokenSupply * 2);
		modelToken.setAutoRenewAccount(treasuryAccount);
		modelToken.setTreasury(autoRenewAccount);
		modelToken.setFrozenByDefault(!freezeDefault);
		modelToken.mintedUniqueTokens().add(mintedToken);
		modelToken.setIsDeleted(false);
		modelToken.setExpiry(expiry);
		modelToken.setAutoRenewPeriod(autoRenewPeriod);
		modelToken.removedUniqueTokens().add(wipedToken);
		modelToken.setAutoRenewPeriod(autoRenewPeriod);
		modelToken.setCustomFees(List.of());
		modelToken.setMemo(memo);
		// and:
		subject.commitToken(modelToken);

		// then:
		assertEquals(expectedReplacementToken, merkleToken);
		// and:
		verify(sideEffectsTracker).trackTokenChanges(modelToken);
		verify(uniqueTokens).put(expectedNewUniqTokenId, expectedNewUniqToken);
		verify(uniqueTokens).put(NftId.withDefaultShardRealm(tokenEntityId.num(), mintedSerialNo), expectedNewUniqToken);
		verify(uniqueTokens).remove(expectedPastUniqTokenId);
		verify(uniqueTokenViewsManager).mintNotice(EntityNumPair.fromNftId(expectedNewUniqTokenId), autoRenewId.asEntityId());
		verify(uniqueTokenViewsManager).wipeNotice(EntityNumPair.fromNftId(expectedPastUniqTokenId), treasuryId);

		// when:
		modelToken = subject.loadToken(tokenId);
		// and:
		modelToken.setTotalSupply(tokenSupply * 4);
		modelToken.setAutoRenewAccount(treasuryAccount);
		modelToken.setTreasury(treasuryAccount);
		modelToken.setFrozenByDefault(!freezeDefault);
		modelToken.mintedUniqueTokens().add(mintedToken2);
		modelToken.setIsDeleted(false);
		modelToken.setExpiry(expiry);
		modelToken.removedUniqueTokens().add(burnedToken);
		modelToken.setCustomFees(List.of());
		// and:
		subject.commitToken(modelToken);

		// then:
		assertEquals(expectedReplacementToken2, merkleToken);
		// and:
		verify(sideEffectsTracker).trackTokenChanges(modelToken);
		verify(uniqueTokens).put(expectedNewUniqTokenId2, expectedNewUniqToken);
		verify(uniqueTokens).remove(expectedPastUniqTokenId2);
		verify(uniqueTokenViewsManager).mintNotice(EntityNumPair.fromNftId(expectedNewUniqTokenId2), treasuryId);
		verify(uniqueTokenViewsManager).burnNotice(EntityNumPair.fromNftId(expectedPastUniqTokenId2), treasuryId);
	}

	@Test
	void persistsNewTokenAsExpected() {
		final var newToken = new Token(IdUtils.asModelId("1.2.3"));
		newToken.setNew(true);
		newToken.setExpiry(123);
		newToken.setTreasury(treasuryAccount);
		newToken.setMemo("memo");
		newToken.setName("name");
		newToken.setType(TokenType.FUNGIBLE_COMMON);
		newToken.initSupplyConstraints(TokenSupplyType.INFINITE, 0);
		newToken.setTotalSupply(1000);
		newToken.setKycKey(kycKey);
		newToken.setFreezeKey(freezeKey);
		newToken.setSupplyKey(supplyKey);
		newToken.setWipeKey(wipeKey);
		newToken.setAdminKey(adminKey);
		newToken.setCustomFees(List.of());

		subject.persistNew(newToken);
		verify(tokens).put(any(), any());
		verify(sideEffectsTracker).trackTokenChanges(newToken);
	}

	@Test
	void loadOrFailsWorksAsExpected() {
		assertFailsWith(() -> subject.loadTokenOrFailWith(Id.DEFAULT, FAIL_INVALID), FAIL_INVALID);
		given(tokens.getImmutableRef(any())).willReturn(merkleToken);
		assertNotNull(subject.loadTokenOrFailWith(IdUtils.asModelId("0.0.3"), FAIL_INVALID));
	}

	private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
		var ex = Assertions.assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}

	private void givenRelationship(final EntityNumPair anAssoc, MerkleTokenRelStatus aRelationship) {
		given(tokenRels.getImmutableRef(anAssoc.asAccountTokenRel())).willReturn(aRelationship);
	}

	private void givenModifiableRelationship(final EntityNumPair anAssoc, final MerkleTokenRelStatus aRelationship) {
		given(tokenRels.getRef(anAssoc.asAccountTokenRel())).willReturn(aRelationship);
	}

	private void givenToken(final EntityNum anId, final MerkleToken aToken) {
		given(tokens.getImmutableRef(anId.toGrpcTokenId())).willReturn(aToken);
	}

	private void givenModifiableToken(final EntityNum anId, final MerkleToken aToken) {
		given(tokens.getRef(anId.toGrpcTokenId())).willReturn(aToken);
	}

	private void assertTokenLoadFailsWith(final ResponseCodeEnum status) {
		final var ex = assertThrows(InvalidTransactionException.class, () -> subject.loadToken(tokenId));
		assertEquals(status, ex.getResponseCode());
	}

	private void assertLoadPossiblyPausedTokenFailsWith(final ResponseCodeEnum status) {
		final var ex = assertThrows(InvalidTransactionException.class, () -> subject.loadPossiblyPausedToken(tokenId));
		assertEquals(status, ex.getResponseCode());
	}

	private void assertLoadPossiblyDeletedTokenFailsWith(final ResponseCodeEnum status) {
		final var ex = assertThrows(InvalidTransactionException.class,
				() -> subject.loadPossiblyDeletedOrAutoRemovedToken(tokenId));
		assertEquals(status, ex.getResponseCode());
	}

	private void assertMiscRelLoadFailsWith(final ResponseCodeEnum status) {
		final var ex = assertThrows(InvalidTransactionException.class,
				() -> subject.loadTokenRelationship(token, miscAccount));
		assertEquals(status, ex.getResponseCode());
	}

	private void setupToken() {
		merkleToken = new MerkleToken(
				expiry, tokenSupply, 0,
				symbol, name,
				freezeDefault, true,
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
		miscTokenMerkleRel = new MerkleTokenRelStatus(balance, frozen, kycGranted, automaticAssociation);
		miscTokenRel.initBalance(balance);
		miscTokenRel.setFrozen(frozen);
		miscTokenRel.setKycGranted(kycGranted);
		miscTokenRel.setAutomaticAssociation(automaticAssociation);
		miscTokenRel.markAsPersisted();
	}

	private final long expiry = 1_234_567L;
	private final long balance = 1_000L;
	private final long miscAccountNum = 1_234L;
	private final long treasuryAccountNum = 2_234L;
	private final long autoRenewAccountNum = 3_234L;
	private final long autoRenewPeriod = 1234L;
	private final Id miscId = new Id(0, 0, miscAccountNum);
	private final Id treasuryId = new Id(0, 0, treasuryAccountNum);
	private final Id autoRenewId = new Id(0, 0, autoRenewAccountNum);
	private final Account miscAccount = new Account(miscId);
	private final Account treasuryAccount = new Account(treasuryId);
	private final Account autoRenewAccount = new Account(autoRenewId);

	private final JKey kycKey = TxnHandlingScenario.TOKEN_KYC_KT.asJKeyUnchecked();
	private final JKey freezeKey = TxnHandlingScenario.TOKEN_FREEZE_KT.asJKeyUnchecked();
	private final JKey supplyKey = TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKeyUnchecked();
	private final JKey wipeKey = TxnHandlingScenario.TOKEN_WIPE_KT.asJKeyUnchecked();
	private final JKey adminKey = TxnHandlingScenario.TOKEN_ADMIN_KT.asJKeyUnchecked();
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
