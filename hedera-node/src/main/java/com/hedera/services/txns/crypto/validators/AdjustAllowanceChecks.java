/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.txns.crypto.validators;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.List;

import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSerials;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

public class AdjustAllowanceChecks extends AllowanceChecks {
	public AdjustAllowanceChecks(
			final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger,
			final TypedTokenStore tokenStore,
			final GlobalDynamicProperties dynamicProperties) {
		super(nftsLedger, tokenStore, dynamicProperties);
	}

	@Override
	public ResponseCodeEnum validateAmount(final long amount, Token fungibleToken) {
		if (amount < 0) {
			return NEGATIVE_ALLOWANCE_AMOUNT;
		}

		if (fungibleToken != null && amount > fungibleToken.getMaxSupply()) {
			return AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
		}
		return OK;
	}

	@Override
	protected ResponseCodeEnum validateSerialNums(final List<Long> serialNums, final Account ownerAccount,
			final Token token, final boolean approvedForAll) {
		for (var serial : serialNums) {
			final var absoluteSerialNum = serial < 0 ? serial * -1 : serial;
			final var nftId = NftId.withDefaultShardRealm(token.getId().num(), absoluteSerialNum);
			if (!nftsLedger.exists(nftId)) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}

			final var owner = (EntityId) nftsLedger.get(nftId, OWNER);
			if (!ownerAccount.getId().asEntityId().equals(owner)) {
				return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
			}

			if (hasRepeatedSerials(serialNums)) {
				return REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
			}
		}
		return OK;
	}
}
