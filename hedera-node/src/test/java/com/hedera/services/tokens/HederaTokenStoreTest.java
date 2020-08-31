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
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenScopedPropertyValue;
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
import org.mockito.ArgumentCaptor;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.state.merkle.MerkleEntityId.fromTokenId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SETTING_NEGATIVE_ACCOUNT_BALANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class HederaTokenStoreTest {
	EntityIdSource ids;
	GlobalDynamicProperties properties;
	FCMap<MerkleEntityId, MerkleToken> tokens;
	TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

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
	TokenID pending = IdUtils.asToken("1.2.555555");
	int MAX_TOKENS_PER_ACCOUNT = 100;
	int MAX_TOKEN_SYMBOL_LENGTH = 10;

	HederaTokenStore subject;

	@BeforeEach
	public void setup() {
		adminKey = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
		freezeKey = TxnHandlingScenario.CARELESS_SIGNING_PAYER_KT.asKey();

		token = mock(MerkleToken.class);

		ids = mock(EntityIdSource.class);
		given(ids.newTokenId(sponsor)).willReturn(created);

		account = mock(MerkleAccount.class);

		ledger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>)mock(TransactionalLedger.class);
		given(ledger.exists(treasury)).willReturn(true);
		given(ledger.exists(sponsor)).willReturn(true);
		given(ledger.get(treasury, IS_DELETED)).willReturn(false);
		given(ledger.getDetachedTokenView(treasury)).willReturn(account);

		tokens = (FCMap<MerkleEntityId, MerkleToken>) mock(FCMap.class);
		given(tokens.get(fromTokenId(created))).willReturn(token);
		given(tokens.containsKey(fromTokenId(misc))).willReturn(true);
		given(tokens.get(fromTokenId(misc))).willReturn(token);

		properties = mock(GlobalDynamicProperties.class);
		given(properties.maxTokensPerAccount()).willReturn(MAX_TOKENS_PER_ACCOUNT);
		given(properties.maxTokenSymbolLength()).willReturn(MAX_TOKEN_SYMBOL_LENGTH);

		subject = new HederaTokenStore(ids, properties, () -> tokens);
		subject.setLedger(ledger);
	}

	@Test
	public void getDelegates() {
		// expect:
		assertSame(token, subject.get(misc));
	}

	@Test
	public void getThrowsIseOnMissing() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> subject.get(misc));
	}

	@Test
	public void getCanReturnPending() {
		// setup:
		subject.pendingId = pending;
		subject.pendingCreation = token;

		// expect:
		assertSame(token, subject.get(pending));
	}

	@Test
	public void existenceCheckIncludesPending() {
		// setup:
		subject.pendingId = pending;

		// expect:
		assertTrue(subject.exists(pending));
	}

	@Test
	public void adjustabilityCheckRejectsMissingAccount() {
		given(ledger.exists(sponsor)).willReturn(false);

		// when:
		var status = subject.adjustBalance(sponsor, misc, 1);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_ACCOUNT_ID, status);
	}

	@Test
	public void adjustabilityCheckRejectsMissingToken() {
		given(tokens.containsKey(fromTokenId(misc))).willReturn(false);

		// when:
		var status = subject.adjustBalance(sponsor, misc, 1);

		// expect:
		assertEquals(ResponseCodeEnum.INVALID_TOKEN_ID, status);
	}

	@Test
	public void adjustabilityCheckRejectsSaturatedAccount() {
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT + 1);
		given(account.hasRelationshipWith(misc)).willReturn(false);

		// when:
		var status = subject.adjustBalance(treasury, misc, 1);

		// then:
		assertEquals(ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, status);
		verify(account).hasRelationshipWith(misc);
	}

	@Test
	public void allowsNewTokenForUndersaturatedAccount() {
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT - 1);
		given(account.validityOfAdjustment(misc, token, 1)).willReturn(OK);

		// when:
		var status = subject.adjustBalance(treasury, misc, 1);

		// then:
		assertEquals(OK, status);
		verify(account, never()).hasRelationshipWith(misc);
	}

	@Test
	public void allowsAdjustingOldRelationships() {
		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT + 1);
		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(account.validityOfAdjustment(misc, token, 1)).willReturn(OK);

		// when:
		var status = subject.adjustBalance(treasury, misc, 1);

		// then:
		assertEquals(OK, status);
		verify(account).hasRelationshipWith(misc);
	}

	@Test
	public void refusesInvalidAdjustment() {
		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(account.validityOfAdjustment(misc, token, -1)).willReturn(SETTING_NEGATIVE_ACCOUNT_BALANCE);

		// when:
		var status = subject.adjustBalance(treasury, misc, -1);

		// then:
		assertEquals(SETTING_NEGATIVE_ACCOUNT_BALANCE, status);
	}

	@Test
	public void performsValidAdjustment() {
		// setup:
		ArgumentCaptor<TokenScopedPropertyValue> captor = ArgumentCaptor.forClass(TokenScopedPropertyValue.class);

		given(account.hasRelationshipWith(misc)).willReturn(true);
		given(account.validityOfAdjustment(misc, token, -1)).willReturn(OK);

		// when:
		subject.adjustBalance(treasury, misc, -1);

		// then:
//		verify(account).adjustTokenBalance(argThat(misc::equals), argThat(token::equals), captor.capture());
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
	public void allowsOldTokenForSaturatedAccount() {
		// setup:
		var account = mock(MerkleAccount.class);

		given(account.numTokenRelationships()).willReturn(MAX_TOKENS_PER_ACCOUNT + 1);
		given(account.hasRelationshipWith(misc)).willReturn(true);

		// when:
		var status = subject.relationshipStatus(account, misc);

		// then:
		assertEquals(OK, status);
		verify(account).hasRelationshipWith(misc);
	}

	@Test
	public void happyPathWorks() {
		// given:
		var req = fullyValidAttempt().build();

		// when:
		var result = subject.createProvisionally(req, sponsor);

		// then:
		assertEquals(OK, result.getStatus());
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
		given(ledger.exists(treasury)).willReturn(false);
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
		given(ledger.get(treasury, IS_DELETED)).willReturn(true);

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