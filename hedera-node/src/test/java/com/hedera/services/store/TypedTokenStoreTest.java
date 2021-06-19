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

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.records.TransactionRecordService;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TypedTokenStoreTest {
	@Mock
	private AccountStore accountStore;
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private TransactionRecordService transactionRecordService;
	@Mock
	private FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenRels;
	@Mock
	private BackingTokenRels backingTokenRels;

	private TypedTokenStore subject;

	@BeforeEach
	void setUp() {
		setupToken();
		setupTokenRel();

		subject = new TypedTokenStore(
				accountStore, transactionRecordService, () -> tokens, () -> tokenRels, backingTokenRels);
	}

	/* --- Token relationship loading --- */
	@Test
	void failsLoadingMissingRelationship() {
		assertMiscRelLoadFailsWith(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
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
		final var expectedReplacementTokenRel = new MerkleTokenRelStatus(balance * 2, !frozen, !kycGranted);

		givenRelationship(miscTokenRelId, miscTokenMerkleRel);
		givenModifiableRelationship(miscTokenRelId, miscTokenMerkleRel);

		// when:
		final var modelTokenRel = subject.loadTokenRelationship(token, miscAccount);
		// and:
		modelTokenRel.setBalance(balance * 2);
		modelTokenRel.setFrozen(!frozen);
		modelTokenRel.setKycGranted(!kycGranted);
		// and:
		subject.persistTokenRelationship(modelTokenRel);

		// then:
		assertEquals(expectedReplacementTokenRel, miscTokenMerkleRel);
		verify(tokenRels, never()).replace(miscTokenRelId, expectedReplacementTokenRel);
		// and:
		verify(transactionRecordService).includeChangesToTokenRel(modelTokenRel);
	}

	@Test
	void persistsNewTokenRelAsExpected() {
		// setup:
		final var expectedNewTokenRel = new MerkleTokenRelStatus(balance * 2, false, true);

		// given:
		final var newTokenRel = new TokenRelationship(token, miscAccount);

		// when:
		newTokenRel.setKycGranted(true);
		newTokenRel.setBalance(balance * 2);
		// and:
		subject.persistTokenRelationship(newTokenRel);

		// then:
		verify(tokenRels).put(miscTokenRelId, expectedNewTokenRel);
		// and:
		verify(transactionRecordService).includeChangesToTokenRel(newTokenRel);
	}

	/* --- Token loading --- */
	@Test
	void loadsExpectedToken() {
		given(accountStore.loadAccount(autoRenewId)).willReturn(autoRenewAccount);
		given(accountStore.loadAccount(treasuryId)).willReturn(treasuryAccount);
		givenToken(merkleTokenId, merkleToken);

		// when:
		final var actualToken = subject.loadToken(tokenId, true);

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
	void failsLoadingDeletedToken() {
		givenToken(merkleTokenId, merkleToken);
		merkleToken.setDeleted(true);

		assertTokenLoadFailsWith(TOKEN_WAS_DELETED);
	}

	/* --- Token saving --- */
	@Test
	void savesTokenAsExpected() {
		// setup:
		final var expectedReplacementToken = new MerkleToken(
				expiry, tokenSupply * 2, 0,
				symbol, name,
				freezeDefault, true,
				new EntityId(0, 0, autoRenewAccountNum));
		expectedReplacementToken.setAutoRenewAccount(new EntityId(0, 0, treasuryAccountNum));
		expectedReplacementToken.setSupplyKey(supplyKey);
		expectedReplacementToken.setFreezeKey(freezeKey);
		expectedReplacementToken.setKycKey(kycKey);
		expectedReplacementToken.setAccountsFrozenByDefault(!freezeDefault);

		givenToken(merkleTokenId, merkleToken);
		givenModifiableToken(merkleTokenId, merkleToken);

		// when:
		final var modelToken = subject.loadToken(tokenId, true);
		// and:
		modelToken.setTotalSupply(tokenSupply * 2);
		modelToken.setAutoRenewAccount(treasuryAccount);
		modelToken.setTreasury(autoRenewAccount);
		modelToken.setFrozenByDefault(!freezeDefault);
		// and:
		subject.persistToken(modelToken);

		// then:
		assertEquals(expectedReplacementToken, merkleToken);
		verify(tokens, never()).replace(merkleTokenId, expectedReplacementToken);
		// and:
		verify(transactionRecordService).includeChangesToToken(modelToken);
	}

	private void givenRelationship(MerkleEntityAssociation anAssoc, MerkleTokenRelStatus aRelationship) {
		given(tokenRels.get(anAssoc)).willReturn(aRelationship);
	}

	private void givenModifiableRelationship(MerkleEntityAssociation anAssoc, MerkleTokenRelStatus aRelationship) {
		given(tokenRels.getForModify(anAssoc)).willReturn(aRelationship);
	}

	private void givenToken(MerkleEntityId anId, MerkleToken aToken) {
		given(tokens.get(anId)).willReturn(aToken);
	}

	private void givenModifiableToken(MerkleEntityId anId, MerkleToken aToken) {
		given(tokens.getForModify(anId)).willReturn(aToken);
	}

	private void assertTokenLoadFailsWith(ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class, () -> subject.loadToken(tokenId, true));
		assertEquals(status, ex.getResponseCode());
	}

	private void assertMiscRelLoadFailsWith(ResponseCodeEnum status) {
		var ex = assertThrows(InvalidTransactionException.class,
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

		token.setTreasury(treasuryAccount);
		token.setAutoRenewAccount(autoRenewAccount);
		token.setTotalSupply(tokenSupply);
		token.setKycKey(kycKey);
		token.setSupplyKey(supplyKey);
		token.setFreezeKey(freezeKey);
		token.setFrozenByDefault(freezeDefault);
	}

	private void setupTokenRel() {
		miscTokenMerkleRel = new MerkleTokenRelStatus(balance, frozen, kycGranted);
		miscTokenRel.initBalance(balance);
		miscTokenRel.setFrozen(frozen);
		miscTokenRel.setKycGranted(kycGranted);
		miscTokenRel.setNotYetPersisted(false);
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
	private final long tokenNum = 4_234L;
	private final long tokenSupply = 777L;
	private final String name = "Testing123";
	private final String symbol = "T123";
	private final MerkleEntityId merkleTokenId = new MerkleEntityId(0, 0, tokenNum);
	private final Id tokenId = new Id(0, 0, tokenNum);
	private final Token token = new Token(tokenId);

	private final boolean frozen = false;
	private final boolean kycGranted = true;
	private final boolean freezeDefault = true;
	private final MerkleEntityAssociation miscTokenRelId = new MerkleEntityAssociation(
			0, 0, miscAccountNum,
			0, 0, tokenNum);
	private final TokenRelationship miscTokenRel = new TokenRelationship(token, miscAccount);

	private MerkleToken merkleToken;
	private MerkleTokenRelStatus miscTokenMerkleRel;
}
