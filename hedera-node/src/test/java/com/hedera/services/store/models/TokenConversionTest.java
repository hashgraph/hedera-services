package com.hedera.services.store.models;

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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.AccountStore;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.services.store.models.TokenConversion.fromMerkle;
import static com.hedera.services.store.models.TokenConversion.fromMerkleUnique;
import static com.hedera.services.store.models.TokenConversion.fromToken;
import static com.hedera.services.store.models.TokenConversion.fromUniqueToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class TokenConversionTest {
	private long tokenNum = 777L;
	final long serialNum = 10L;
	private Id tokenId = new Id(0,0,tokenNum);
	private Token token = new Token(tokenId);
	private EntityId owner = new EntityId(0, 0, 3);
	private byte[] metadata = "Test NFT".getBytes();
	private UniqueToken uniqueToken = new UniqueToken(tokenId, serialNum);
	private RichInstant timestamp = RichInstant.fromJava(Instant.ofEpochSecond(1_234_567L));
	private final long expiry = 1_234_567L;
	private final long treasuryAccountNum = 2_234L;
	private final long autoRenewAccountNum = 3_234L;
	private final Id treasuryId = new Id(0, 0, treasuryAccountNum);
	private final Id autoRenewId = new Id(0, 0, autoRenewAccountNum);
	private final Account treasuryAccount = new Account(treasuryId);
	private final Account autoRenewAccount = new Account(autoRenewId);

	private final JKey kycKey = TxnHandlingScenario.TOKEN_KYC_KT.asJKeyUnchecked();
	private final JKey freezeKey = TxnHandlingScenario.TOKEN_FREEZE_KT.asJKeyUnchecked();
	private final JKey supplyKey = TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKeyUnchecked();
	private final JKey wipeKey = TxnHandlingScenario.TOKEN_WIPE_KT.asJKeyUnchecked();
	private final JKey adminKey = TxnHandlingScenario.TOKEN_ADMIN_KT.asJKeyUnchecked();
	private final JKey feeScheduleKey = TxnHandlingScenario.TOKEN_ADMIN_KT.asJKeyUnchecked();
	private final long tokenSupply = 777L;
	private final long autoRenewPeriod = 1234L;
	private final String name = "Testing123";
	private final String symbol = "T123";
	private final String memo = "memo";
	private final boolean freezeDefault = true;
	private final boolean kycGrantedDefault = true;

	private MerkleToken merkleToken;
	private MerkleUniqueToken merkleUniqueToken;
	private AccountStore accountStore;

	@BeforeEach
	void setUp() {
		setupToken();
		accountStore = mock(AccountStore.class);

		given(accountStore.loadAccount(treasuryId)).willReturn(treasuryAccount);
		given(accountStore.loadAccount(autoRenewId)).willReturn(autoRenewAccount);
	}
	private void setupToken() {
		merkleToken = new MerkleToken(
				expiry, tokenSupply, 0,
				symbol, name,
				freezeDefault, kycGrantedDefault,
				new EntityId(0, 0, treasuryAccountNum));
		merkleToken.setAutoRenewAccount(new EntityId(0, 0, autoRenewAccountNum));
		merkleToken.setSupplyKey(supplyKey);
		merkleToken.setKycKey(kycKey);
		merkleToken.setWipeKey(wipeKey);
		merkleToken.setAdminKey(adminKey);
		merkleToken.setFreezeKey(freezeKey);
		merkleToken.setFeeScheduleKey(feeScheduleKey);
		merkleToken.setMemo(memo);
		merkleToken.setAutoRenewPeriod(autoRenewPeriod);

		token.setName(name);
		token.setSymbol(symbol);
		token.setMemo(memo);
		token.setTreasury(treasuryAccount);
		token.setAutoRenewAccount(autoRenewAccount);
		token.setTotalSupply(tokenSupply);
		token.setKycKey(kycKey);
		token.setSupplyKey(supplyKey);
		token.setFreezeKey(freezeKey);
		token.setWipeKey(wipeKey);
		token.setAdminKey(adminKey);
		token.setFeeScheduleKey(feeScheduleKey);
		token.setFrozenByDefault(freezeDefault);
		token.setKycGrantedByDefault(kycGrantedDefault);
		token.setIsDeleted(false);
		token.setExpiry(expiry);
		token.setAutoRenewPeriod(autoRenewPeriod);

		merkleUniqueToken = new MerkleUniqueToken(owner, metadata, timestamp);

		uniqueToken.setOwner(owner.asId());
		uniqueToken.setMetadata(metadata);
		uniqueToken.setCreationTime(timestamp);
	}

	@Test
	void conversionFromMerkleWorks() {
		var newToken = fromMerkle(merkleToken, tokenId, accountStore);
		assertEquals(token.toString(), newToken.toString());
	}

	@Test
	void conversionFromTokenWorks() {
		var newMerkleToken = fromToken(token);

		assertEquals(merkleToken, newMerkleToken);
	}

	@Test
	void conversionCycleFromMerkleWorks() {
		var tokenSubject = fromMerkle(merkleToken, tokenId, accountStore);
		var newMerkleToken = fromToken(tokenSubject);

		assertEquals(merkleToken, newMerkleToken);
	}

	@Test
	void conversionCycleFromTokenWorks() {
		var newMerkleToken = fromToken(token);

		var newToken = fromMerkle(newMerkleToken, tokenId, accountStore);

		assertEquals(token.toString(), newToken.toString());
	}

	@Test
	void conversionFromMerkleUniqueWorks() {
		var newUniqueToken = fromMerkleUnique(merkleUniqueToken, tokenId, serialNum);

		assertEquals(uniqueToken.toString(), newUniqueToken.toString());
	}

	@Test
	void conversionFromUniqueTokenWorks() {
		var newMerkleUniqueToken = fromUniqueToken(uniqueToken);

		assertEquals(merkleUniqueToken.toString(), newMerkleUniqueToken.toString());
	}

	@Test
	void conversionCycleFromMerkleUniqueWorks() {
		var newUniqueToken = fromMerkleUnique(merkleUniqueToken, tokenId, serialNum);
		var newMerkleUniqueToken = fromUniqueToken(newUniqueToken);

		assertEquals(merkleUniqueToken.toString(), newMerkleUniqueToken.toString());
	}

	@Test
	void conversionCycleFromUniqueTokenWorks() {
		var newMerkleUniqueToken = fromUniqueToken(uniqueToken);
		var newUniqueToken = fromMerkleUnique(newMerkleUniqueToken, tokenId, serialNum);

		assertEquals(uniqueToken.toString(), newUniqueToken.toString());
	}
}
