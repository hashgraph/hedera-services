package com.hedera.services.state.expiry.renewal;

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

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;

@Singleton
public class TreasuryReturnHelper {
	private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;
	private final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels;

	@Inject
	public TreasuryReturnHelper(
			final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens,
			final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels
	) {
		this.tokens = tokens;
		this.tokenRels = tokenRels;
	}

	void updateReturns(
			final AccountID expired,
			final TokenID associatedToken,
			final List<EntityId> tokenTypes,
			final List<CurrencyAdjustments> returnTransfers
	) {
		final var curTokenRels = tokenRels.get();
		final var expiredRel = fromAccountTokenRel(expired, associatedToken);
		final var relStatus = curTokenRels.get(expiredRel);
		final long balance = relStatus.getBalance();

		curTokenRels.remove(expiredRel);

		final var tokenNum = EntityNum.fromTokenId(associatedToken);
		final var curTokens = tokens.get();
		if (!curTokens.containsKey(tokenNum)) {
			return;
		}
		final var token = curTokens.get(tokenNum);
		if (token.isDeleted()) {
			return;
		}
		if (balance == 0) {
			return;
		}
		final var treasury = token.treasury().toGrpcAccountId();
		final boolean expiredFirst = ACCOUNT_ID_COMPARATOR.compare(expired, treasury) < 0;
		tokenTypes.add(EntityId.fromGrpcTokenId(associatedToken));
		final var expiredId = EntityId.fromGrpcAccountId(expired);
		final var treasuryId = EntityId.fromGrpcAccountId(treasury);
		returnTransfers.add(new CurrencyAdjustments(
				expiredFirst
						? new long[] { -balance, +balance }
						: new long[] { +balance, -balance },
				expiredFirst
						? new long[] { expiredId.num(), treasuryId.num() }
						: new long[] { treasuryId.num(), expiredId.num() }
		));
		final var treasuryRel = fromAccountTokenRel(treasury, associatedToken);
		final var mutableTreasuryRelStatus = curTokenRels.getForModify(treasuryRel);
		final long newTreasuryBalance = mutableTreasuryRelStatus.getBalance() + balance;
		mutableTreasuryRelStatus.setBalance(newTreasuryBalance);
	}
}
