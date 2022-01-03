package com.hedera.services.context;

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
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleSpecialFiles;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.AddressBook;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;

import java.time.Instant;

/**
 * A {@link StateChildren} implementation used to capture a signed state, whose children are necessarily immutable.
 *
 * This class is thread-safe as long as it is published safely (e.g., via a {@code volatile} field, a field with
 * normal locking, or via a concurrent collection).
 */
public record ImmutableStateChildren(
		MerkleMap<EntityNum, MerkleAccount> accounts,
		MerkleMap<EntityNum, MerkleTopic> topics,
		MerkleMap<EntityNum, MerkleToken> tokens,
		MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens,
		MerkleMap<EntityNum, MerkleSchedule> schedules,
		MerkleMap<String, MerkleOptionalBlob> storage,
		MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations,
		FCOneToManyRelation<EntityNum, Long> uniqueTokenAssociations,
		FCOneToManyRelation<EntityNum, Long> uniqueOwnershipAssociations,
		FCOneToManyRelation<EntityNum, Long> uniqueOwnershipTreasuryAssociations,
		MerkleNetworkContext networkCtx, AddressBook addressBook,
		MerkleSpecialFiles specialFiles,
		RecordsRunningHashLeaf runningHashLeaf,
		Instant signedAt) implements StateChildren {
}
