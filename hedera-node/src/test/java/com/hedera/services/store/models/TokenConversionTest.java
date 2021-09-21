/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.store.models;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.AccountStore;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TokenConversionTest {

	final String MEMO = "memo";
	final String SYMBOL = "HBAR";
	final String NAME = "SomeName";
	final JKey key = TxnHandlingScenario.TOKEN_ADMIN_KT.asJKeyUnchecked();
	@Mock
	private AccountStore store;

	@Test
	void modelToMerkleMappings() {
		final var model = new Token(Id.DEFAULT);
		model.setMemo(MEMO);
		model.setFeeScheduleKey(key);
		model.setAdminKey(key);
		model.setKycKey(key);
		model.setSupplyKey(key);
		model.setWipeKey(key);
		model.setFreezeKey(key);
		model.setSymbol(SYMBOL);
		model.setName(NAME);
		model.setTreasury(new Account(Id.DEFAULT));
		model.setAutoRenewAccount(new Account(Id.DEFAULT));
		model.setAutoRenewPeriod(1000);
		model.setCustomFees(List.of());

		final var merkle = new MerkleToken();
		TokenConversion.mapModelToMerkle(model, merkle);

		assertEquals(MEMO, merkle.memo());
		assertEquals(SYMBOL, merkle.symbol());
		assertEquals(NAME, merkle.name());
		assertEquals(key, merkle.getKycKey());
		assertEquals(key, merkle.getFreezeKey());
		assertEquals(key, merkle.getWipeKey());
		assertEquals(key, merkle.getSupplyKey());
		assertEquals(key, merkle.getFeeScheduleKey());
		assertEquals(key, merkle.getAdminKey());
		assertEquals(EntityId.MISSING_ENTITY_ID, merkle.treasury());
		assertEquals(EntityId.MISSING_ENTITY_ID, merkle.autoRenewAccount());
		assertEquals(1000, merkle.autoRenewPeriod());
		assertEquals(List.of(), merkle.customFeeSchedule());
	}

	@Test
	void merkleToModelMappings() {
		final var merkle = new MerkleToken();
		merkle.setName(NAME);
		merkle.setSymbol(SYMBOL);
		merkle.setMemo(MEMO);
		merkle.setAdminKey(key);
		merkle.setSupplyKey(key);
		merkle.setFeeScheduleKey(key);
		merkle.setFreezeKey(key);
		merkle.setWipeKey(key);
		merkle.setKycKey(key);
		merkle.setTokenType(TokenType.NON_FUNGIBLE_UNIQUE);
		merkle.setSupplyType(TokenSupplyType.FINITE);
		merkle.setTotalSupply(0);
		merkle.setMaxSupply(10);
		merkle.setLastUsedSerialNumber(1);
		merkle.setExpiry(1000);
		merkle.setDecimals(2);
		merkle.setTreasury(EntityId.MISSING_ENTITY_ID);
		final var model = mock(Token.class);

		final var treasuryID = new EntityId(4, 5, 6);
		final var treasury = mock(Account.class);
		given(store.loadAccount(treasuryID.asId())).willReturn(treasury);

		final var autoRenewID = new EntityId(1, 2, 3);
		final var autoRenew = mock(Account.class);
		given(store.loadAccount(autoRenewID.asId())).willReturn(autoRenew);
		merkle.setAutoRenewPeriod(10);

		merkle.setAutoRenewAccount(autoRenewID);
		merkle.setTreasury(treasuryID);
		TokenConversion.mapMerkleToModel(merkle, model);
		TokenConversion.mapMerkleAccountsToModel(model, treasuryID, autoRenewID, store);

		verify(model).initTotalSupply(0);
		verify(model).initSupplyConstraints(TokenSupplyType.FINITE, 10);
		verify(model).setKycKey(merkle.getKycKey());
		verify(model).setFreezeKey(merkle.getFreezeKey());
		verify(model).setSupplyKey(merkle.getSupplyKey());
		verify(model).setWipeKey(merkle.getWipeKey());
		verify(model).setFrozenByDefault(false);
		verify(model).setAdminKey(merkle.getAdminKey());
		verify(model).setFeeScheduleKey(merkle.getFeeScheduleKey());
		verify(model).setType(TokenType.NON_FUNGIBLE_UNIQUE);
		verify(model).setLastUsedSerialNumber(1);
		verify(model).setIsDeleted(false);
		verify(model).setExpiry(merkle.expiry());
		verify(model).setMemo(MEMO);
		verify(model).setAutoRenewPeriod(merkle.autoRenewPeriod());
		verify(model).setSymbol(SYMBOL);
		verify(model).setName(NAME);
		verify(model).setDecimals(merkle.decimals());

		verify(store).loadAccount(autoRenewID.asId());
		verify(model).setAutoRenewAccount(autoRenew);
		verify(store).loadAccount(treasuryID.asId());
		verify(model).setTreasury(treasury);
	}

	@Test
	public void uniqueTokenMapping() {
		final var model = mock(UniqueToken.class);
		final var merkle = new MerkleUniqueToken(
				1, new byte[]{12, 10, 104}, 100, 2);
		TokenConversion.mapUniqueTokenModelFields(model, merkle);
		verify(model).setCreationTime(new RichInstant(0, 100));
		verify(model).setOwner(merkle.getOwner().asId());
		verify(model).setMetadata(new byte[]{12, 10, 104});
	}

}