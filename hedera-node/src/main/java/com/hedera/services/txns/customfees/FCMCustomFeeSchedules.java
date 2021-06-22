package com.hedera.services.txns.customfees;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fcmap.FCMap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Active CustomFeeSchedules for an entity in the tokens FCMap
 */
public class FCMCustomFeeSchedules implements CustomFeeSchedules {
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;

	public FCMCustomFeeSchedules(Supplier<FCMap<MerkleEntityId, MerkleToken>> tokenFCMap) {
		tokens = tokenFCMap;
	}

	@Override
	public List<CustomFee> lookupScheduleFor(EntityId tokenId) {
		if (!tokens.get().containsKey(tokenId.asMerkle())) {
			return new ArrayList<>();
		}
		final var merkleToken = tokens.get().get(tokenId.asMerkle());
		return merkleToken.getFeeSchedule();
	}
}
