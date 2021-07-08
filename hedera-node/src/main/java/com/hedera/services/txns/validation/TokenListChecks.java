package com.hedera.services.txns.validation;

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

import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenType;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

import static com.hedera.services.txns.validation.PureValidation.checkKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class TokenListChecks {
	static Predicate<Key> ADMIN_KEY_REMOVAL = ImmutableKeyUtils::signalsKeyRemoval;

	public static boolean repeatsItself(List<TokenID> tokens) {
		return new HashSet<>(tokens).size() < tokens.size();
	}


	public static ResponseCodeEnum typeCheck(TokenType type, long initialSupply, int decimals) {
		switch (type) {
			case FUNGIBLE_COMMON:
				return fungibleCommonTypeCheck(initialSupply, decimals);
			case NON_FUNGIBLE_UNIQUE:
				return nonFungibleUniqueCheck(initialSupply, decimals);
			default:
				return NOT_SUPPORTED;
		}
	}

	public static ResponseCodeEnum nonFungibleUniqueCheck(long initialSupply, int decimals) {
		if (initialSupply != 0) {
			return INVALID_TOKEN_INITIAL_SUPPLY;
		}

		return decimals != 0 ? INVALID_TOKEN_DECIMALS : OK;
	}

	public static ResponseCodeEnum fungibleCommonTypeCheck(long initialSupply, int decimals) {
		if (initialSupply < 0) {
			return INVALID_TOKEN_INITIAL_SUPPLY;
		}

		return decimals < 0 ? INVALID_TOKEN_DECIMALS : OK;
	}

	public static ResponseCodeEnum suppliesCheck(long initialSupply, long maxSupply) {
		if (maxSupply > 0 && initialSupply > maxSupply) {
			return INVALID_TOKEN_INITIAL_SUPPLY;
		}

		return OK;
	}

	public static ResponseCodeEnum supplyTypeCheck(TokenSupplyType supplyType, long maxSupply) {
		switch (supplyType) {
			case INFINITE:
				return maxSupply != 0 ? INVALID_TOKEN_MAX_SUPPLY : OK;
			case FINITE:
				return maxSupply <= 0 ? INVALID_TOKEN_MAX_SUPPLY : OK;
			default:
				return NOT_SUPPORTED;
		}
	}

	public static ResponseCodeEnum checkKeys(
			boolean hasAdminKey, Key adminKey,
			boolean hasKycKey, Key kycKey,
			boolean hasWipeKey, Key wipeKey,
			boolean hasSupplyKey, Key supplyKey,
			boolean hasFreezeKey, Key freezeKey,
			boolean hasFeeScheduleKey, Key feeScheduleKey
	) {
		ResponseCodeEnum validity = OK;

		if (hasAdminKey && !ADMIN_KEY_REMOVAL.test(adminKey)) {
			if ((validity = checkKey(adminKey, INVALID_ADMIN_KEY)) != OK) {
				return validity;
			}
		}
		if (hasKycKey) {
			if ((validity = checkKey(kycKey, INVALID_KYC_KEY)) != OK) {
				return validity;
			}
		}
		if (hasWipeKey) {
			if ((validity = checkKey(wipeKey, INVALID_WIPE_KEY)) != OK) {
				return validity;
			}
		}
		if (hasSupplyKey) {
			if ((validity = checkKey(supplyKey, INVALID_SUPPLY_KEY)) != OK) {
				return validity;
			}
		}
		if (hasFreezeKey) {
			if ((validity = checkKey(freezeKey, INVALID_FREEZE_KEY)) != OK) {
				return validity;
			}
		}
		if (hasFeeScheduleKey) {
			if ((validity = checkKey(feeScheduleKey, INVALID_CUSTOM_FEE_SCHEDULE_KEY)) != OK) {
				return validity;
			}
		}

		return validity;
	}
}
