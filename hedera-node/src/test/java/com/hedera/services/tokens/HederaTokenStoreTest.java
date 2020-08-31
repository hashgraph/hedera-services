package com.hedera.services.tokens;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreation;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class HederaTokenStoreTest {
	EntityIdSource ids;
	GlobalDynamicProperties properties;
	FCMap<MerkleEntityId, MerkleToken> tokens;

	MerkleToken token;
	MerkleAccount account;

	Key adminKey, freezeKey;
	String symbol = "NotHbar";
	long tokenFloat = 1_000_000;
	int divisibility = 10;
	TokenID misc = IdUtils.asToken("3.2.1");
	boolean freezeDefault = true;
	AccountID treasury = IdUtils.asAccount("1.2.3");
	AccountID sponsor = IdUtils.asAccount("1.2.666");
	TokenID created = IdUtils.asToken("1.2.666666");
	int MAX_TOKENS_PER_ACCOUNT = 100;
	int MAX_TOKEN_SYMBOL_LENGTH = 10;
	BackingAccounts<AccountID, MerkleAccount> accounts;

	HederaTokenStore subject;

	@BeforeEach
	public void setup() {
		adminKey = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
		freezeKey = TxnHandlingScenario.CARELESS_SIGNING_PAYER_KT.asKey();

		token = mock(MerkleToken.class);

		ids = mock(EntityIdSource.class);
		given(ids.newTokenId(sponsor)).willReturn(created);

		tokens = (FCMap<MerkleEntityId, MerkleToken>) mock(FCMap.class);
		given(tokens.get(fromTokenId(created))).willReturn(token);

		account = mock(MerkleAccount.class);
		given(account.isDeleted()).willReturn(false);

		accounts = (BackingAccounts<AccountID, MerkleAccount>)mock(BackingAccounts.class);
		given(accounts.contains(treasury)).willReturn(true);
		given(accounts.getRef(treasury)).willReturn(account);

		properties = mock(GlobalDynamicProperties.class);
		given(properties.maxTokensPerAccount()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(properties.maxTokenSymbolLength()).willReturn(MAX_TOKEN_SYMBOL_LENGTH);

		subject = new HederaTokenStore(ids, properties, accounts, () -> tokens);
	}

	@Test
	public void rollbackReclaimsIdAndClears() {
		// setup:
		subject.pendingId = created;
		subject.pendingCreation = token;

		// when:
		subject.rollbackCreation();

		// then:
		verify(tokens, never()).put(fromTokenId(created), token);
		verify(ids).reclaimLastId();
		// and:
		assertSame(subject.pendingId, HederaTokenStore.NO_PENDING_ID);
		assertNull(subject.pendingCreation);
	}

	@Test
	public void commitAndRollbackThrowIseIfNoPendingCreation() {
		// expect:
		assertThrows(IllegalStateException.class, subject::commitCreation);
		assertThrows(IllegalStateException.class, subject::rollbackCreation);
	}

	@Test
	public void commitPutsToMapAndClears() {
		// setup:
		subject.pendingId = created;
		subject.pendingCreation = token;

		// when:
		subject.commitCreation();

		// then:
		verify(tokens).put(fromTokenId(created), token);
		// and:
		assertSame(subject.pendingId, HederaTokenStore.NO_PENDING_ID);
		assertNull(subject.pendingCreation);
	}

	@Test
	public void delegatesIdLookup() {
		// expect:
		assertSame(token, subject.lookup(created).get());
	}

	@Test
	public void rejectsNewTokenForSaturatedAccount() {
		// setup:
		var account = mock(MerkleAccount.class);

		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT + 1);
		given(account.hasRelationshipWith(misc)).willReturn(false);

		// when:
		var status = subject.relationshipStatus(account, misc);

		// then:
		assertEquals(ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		verify(account).hasRelationshipWith(misc);
	}

	@Test
	public void allowsOldTokenForSaturatedAccount() {
		// setup:
		var account = mock(MerkleAccount.class);

		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT + 1);
		given(account.hasRelationshipWith(misc)).willReturn(true);

		// when:
		var status = subject.relationshipStatus(account, misc);

		// then:
		assertEquals(ResponseCodeEnum.OK, status);
		verify(account).hasRelationshipWith(misc);
	}

	@Test
	public void allowsNewTokenForUndersaturatedAccount() {
		// setup:
		var account = mock(MerkleAccount.class);

		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT - 1);

		// when:
		var status = subject.relationshipStatus(account, misc);

		// then:
		assertEquals(ResponseCodeEnum.OK, status);
		verify(account, never()).hasRelationshipWith(misc);
	}

	@Test
	public void happyPathWorks() {
		// given:
		var req = fullyValidAttempt().build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.OK, result.getStatus());
		assertEquals(created, result.getCreated().get());
	}

	@Test
	public void rejectsMissingAdminKey() {
		// given:
		var req = fullyValidAttempt()
				.clearAdminKey()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_ADMIN_KEY, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	public void rejectsInvalidAdminKey() {
		// given:
		var req = fullyValidAttempt()
				.setAdminKey(Key.getDefaultInstance())
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_ADMIN_KEY, result.getStatus());
		assertTrue(result.getCreated().isEmpty());
	}

	@Test
	public void rejectsInvalidSymbol() {
		// given:
		var req = fullyValidAttempt()
				.setSymbol(IntStream.range(0, MAX_TOKEN_SYMBOL_LENGTH + 1)
						.mapToObj(ignore -> "A")
						.collect(Collectors.joining("")))
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_SYMBOL, result.getStatus());
	}

	@Test
	public void rejectsMissingSymbol() {
		// given:
		var req = fullyValidAttempt()
				.clearSymbol()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_SYMBOL, result.getStatus());
	}

	@Test
	public void rejectsNonAlphanumericSymbol() {
		// given:
		var req = fullyValidAttempt()
				.setSymbol("!!!")
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_SYMBOL, result.getStatus());
	}

	@Test
	public void rejectsMissingTreasury() {
		given(accounts.contains(treasury)).willReturn(false);
		// and:
		var req = fullyValidAttempt()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN, result.getStatus());
	}

	@Test
	public void rejectsDeletedTreasuryAccount() {
		given(account.isDeleted()).willReturn(true);
		// and:
		var req = fullyValidAttempt()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN, result.getStatus());
	}

	@Test
	public void rejectsInsufficientFloat() {
		// given:
		var req = fullyValidAttempt()
				.setFloat(0L)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_FLOAT, result.getStatus());
	}

	@Test
	public void rejectsInvalidDivisibility() {
		// given:
		var req = fullyValidAttempt()
				.setDivisibility(1 << 30)
				.setFloat(1L << 34)
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_DIVISIBILITY, result.getStatus());
	}

	@Test
	public void rejectsFreezeDefaultWithoutFreezeKey() {
		// given:
		var req = fullyValidAttempt()
				.clearFreezeKey()
				.build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY, result.getStatus());
	}

	private TokenCreation.Builder fullyValidAttempt() {
		return TokenCreation.newBuilder()
				.setAdminKey(adminKey)
				.setFreezeKey(freezeKey)
				.setSymbol(symbol)
				.setFloat(tokenFloat)
				.setTreasury(treasury)
				.setDivisibility(divisibility)
				.setFreezeDefault(freezeDefault);
	}
}