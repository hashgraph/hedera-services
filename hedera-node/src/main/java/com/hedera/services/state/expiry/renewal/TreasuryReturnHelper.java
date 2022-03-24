package com.hedera.services.state.expiry.renewal;

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;

import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;

public class TreasuryReturnHelper {
	private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;
	private final Supplier<MerkleMap<EntityNumPair, MerkleTokenRelStatus>> tokenRels;

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

		final var tKey = EntityNum.fromTokenId(associatedToken);

		final var curTokens = tokens.get();
		if (!curTokens.containsKey(tKey)) {
			return;
		}

		final var token = curTokens.get(tKey);
		if (token.isDeleted()) {
			return;
		}

		if (balance == 0L) {
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
