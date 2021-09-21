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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.store.models.AccountConversion.mapMerkleToModel;
import static com.hedera.services.store.models.AccountConversion.mapModelToMerkle;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountConversionTest {
	final JKey key = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKeyUnchecked();

	@Test
	void merkleToModelMapping() {
		final var merkle = new MerkleAccount();
		final var tokens = new MerkleAccountTokens();
		merkle.setSmartContract(true);
		merkle.setMemo("memo");
		merkle.setReceiverSigRequired(true);
		merkle.setProxy(EntityId.MISSING_ENTITY_ID);
		merkle.setBalanceUnchecked(10);
		merkle.setNftsOwned(10);
		merkle.setAutoRenewSecs(10);
		merkle.setExpiry(10);
		merkle.setAccountKey(key);
		merkle.setMaxAutomaticAssociations(10);
		merkle.setAlreadyUsedAutomaticAssociations(5);
		merkle.setDeleted(false);
		merkle.setTokens(tokens);
		final var model = mock(Account.class);
		mapMerkleToModel(merkle, model);

		verify(model).setExpiry(10);
		verify(model).setBalance(10);
		verify(model).setAssociatedTokens(tokens.getIds());
		verify(model).setOwnedNfts(10);
		verify(model).setMaxAutomaticAssociations(10);
		verify(model).setAlreadyUsedAutomaticAssociations(5);
		verify(model).setProxy(EntityId.MISSING_ENTITY_ID.asId());
		verify(model).setReceiverSigRequired(true);
		verify(model).setKey(key);
		verify(model).setMemo("memo");
		verify(model).setAutoRenewSecs(10);
		verify(model).setDeleted(false);
		verify(model).setSmartContract(true);
	}

	@Test
	void modelToMerkleMapping() {
		final var tokens = new CopyOnWriteIds();
		final var model = new Account(Id.DEFAULT);
		final var merkle = mock(MerkleAccount.class);
		final var merkleAccountState = mock(MerkleAccountState.class);
		given(merkle.state()).willReturn(merkleAccountState);
		model.setSmartContract(true);
		model.setDeleted(true);
		model.setReceiverSigRequired(true);
		model.setKey(key);
		model.setAssociatedTokens(tokens);
		model.setProxy(Id.DEFAULT);
		model.setBalance(10);
		model.setOwnedNfts(10);
		model.setAutoRenewSecs(10);
		model.setExpiry(10);
		model.setMemo("memo");
		model.setMaxAutomaticAssociations(10);
		model.setAlreadyUsedAutomaticAssociations(5);

		mapModelToMerkle(model, merkle);

		merkle.setProxy(EntityId.MISSING_ENTITY_ID);
		verify(merkle).setExpiry(10);
		verify(merkle).setBalanceUnchecked(10);
		verify(merkle).setNftsOwned(10);
		verify(merkle).setMaxAutomaticAssociations(10);
		verify(merkle).setAlreadyUsedAutomaticAssociations(5);
		verify(merkleAccountState).setAccountKey(key);
		verify(merkle).setReceiverSigRequired(true);
		verify(merkle).setDeleted(true);
		verify(merkle).setAutoRenewSecs(10);
	}
}