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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.GrantedCryptoAllowance;
import com.hederahashgraph.api.proto.java.GrantedNftAllowance;
import com.hederahashgraph.api.proto.java.GrantedTokenAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;

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

	public static boolean hasRepeatedSpender(List<EntityNumPair> spenders) {
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

	public static boolean hasRepeatedId(List<Pair<EntityNum, FcTokenAllowanceId>> allowanceKeys) {
		final int n = allowanceKeys.size();
		if (n < 2) {
			return false;
		}
		for (var i = 0; i < n - 1; i++) {
			for (var j = i + 1; j < n; j++) {
				final var a = allowanceKeys.get(i);
				final var b = allowanceKeys.get(j);
				if (a.getLeft().equals(b.getLeft()) && a.getRight().equals(b.getRight())) {
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

	public static List<GrantedNftAllowance> getNftAllowancesList(final MerkleAccount account) {
		if (!account.state().getNftAllowances().isEmpty()) {
			List<GrantedNftAllowance> nftAllowances = new ArrayList<>();
			for (var a : account.state().getNftAllowances().entrySet()) {
				final var nftAllowance = GrantedNftAllowance.newBuilder();
				nftAllowance.setTokenId(a.getKey().getTokenNum().toGrpcTokenId());
				nftAllowance.setSpender(a.getKey().getSpenderNum().toGrpcAccountId());
				nftAllowance.setApprovedForAll(a.getValue().isApprovedForAll());
				nftAllowance.addAllSerialNumbers(a.getValue().getSerialNumbers());
				nftAllowances.add(nftAllowance.build());
			}
			return nftAllowances;
		}
		return Collections.emptyList();
	}

	public static List<GrantedTokenAllowance> getFungibleTokenAllowancesList(final MerkleAccount account) {
		if (!account.state().getFungibleTokenAllowances().isEmpty()) {
			List<GrantedTokenAllowance> tokenAllowances = new ArrayList<>();
			final var tokenAllowance = GrantedTokenAllowance.newBuilder();
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

	public static List<GrantedCryptoAllowance> getCryptoAllowancesList(final MerkleAccount account) {
		if (!account.state().getCryptoAllowances().isEmpty()) {
			List<GrantedCryptoAllowance> cryptoAllowances = new ArrayList<>();
			final var cryptoAllowance = GrantedCryptoAllowance.newBuilder();
			for (var a : account.state().getCryptoAllowances().entrySet()) {
				cryptoAllowance.setSpender(a.getKey().toGrpcAccountId());
				cryptoAllowance.setAmount(a.getValue());
				cryptoAllowances.add(cryptoAllowance.build());
			}
			return cryptoAllowances;
		}
		return Collections.emptyList();
	}

	public static Account fetchOwnerAccount(final AccountID owner,
			final Account payerAccount,
			final AccountStore accountStore,
			final Map<Long, Account> entitiesChanged) {
		final var ownerId = Id.fromGrpcAccount(owner);
		if (owner.equals(AccountID.getDefaultInstance()) || owner.equals(payerAccount.getId().asGrpcAccount())) {
			return payerAccount;
		} else if (entitiesChanged.containsKey(ownerId.num())) {
			return entitiesChanged.get(ownerId.num());
		} else {
			return accountStore.loadAccountOrFailWith(ownerId, INVALID_ALLOWANCE_OWNER_ID);
		}
	}

	public static EntityNumPair buildEntityNumPairFrom(AccountID owner, AccountID spender, final EntityNum payer) {
		return EntityNumPair.fromLongs(owner == null ? payer.longValue() : owner.getAccountNum(),
				spender.getAccountNum());
	}

	public static Pair<EntityNum, FcTokenAllowanceId> buildTokenAllowanceKey
			(AccountID owner, TokenID token, AccountID spender) {
		return Pair.of(EntityNum.fromAccountId(owner), FcTokenAllowanceId.from(EntityNum.fromTokenId(token),
				EntityNum.fromAccountId(spender)));
	}
}
