package com.hedera.services.store.tokens.unique;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.utils.invertible_fchashmap.FCInvertibleHashMap;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.services.ledger.accounts.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_KYC_GRANTED;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.state.merkle.MerkleUniqueTokenId.fromNftID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class UniqueTokenStoreTest {

	UniqueTokenStore store;

	EntityIdSource ids;
	GlobalDynamicProperties properties;

	FCMap<MerkleEntityId, MerkleToken> tokens;
	FCInvertibleHashMap<MerkleUniqueTokenId, MerkleUniqueToken, OwnerIdentifier> nfTokens;

	HederaLedger hederaLedger;
	TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;

	MerkleUniqueTokenId nftId;
	MerkleUniqueToken nft;
	MerkleToken token;
	EntityId eId;
	NftID misc = IdUtils.asNftID("3.2.1", 4);
	TokenID tokenID = IdUtils.asToken("1.2.3");
	AccountID treasury = IdUtils.asAccount("1.2.3");
	AccountID sponsor = IdUtils.asAccount("1.2.666");
	Pair<AccountID, TokenID> sponsorPair = asTokenRel(sponsor, tokenID);
	long sponsorBalance = 1_000;
	ByteString metadata = ByteString.copyFromUtf8("hello");


	@BeforeEach
	void setUp() {
		eId = mock(EntityId.class);
		nftId = mock(MerkleUniqueTokenId.class);
		nft = mock(MerkleUniqueToken.class);
		given(nft.getOwner()).willReturn(EntityId.fromGrpcAccountId(treasury));
		given(nft.getMetadata()).willReturn(metadata);
		token = mock(MerkleToken.class);
		given(token.isDeleted()).willReturn(false);
		given(token.treasury()).willReturn(EntityId.fromGrpcAccountId(sponsor));
		given(token.totalSupply()).willReturn(2000L);
		given(token.hasSupplyKey()).willReturn(true);

		properties = mock(GlobalDynamicProperties.class);

		ids = mock(EntityIdSource.class);

		hederaLedger = mock(HederaLedger.class);

		tokens = (FCMap<MerkleEntityId, MerkleToken>) mock(FCMap.class);
		given(tokens.containsKey(MerkleEntityId.fromTokenId(tokenID))).willReturn(true);
		given(tokens.get(MerkleEntityId.fromTokenId(tokenID))).willReturn(token);
		given(tokens.getForModify(any())).willReturn(token);

		nfTokens = mock(FCInvertibleHashMap.class);


		accountsLedger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>) mock(TransactionalLedger.class);
		given(accountsLedger.exists(sponsor)).willReturn(true);
		given(accountsLedger.get(treasury, IS_DELETED)).willReturn(false);

		tokenRelsLedger = mock(TransactionalLedger.class);
		given(tokenRelsLedger.get(sponsorPair, TOKEN_BALANCE)).willReturn(sponsorBalance);
		given(tokenRelsLedger.get(sponsorPair, IS_FROZEN)).willReturn(false);
		given(tokenRelsLedger.get(sponsorPair, IS_KYC_GRANTED)).willReturn(true);

		store = new UniqueTokenStore(ids, TestContextValidator.TEST_VALIDATOR, properties, () -> tokens, () -> nfTokens, tokenRelsLedger);
		store.setHederaLedger(hederaLedger);
		store.setAccountsLedger(accountsLedger);

		given(nft.getOwner()).willReturn(EntityId.fromGrpcAccountId(treasury));
		given(nft.getMetadata()).willReturn(metadata);

		given(nfTokens.containsKey(fromNftID(misc))).willReturn(true);
		given(nfTokens.get(fromNftID(misc))).willReturn(nft);

	}

	@Test
	void superAdjustBalanceFails() {
		given(tokenRelsLedger.get(sponsorPair, IS_FROZEN)).willReturn(true);
		var res = store.mint(singleTokenTxBody(), RichInstant.fromJava(Instant.now()));
		assertEquals(ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN, res.getStatus());
		verify(token, times(0)).setSerialNum(anyLong());
	}

	@Test
	void mintOne() {
		var res = store.mint(singleTokenTxBody(), RichInstant.fromJava(Instant.now()));
		assertEquals(ResponseCodeEnum.OK, res.getStatus());
		verify(token, times(1)).setSerialNum(anyLong());
	}

	@Test
	void verifyPopsLastMintedNums() {
		given(token.getCurrentSerialNum()).willReturn(5L);
		var lastSerials = store.mint(multipleTokenTxBody(), RichInstant.fromJava(Instant.now()));
		assertEquals(2, lastSerials.getCreated().get().size());
	}

	@Test
	void verifyIncrementSerialNumGetsCalled() {
//		MerkleToken tkn = new MerkleToken(100L, 100L, 2,
//				"tkn", "name", false, true,
//				EntityId.fromGrpcAccountId(sponsor));
//		var jKey = mock(JKey.class);
//		tkn.setSupplyKey(jKey);

		var tbody = multipleTokenTxBody();
		given(store.get(tokenID)).willReturn(token);
		given(tokens.getForModify(tokenID)).willReturn(token);

		var res = store.mint(tbody, RichInstant.fromJava(Instant.now()));
		assertEquals(ResponseCodeEnum.OK, res.getStatus());
		var lastSerials = res.getCreated().get();
		assertEquals(2, lastSerials.size());
		verify(token, times(1)).setSerialNum(anyLong());
	}

	@Test
	void mintMany() {
		var res = store.mint(multipleTokenTxBody(), RichInstant.fromJava(Instant.now()));
		assertEquals(ResponseCodeEnum.OK, res.getStatus());
		verify(token, times(1)).setSerialNum(anyLong());
	}

	@Test
	void mintManyFail() {
		var res = store.mint(multipleTokenFailTxBody(), RichInstant.fromJava(Instant.now()));
		assertNotEquals(res, ResponseCodeEnum.OK);
		verify(token, times(0)).setSerialNum(anyLong());
		verify(nfTokens, times(0)).put(any(), any());
	}

	@Test
	void mintFailsIfNoSupplyKey() {
		given(token.hasSupplyKey()).willReturn(false);
		var res = store.mint(singleTokenTxBody(), RichInstant.fromJava(Instant.now()));
		assertEquals(ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY, res.getStatus());
		verify(token, times(0)).setSerialNum(anyLong());
	}

	@Test
	void wipe() {
		var res = store.wipe(treasury, tokenID, 1, true);
		assertNull(res);
	}

	private TokenMintTransactionBody singleTokenTxBody() {
		return TokenMintTransactionBody.newBuilder()
				.addMetadata(ByteString.copyFromUtf8("memo"))
				.setAmount(123)
				.setToken(tokenID)
				.build();
	}

	private TokenMintTransactionBody multipleTokenTxBody() {
		return TokenMintTransactionBody.newBuilder()
				.setToken(tokenID)
				.addMetadata(ByteString.copyFromUtf8("memo1"))
				.addMetadata(ByteString.copyFromUtf8("memo2"))
				.build();
	}

	private TokenMintTransactionBody multipleTokenFailTxBody() {
		return TokenMintTransactionBody.newBuilder()
				.setToken(tokenID)
				.setAmount(123)
				.addMetadata(ByteString.copyFromUtf8("memo1"))
				.addMetadata(ByteString.copyFromUtf8("memo1"))
				.build();
	}


	@Test
	public void getDelegates() {
		// expect:
		assertSame(nft, store.get(misc));
		// and:
		verify(nfTokens).containsKey(fromNftID(misc));
		verify(nfTokens).get(fromNftID(misc));
	}

	@Test
	public void getThrowsIseOnMissing() {
		// given:
		given(nfTokens.containsKey(fromNftID(misc))).willReturn(false);

		// expect:
		assertThrows(IllegalArgumentException.class, () -> store.get(misc));
		// and:
		verify(nfTokens).containsKey(fromNftID(misc));
		verify(nfTokens, never()).get(fromNftID(misc));
	}

	@Test
	public void validExistence() {
		// expect:
		assertTrue(store.nftExists(misc));
		// and:
		verify(nfTokens).containsKey(fromNftID(misc));
	}


}