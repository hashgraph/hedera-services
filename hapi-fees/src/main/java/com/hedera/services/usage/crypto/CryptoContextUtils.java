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

package com.hedera.services.usage.crypto;

import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CryptoContextUtils {
	public static Map<Long, Long> convertToCryptoMap(final List<CryptoAllowance> allowances) {
		Map<Long, Long> allowanceMap = new HashMap<>();
		for (var a : allowances) {
			allowanceMap.put(a.getSpender().getAccountNum(), a.getAmount());
		}
		return allowanceMap;
	}

	public static Map<ExtantCryptoContext.AllowanceMapKey, Long> convertToTokenMap(
			final List<TokenAllowance> allowances) {
		Map<ExtantCryptoContext.AllowanceMapKey, Long> allowanceMap = new HashMap<>();
		for (var a : allowances) {
			allowanceMap.put(new ExtantCryptoContext.AllowanceMapKey(a.getTokenId().getTokenNum(),
					a.getSpender().getAccountNum()), a.getAmount());
		}
		return allowanceMap;
	}

	public static Map<ExtantCryptoContext.AllowanceMapKey, ExtantCryptoContext.AllowanceMapValue> convertToNftMap(
			final List<NftAllowance> allowances) {
		Map<ExtantCryptoContext.AllowanceMapKey, ExtantCryptoContext.AllowanceMapValue> allowanceMap =
				new HashMap<>();
		for (var a : allowances) {
			allowanceMap.put(new ExtantCryptoContext.AllowanceMapKey(a.getTokenId().getTokenNum(),
							a.getSpender().getAccountNum()),
					new ExtantCryptoContext.AllowanceMapValue(a.getApprovedForAll().getValue(),
							a.getSerialNumbersList()));
		}
		return allowanceMap;
	}

	public static int countSerialNums(final Collection<ExtantCryptoContext.AllowanceMapValue> nftAllowancesListValues) {
		int totalSerials = 0;
		for (var allowance : nftAllowancesListValues) {
			totalSerials += allowance.serialNums().size();
		}
		return totalSerials;
	}

	public static int countSerials(final List<NftAllowance> nftAllowancesList) {
		int totalSerials = 0;
		for (var allowance : nftAllowancesList) {
			totalSerials += allowance.getSerialNumbersCount();
		}
		return totalSerials;
	}
}
