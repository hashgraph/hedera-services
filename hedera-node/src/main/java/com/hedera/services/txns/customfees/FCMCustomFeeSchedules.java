package com.hedera.services.txns.customfees;

import com.google.common.base.MoreObjects;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

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

	public Supplier<FCMap<MerkleEntityId, MerkleToken>> getTokens() {
		return tokens;
	}

	/* NOTE: The object methods below are only overridden to improve
				readability of unit tests; this model object is not used in hash-based
				collections, so the performance of these methods doesn't matter. */
	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(FCMCustomFeeSchedules.class)
				.add("tokens", tokens.get())
				.toString();
	}
}
