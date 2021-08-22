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

import com.hedera.services.grpc.marshalling.CustomFeeMeta;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.models.Id;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

/**
 * Active CustomFeeSchedules for an entity in the tokens FCMap
 */
@Singleton
public class FcmCustomFeeSchedules implements CustomFeeSchedules {
	private final Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens;

	@Inject
	public FcmCustomFeeSchedules(Supplier<FCMap<MerkleEntityId, MerkleToken>> tokens) {
		this.tokens = tokens;
	}

	@Override
	public CustomFeeMeta lookupMetaFor(Id tokenId) {
		final var currentTokens = tokens.get();
		if (!currentTokens.containsKey(tokenId.asMerkle())) {
			return CustomFeeMeta.MISSING_META;
		}
		final var merkleToken = currentTokens.get(tokenId.asMerkle());
		return new CustomFeeMeta(tokenId, merkleToken.treasury().asId(), merkleToken.customFeeSchedule());
	}

	public Supplier<FCMap<MerkleEntityId, MerkleToken>> getTokens() {
		return tokens;
	}

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}
}
