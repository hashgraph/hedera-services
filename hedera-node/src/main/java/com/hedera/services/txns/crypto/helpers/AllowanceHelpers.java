package com.hedera.services.txns.crypto.helpers;

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

package com.hedera.services.txns.crypto.helpers;

import com.google.protobuf.BoolValue;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AllowanceHelpers {
	private AllowanceHelpers() {
		throw new IllegalStateException("Utility class");
	}

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

	public static boolean hasRepeatedSpender(List<AccountID> spenders) {
		final int n = spenders.size();
		if (n < 2) {
			return false;
		}
		for (var i = 0; i < n - 1; i++) {
			for (var j = i + 1; j < n; j++) {
				if (spenders.get(i).equals(spenders.get(j))) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean hasRepeatedSerials(List<Long> serials) {
		final int n = serials.size();
		if (n < 2) {
			return false;
		}
		for (var i = 0; i < n - 1; i++) {
			for (var j = i + 1; j < n; j++) {
				if (absolute(serials.get(i)).equals(absolute(serials.get(j)))) {
					return true;
				}
			}
		}
		return false;
	}

	public static boolean hasRepeatedId(List<FcTokenAllowanceId> serials) {
		final int n = serials.size();
		if (n < 2) {
			return false;
		}
		for (var i = 0; i < n - 1; i++) {
			for (var j = i + 1; j < n; j++) {
				if (serials.get(i).equals(serials.get(j))) {
					return true;
				}
			}
		}
		return false;
	}

	public static int countSerials(final List<NftAllowance> nftAllowancesList) {
		int totalSerials = 0;
		for (var allowance : nftAllowancesList) {
			totalSerials += allowance.getSerialNumbersCount();
		}
		return totalSerials;
	}

	public static Long absolute(Long val) {
		return val < 0 ? val * -1 : val;
	}

	public static List<NftAllowance> getNftAllowancesList(final MerkleAccount account) {
		if (!account.state().getNftAllowances().isEmpty()) {
			List<NftAllowance> nftAllowances = new ArrayList<>();
			final var nftAllowance = NftAllowance.newBuilder();
			for (var a : account.state().getNftAllowances().entrySet()) {
				nftAllowance.setTokenId(a.getKey().getTokenNum().toGrpcTokenId());
				nftAllowance.setSpender(a.getKey().getSpenderNum().toGrpcAccountId());
				nftAllowance.setApprovedForAll(BoolValue.of(a.getValue().isApprovedForAll()));
				nftAllowance.addAllSerialNumbers(a.getValue().getSerialNumbers());
				nftAllowances.add(nftAllowance.build());
			}
			return nftAllowances;
		}
		return Collections.emptyList();
	}

	public static List<TokenAllowance> getFungibleTokenAllowancesList(final MerkleAccount account) {
		if (!account.state().getFungibleTokenAllowances().isEmpty()) {
			List<TokenAllowance> tokenAllowances = new ArrayList<>();
			final var tokenAllowance = TokenAllowance.newBuilder();
			for (var a : account.state().getFungibleTokenAllowances().entrySet()) {
				tokenAllowance.setTokenId(a.getKey().getTokenNum().toGrpcTokenId());
				tokenAllowance.setSpender(a.getKey().getSpenderNum().toGrpcAccountId());
				tokenAllowance.setAmount(a.getValue());
				tokenAllowances.add(tokenAllowance.build());
			}
			return tokenAllowances;
		}
		return Collections.emptyList();
	}

	public static List<CryptoAllowance> getCryptoAllowancesList(final MerkleAccount account) {
		if (!account.state().getCryptoAllowances().isEmpty()) {
			List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
			final var cryptoAllowance = CryptoAllowance.newBuilder();
			for (var a : account.state().getCryptoAllowances().entrySet()) {
				cryptoAllowance.setSpender(a.getKey().toGrpcAccountId());
				cryptoAllowance.setAmount(a.getValue());
				cryptoAllowances.add(cryptoAllowance.build());
			}
			return cryptoAllowances;
		}
		return Collections.emptyList();
	}
}
