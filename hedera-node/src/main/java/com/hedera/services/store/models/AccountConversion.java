package com.hedera.services.store.models;

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

import com.hedera.services.state.merkle.MerkleAccount;

/**
 * A utility class used to convert between {@link Account} and {@link com.hedera.services.state.merkle.MerkleAccount}
 */
public class AccountConversion {
	
	public static void mapMerkleToModel(MerkleAccount merkle, Account model) {
		model.setExpiry(merkle.getExpiry());
		model.setBalance(merkle.getBalance());
		model.setAssociatedTokens(merkle.tokens().getIds().copy());
		model.setOwnedNfts(merkle.getNftsOwned());
		model.setMaxAutomaticAssociations(merkle.getMaxAutomaticAssociations());
		model.setAlreadyUsedAutomaticAssociations(merkle.getAlreadyUsedAutoAssociations());
		if (merkle.getProxy() != null) {
			model.setProxy(merkle.getProxy().asId());
		}
		model.setReceiverSigRequired(merkle.isReceiverSigRequired());
		model.setKey(merkle.state().key());
		model.setMemo(merkle.getMemo());
		model.setAutoRenewSecs(merkle.getAutoRenewSecs());
		model.setDeleted(merkle.isDeleted());
		model.setSmartContract(merkle.isSmartContract());
	} 
	
	public static void mapModelToMerkle(Account model, MerkleAccount merkle) {
		if (model.getProxy() != null) {
			merkle.setProxy(model.getProxy().asEntityId());
		}
		merkle.setExpiry(model.getExpiry());
		merkle.setBalanceUnchecked(model.getBalance());
		merkle.setNftsOwned(model.getOwnedNfts());
		merkle.setMaxAutomaticAssociations(model.getMaxAutomaticAssociations());
		merkle.setAlreadyUsedAutomaticAssociations(model.getAlreadyUsedAutomaticAssociations());
		merkle.state().setAccountKey(model.getKey());
		merkle.setReceiverSigRequired(model.isReceiverSigRequired());
		merkle.setDeleted(model.isDeleted());
		merkle.setAutoRenewSecs(model.getAutoRenewSecs());
	}
}
