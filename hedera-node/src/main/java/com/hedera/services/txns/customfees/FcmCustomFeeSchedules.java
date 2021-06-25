package com.hedera.services.txns.customfees;

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

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Active CustomFeeSchedules for an entity in the tokens FCMap
 */
public class FcmCustomFeeSchedules implements CustomFeeSchedules {
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;

	public FcmCustomFeeSchedules(Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens) {
		this.tokens = tokens;
	}

	@Override
	public List<CustomFee> lookupScheduleFor(EntityId tokenId) {
		final var currentTokens = tokens.get();
		if (!currentTokens.containsKey(tokenId.asMerkle())) {
			return Collections.emptyList();
		}
		final var merkleToken = currentTokens.get(tokenId.asMerkle());
		return merkleToken.customFeeSchedule();
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
}
