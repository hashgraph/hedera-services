package com.hedera.services.txns.crypto.validators;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.absolute;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.hasRepeatedSerials;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;

@Singleton
public class AdjustAllowanceChecks extends AllowanceChecks {
	@Inject
	public AdjustAllowanceChecks(final GlobalDynamicProperties dynamicProperties,
			final OptionValidator validator) {
		super(dynamicProperties, validator);
	}

	@Override
	ResponseCodeEnum validateTokenAmount(final Account ownerAccount,
			final long amount,
			final MerkleToken merkleToken,
			final Id token,
			final Id spender) {
		final var existingAllowances = ownerAccount.getFungibleTokenAllowances();

		final var key = FcTokenAllowanceId.from(token.asEntityNum(), spender.asEntityNum());
		final var existingAllowance = existingAllowances.containsKey(key) ? existingAllowances.get(key) : 0;
		final long aggregatedAmount = amount + existingAllowance;

		if (aggregatedAmount < 0) {
			return NEGATIVE_ALLOWANCE_AMOUNT;
		}

		if (merkleToken.supplyType().equals(TokenSupplyType.FINITE) && aggregatedAmount > merkleToken.maxSupply()) {
			return AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
		}
		return OK;
	}

	@Override
	ResponseCodeEnum validateSerialNums(
			final List<Long> serialNums,
			final Account ownerAccount,
			final MerkleToken merkleToken,
			final StateView view,
			final Id token,
			final Id spender) {
		if (hasRepeatedSerials(serialNums)) {
			return REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
		}

		if (serialNums.isEmpty()) {
			return EMPTY_ALLOWANCES;
		}

		final var existingAllowances = ownerAccount.getNftAllowances();

		final var key = FcTokenAllowanceId.from(token.asEntityNum(), spender.asEntityNum());
		final var existingSerials = existingAllowances.containsKey(key) ?
				existingAllowances.get(key).getSerialNumbers() : new ArrayList<Long>();

		for (var serial : serialNums) {
			var absoluteSerial = absolute(serial);
			final var nftId = NftId.withDefaultShardRealm(token.num(), absoluteSerial);

			if ((serial < 0 && !existingSerials.contains(absoluteSerial)) || absoluteSerial == 0) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}
			if (serial > 0 && existingSerials.contains(absoluteSerial)) {
				return REPEATED_SERIAL_NUMS_IN_NFT_ALLOWANCES;
			}
			final var merkleUniqueToken = view.loadNft(nftId);
			if (merkleUniqueToken == null) {
				return INVALID_TOKEN_NFT_SERIAL_NUMBER;
			}
			if (!validOwner(merkleUniqueToken, ownerAccount, merkleToken)) {
				return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
			}
		}

		return OK;
	}

	@Override
	ResponseCodeEnum validateAmount(final long amount, final Account ownerAccount, final Id spender) {
		final var existingAllowances = ownerAccount.getCryptoAllowances();
		final var key = spender.asEntityNum();

		final var existingAmount = existingAllowances.containsKey(key) ? existingAllowances.get(key) : 0;

		if (amount + existingAmount < 0) {
			return NEGATIVE_ALLOWANCE_AMOUNT;
		}
		return OK;
	}
}
