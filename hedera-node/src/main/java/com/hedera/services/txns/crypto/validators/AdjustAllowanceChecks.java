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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class AdjustAllowanceChecks extends AllowanceChecks {
	@Inject
	public AdjustAllowanceChecks(final GlobalDynamicProperties dynamicProperties,
			final OptionValidator validator) {
		super(dynamicProperties, validator);
	}

	@Override
	public ResponseCodeEnum validateTokenAmount(final Account ownerAccount,
			final long amount,
			final Token token,
			final Id spender) {
		final var existingAllowances = ownerAccount.getFungibleTokenAllowances();

		final var key = FcTokenAllowanceId.from(token.getId().asEntityNum(), spender.asEntityNum());
		final var existingAllowance = existingAllowances.containsKey(key) ? existingAllowances.get(key) : 0;
		final long aggregatedAmount = amount + existingAllowance;

		if (aggregatedAmount < 0) {
			return NEGATIVE_ALLOWANCE_AMOUNT;
		}

		if (token.getSupplyType().equals(TokenSupplyType.FINITE) && aggregatedAmount > token.getMaxSupply()) {
			return AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY;
		}
		return OK;
	}

	@Override
	public ResponseCodeEnum validateAmount(final long amount, final Account ownerAccount, final Id spender) {
		final var existingAllowances = ownerAccount.getCryptoAllowances();
		final var key = spender.asEntityNum();

		final var existingAmount = existingAllowances.containsKey(key) ? existingAllowances.get(key) : 0;

		if (amount + existingAmount < 0) {
			return NEGATIVE_ALLOWANCE_AMOUNT;
		}
		return OK;
	}
}
