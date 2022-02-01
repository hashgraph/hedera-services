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

package com.hedera.services.txns.crypto.helpers;

import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hederahashgraph.api.proto.java.NftAllowance;

import java.util.List;
import java.util.Map;

public class AllowanceHelpers {
	/**
	 * Since each serial number in an NFTAllowance is considered as an allowance, to get total allowance
	 * from an NFTAllowance the size of serial numbers should be added.
	 *
	 * @param nftAllowances
	 * @return
	 */
	public static int aggregateNftAllowances(Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances) {
		int nftAllowancesTotal = 0;
		for (var allowances : nftAllowances.entrySet()) {
			var serials = allowances.getValue().getSerialNumbers();
			if (!serials.isEmpty()) {
				nftAllowancesTotal += serials.size();
			} else {
				nftAllowancesTotal++;
			}
		}
		return nftAllowancesTotal;
	}

	/**
	 * Since each serial number in an NFTAllowance is considered as an allowance, to get total allowance
	 * from an NFTAllowance the size of serial numbers should be added.
	 *
	 * @param nftAllowances
	 * @return
	 */
	public static int aggregateNftAllowances(List<NftAllowance> nftAllowances) {
		int nftAllowancesTotal = 0;
		for (var allowances : nftAllowances) {
			var serials = allowances.getSerialNumbersList();
			if (!serials.isEmpty()) {
				nftAllowancesTotal += serials.size();
			} else {
				nftAllowancesTotal++;
			}
		}
		return nftAllowancesTotal;
	}
}
