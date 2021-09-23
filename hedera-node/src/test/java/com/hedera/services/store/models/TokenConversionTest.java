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
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.services.store.models.TokenConversion.fromMerkle;
import static com.hedera.services.store.models.TokenConversion.fromMerkleUnique;
import static com.hedera.services.store.models.TokenConversion.fromToken;
import static com.hedera.services.store.models.TokenConversion.fromUniqueToken;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenConversionTest {
	private static final long TOKEN_NUM = 777L;
	private static final long SERIAL_NUM = 10L;
	private static final Id TOKEN_ID = new Id(0, 0, TOKEN_NUM);
	private static final Token TOKEN = new Token(TOKEN_ID);
	private static final EntityId OWNER = new EntityId(0, 0, 3);
	private static final byte[] METADATA = "Test NFT".getBytes();
	private static final UniqueToken UNIQUE_TOKEN = new UniqueToken(TOKEN_ID, SERIAL_NUM);
	private static final RichInstant CREATION_TIMESTAMP = RichInstant.fromJava(Instant.ofEpochSecond(1_234_567L));
	private static final long EXPIRY = 1_234_567L;
	private static final long TREASURY_ACCOUNT_NUM = 2_234L;
	private static final long AUTORENEW_ACCOUNT_NUM = 3_234L;
	private static final Id TREASURY_ID = new Id(0, 0, TREASURY_ACCOUNT_NUM);
	private static final Id AUTORENEW_ID = new Id(0, 0, AUTORENEW_ACCOUNT_NUM);
	private static final Account TREASURY_ACCOUNT = new Account(TREASURY_ID);
	private static final Account AUTORENEW_ACCOUNT = new Account(AUTORENEW_ID);

	private static final JKey KYC_KEY = TxnHandlingScenario.TOKEN_KYC_KT.asJKeyUnchecked();
	private static final JKey FREEZE_KEY = TxnHandlingScenario.TOKEN_FREEZE_KT.asJKeyUnchecked();
	private static final JKey SUPPLY_KEY = TxnHandlingScenario.TOKEN_SUPPLY_KT.asJKeyUnchecked();
	private static final JKey WIPE_KEY = TxnHandlingScenario.TOKEN_WIPE_KT.asJKeyUnchecked();
	private static final JKey ADMIN_KEY = TxnHandlingScenario.TOKEN_ADMIN_KT.asJKeyUnchecked();
	private static final JKey FEE_SCHEDULE_KEY = TxnHandlingScenario.TOKEN_ADMIN_KT.asJKeyUnchecked();
	private static final long TOKEN_SUPPLY = 777L;
	private static final long AUTORENEW_PERIOD = 1234L;
	private static final String NAME = "Testing123";
	private static final String SYMBOL = "T123";
	private static final String MEMO = "memo";
	private static final boolean FREEZE_DEFAULT = true;
	private static final boolean KYC_GRANTED_DEFAULT = true;

	private MerkleToken merkleToken;
	private MerkleUniqueToken merkleUniqueToken;

	@BeforeEach
	void setUp() {
		setupToken();
	}

	private void setupToken() {
		merkleToken = new MerkleToken(
				EXPIRY, TOKEN_SUPPLY, 0,
				SYMBOL, NAME,
				FREEZE_DEFAULT, KYC_GRANTED_DEFAULT,
				new EntityId(0, 0, TREASURY_ACCOUNT_NUM));
		merkleToken.setAutoRenewAccount(new EntityId(0, 0, AUTORENEW_ACCOUNT_NUM));
		merkleToken.setSupplyKey(SUPPLY_KEY);
		merkleToken.setKycKey(KYC_KEY);
		merkleToken.setWipeKey(WIPE_KEY);
		merkleToken.setAdminKey(ADMIN_KEY);
		merkleToken.setFreezeKey(FREEZE_KEY);
		merkleToken.setFeeScheduleKey(FEE_SCHEDULE_KEY);
		merkleToken.setMemo(MEMO);
		merkleToken.setAutoRenewPeriod(AUTORENEW_PERIOD);

		TOKEN.setName(NAME);
		TOKEN.setSymbol(SYMBOL);
		TOKEN.setMemo(MEMO);
		TOKEN.setTreasury(TREASURY_ACCOUNT);
		TOKEN.setAutoRenewAccount(AUTORENEW_ACCOUNT);
		TOKEN.setTotalSupply(TOKEN_SUPPLY);
		TOKEN.setKycKey(KYC_KEY);
		TOKEN.setSupplyKey(SUPPLY_KEY);
		TOKEN.setFreezeKey(FREEZE_KEY);
		TOKEN.setWipeKey(WIPE_KEY);
		TOKEN.setAdminKey(ADMIN_KEY);
		TOKEN.setFeeScheduleKey(FEE_SCHEDULE_KEY);
		TOKEN.setFrozenByDefault(FREEZE_DEFAULT);
		TOKEN.setKycGrantedByDefault(KYC_GRANTED_DEFAULT);
		TOKEN.setIsDeleted(false);
		TOKEN.setExpiry(EXPIRY);
		TOKEN.setAutoRenewPeriod(AUTORENEW_PERIOD);

		merkleUniqueToken = new MerkleUniqueToken(OWNER, METADATA, CREATION_TIMESTAMP);

		UNIQUE_TOKEN.setOwner(OWNER.asId());
		UNIQUE_TOKEN.setMetadata(METADATA);
		UNIQUE_TOKEN.setCreationTime(CREATION_TIMESTAMP);
	}

	@Test
	void conversionFromMerkleWorks() {
		final var newToken = fromMerkle(merkleToken, TOKEN_ID, TREASURY_ACCOUNT, AUTORENEW_ACCOUNT);

		assertEquals(TOKEN.toString(), newToken.toString());
	}

	@Test
	void conversionFromTokenWorks() {
		final var newMerkleToken = fromToken(TOKEN);

		assertEquals(merkleToken, newMerkleToken);
	}

	@Test
	void conversionCycleFromMerkleWorks() {
		final var tokenSubject = fromMerkle(merkleToken, TOKEN_ID, TREASURY_ACCOUNT, AUTORENEW_ACCOUNT);
		final var newMerkleToken = fromToken(tokenSubject);

		assertEquals(merkleToken, newMerkleToken);
	}

	@Test
	void conversionCycleFromTokenWorks() {
		TOKEN.setAutoRenewAccount(null);
		final var newMerkleToken = fromToken(TOKEN);

		final var newToken = fromMerkle(newMerkleToken, TOKEN_ID, TREASURY_ACCOUNT, null);

		assertEquals(TOKEN.toString(), newToken.toString());
	}

	@Test
	void conversionFromMerkleUniqueWorks() {
		final var newUniqueToken = fromMerkleUnique(merkleUniqueToken, TOKEN_ID, SERIAL_NUM);

		assertEquals(UNIQUE_TOKEN.toString(), newUniqueToken.toString());
	}

	@Test
	void conversionFromUniqueTokenWorks() {
		final var newMerkleUniqueToken = fromUniqueToken(UNIQUE_TOKEN);

		assertEquals(merkleUniqueToken.getOwner(), newMerkleUniqueToken.getOwner());
		assertEquals(merkleUniqueToken.getCreationTime(), newMerkleUniqueToken.getCreationTime());
		assertEquals(merkleUniqueToken.getMetadata(), newMerkleUniqueToken.getMetadata());
	}

	@Test
	void conversionCycleFromMerkleUniqueWorks() {
		final var newUniqueToken = fromMerkleUnique(merkleUniqueToken, TOKEN_ID, SERIAL_NUM);
		final var newMerkleUniqueToken = fromUniqueToken(newUniqueToken);

		assertEquals(merkleUniqueToken.getOwner(), newMerkleUniqueToken.getOwner());
		assertEquals(merkleUniqueToken.getCreationTime(), newMerkleUniqueToken.getCreationTime());
		assertEquals(merkleUniqueToken.getMetadata(), newMerkleUniqueToken.getMetadata());
	}

	@Test
	void conversionCycleFromUniqueTokenWorks() {
		final var newMerkleUniqueToken = fromUniqueToken(UNIQUE_TOKEN);
		final var newUniqueToken = fromMerkleUnique(newMerkleUniqueToken, TOKEN_ID, SERIAL_NUM);

		assertEquals(UNIQUE_TOKEN.getOwner(), newUniqueToken.getOwner());
		assertEquals(UNIQUE_TOKEN.getCreationTime(), newUniqueToken.getCreationTime());
		assertEquals(UNIQUE_TOKEN.getMetadata(), newUniqueToken.getMetadata());
		assertEquals(UNIQUE_TOKEN.getSerialNumber(), newUniqueToken.getSerialNumber());
		assertEquals(UNIQUE_TOKEN.getTokenId(), newUniqueToken.getTokenId());
		assertEquals(UNIQUE_TOKEN.toString(), newUniqueToken.toString());
	}
}
